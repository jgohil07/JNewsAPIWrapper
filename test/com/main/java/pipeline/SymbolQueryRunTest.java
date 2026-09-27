package com.main.java.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.main.java.core.HttpFetcher;
import com.main.java.sources.NseSymbolMaster;
import com.main.java.sources.SourceRegistry;
import com.main.java.testutil.FixtureServer;
import com.main.java.testutil.FixtureServer.Reply;

/** Review finding 4: the per-symbol query's status and exit semantics. */
class SymbolQueryRunTest {

	static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");
	static final NseSymbolMaster.Company MBAPL = new NseSymbolMaster.Company("MBAPL", "Madhya Bharat Agro Products Limited", "INE900L01010");

	private FixtureServer server;
	private SymbolQuery query;

	@BeforeEach
	void setUp() throws IOException {
		server = new FixtureServer();
		SourceRegistry empty = SourceRegistry.parse("""
				{"categories":[{"id":"x","label":"X","group":"world","minHealthy":1,"required":true}],"sources":[]}"""
				.getBytes(StandardCharsets.UTF_8));
		HttpFetcher http = HttpFetcher.builder().maxRetries(0).build();
		Aggregator agg = new Aggregator(empty, cfg -> { throw new AssertionError("no registry sources expected"); },
				Classifier.of(List.of()), Clock.fixed(NOW, ZoneOffset.UTC));
		query = new SymbolQuery(empty, agg, http, Clock.fixed(NOW, ZoneOffset.UTC))
				.endpoints(server.url("/nse?symbol="), server.url("/search"));
	}

	@AfterEach
	void tearDown() {
		server.close();
	}

	static String announcement(String url, String when) {
		return """
				{"symbol":"MBAPL","sm_name":"Madhya Bharat Agro Products Limited","desc":"Updates","an_dt":"%s",
				 "attchmntFile":"%s","attchmntText":"has informed the Exchange"}""".formatted(when, url);
	}

	@Test
	void nseFailureFailsTheRunEvenWhenNewsIsFine() {
		server.on("/nse", Reply.of(403, "text/html", "<html>blocked</html>"));
		server.on("/search", Reply.xml("""
				<rss><channel><item><title>Madhya Bharat Agro Products to raise capital - Paper</title>
				<link>https://paper.example/a</link><pubDate>Sun, 27 Sep 2026 06:00:00 GMT</pubDate>
				<source url="https://paper.example">Paper</source></item></channel></rss>"""));
		Aggregator.Result r = query.run(MBAPL, Duration.ofDays(2), Duration.ofSeconds(10));
		assertEquals(Aggregator.Status.FAILED, r.status());
	}

	@Test
	void emptySearchIsHealthyAndSameTitledFilingsAllSurvive() {
		server.on("/nse", Reply.json("[" + announcement("https://nse.example/1.pdf", "26-Sep-2026 10:00:00") + ","
				+ announcement("https://nse.example/2.pdf", "26-Sep-2026 18:00:00") + "]"));
		server.on("/search", Reply.xml("<rss><channel></channel></rss>"));
		Aggregator.Result r = query.run(MBAPL, Duration.ofDays(2), Duration.ofSeconds(10));
		assertEquals(Aggregator.Status.OK, r.status(), r.sources().toString());
		assertEquals(2, r.items().size(), "two 'Updates' filings are two documents");
		assertTrue(r.items().stream().allMatch(i -> i.symbols().get(0).match().equals("exact")));
	}
}
