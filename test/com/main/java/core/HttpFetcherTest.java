package com.main.java.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.main.java.testutil.FixtureServer;
import com.main.java.testutil.FixtureServer.Reply;

class HttpFetcherTest {

	private FixtureServer server;
	private final List<Duration> sleeps = new ArrayList<>();
	private HttpFetcher fetcher;

	@BeforeEach
	void setUp() throws IOException {
		server = new FixtureServer();
		fetcher = HttpFetcher.builder().sleeper(sleeps::add).maxBodyBytes(1024).build();
	}

	@AfterEach
	void tearDown() {
		server.close();
	}

	@Test
	void retriesServerErrorsThenSucceeds() {
		server.on("/r", Reply.of(503, "text/plain", "busy"), Reply.of(200, "text/plain", "ok"));
		assertEquals("ok", fetcher.get(URI.create(server.url("/r")), Map.of()).text());
		assertEquals(2, server.requests().size());
		assertEquals(List.of(Duration.ofSeconds(1)), sleeps);
	}

	@Test
	void honoursRetryAfterAndGivesUp() {
		server.on("/r", new Reply(429, "text/plain", "slow".getBytes(), Map.of("Retry-After", "7")));
		FetchException e = assertThrows(FetchException.class, () -> fetcher.get(URI.create(server.url("/r?key=secret")), Map.of()));
		assertEquals(429, e.getStatus());
		assertEquals(3, server.requests().size());
		assertEquals(List.of(Duration.ofSeconds(7), Duration.ofSeconds(7)), sleeps);
		assertFalse(e.getMessage().contains("secret"), "query strings are redacted");
	}

	@Test
	void doesNotRetryClientErrors() {
		server.on("/n", Reply.of(404, "text/plain", "nope"));
		assertThrows(FetchException.class, () -> fetcher.get(URI.create(server.url("/n")), Map.of()));
		assertEquals(1, server.requests().size());
	}

	@Test
	void rejectsOversizedBodies() {
		server.on("/big", Reply.of(200, "text/plain", "x".repeat(2048)));
		FetchException e = assertThrows(FetchException.class, () -> fetcher.get(URI.create(server.url("/big")), Map.of()));
		assertTrue(e.getMessage().contains("exceeds"));
	}

	@Test
	void rejectsNonHttpSchemes() {
		for (String u : new String[] { "file:///etc/passwd", "javascript:alert(1)", "ftp://example.com/x" }) {
			assertThrows(FetchException.class, () -> fetcher.get(URI.create(u), Map.of()), u);
		}
	}

	@Test
	void decodesDeclaredCharset() {
		server.on("/c", new Reply(200, "text/plain; charset=ISO-8859-1", "café".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), Map.of()));
		assertEquals("café", fetcher.get(URI.create(server.url("/c")), Map.of()).text());
	}

	@Test
	void slowDripBodyTimesOut() throws Exception {
		com.sun.net.httpserver.HttpServer slow = com.sun.net.httpserver.HttpServer.create(
				new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
		slow.createContext("/", ex -> {
			ex.sendResponseHeaders(200, 0);
			try (var out = ex.getResponseBody()) {
				out.write("<rss>".getBytes());
				out.flush();
				Thread.sleep(10_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		slow.start();
		try {
			HttpFetcher f = HttpFetcher.builder().requestTimeout(Duration.ofSeconds(1)).maxRetries(0).build();
			long t0 = System.nanoTime();
			FetchException e = assertThrows(FetchException.class,
					() -> f.get(URI.create("http://127.0.0.1:" + slow.getAddress().getPort() + "/"), Map.of()));
			assertTrue(e.getMessage().contains("not received within"), e.getMessage());
			assertTrue(Duration.ofNanos(System.nanoTime() - t0).toSeconds() < 5);
			assertFalse(Thread.currentThread().isInterrupted(), "watchdog interrupt must be cleared");
		} finally {
			slow.stop(0);
		}
	}

	@Test
	void postIsNeverRetried() {
		server.on("/p", Reply.of(503, "text/plain", "busy"));
		HttpFetcher.Response r = fetcher.fetch("POST", URI.create(server.url("/p")), Map.of(), "{}");
		assertEquals(503, r.status());
		assertEquals(1, server.requests().size());
		assertTrue(sleeps.isEmpty());
	}

	@Test
	void transportErrorsNeverCarryTheQueryString() {
		URI uri = URI.create("https://h.example/p?apikey=SECRET123&x=1");
		String scrubbed = HttpFetcher.scrub("failed to connect to https://h.example/p?apikey=SECRET123&x=1", uri);
		assertFalse(scrubbed.contains("SECRET123"), scrubbed);
		HttpFetcher closedPort = HttpFetcher.builder().maxRetries(0).connectTimeout(Duration.ofSeconds(2)).build();
		FetchException e = assertThrows(FetchException.class,
				() -> closedPort.get(URI.create("http://127.0.0.1:1/p?apikey=SECRET123"), Map.of()));
		assertFalse(e.getMessage().contains("SECRET123"), e.getMessage());
	}

	@Test
	void redactDropsQueryAndUserInfo() {
		assertEquals("https://h.example/p?…", HttpFetcher.redact(URI.create("https://u:pw@h.example/p?apiKey=abc")));
	}
}
