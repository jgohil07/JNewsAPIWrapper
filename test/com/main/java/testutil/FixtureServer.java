package com.main.java.testutil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

/**
 * Loopback HTTP server serving canned responses per path, recording every request. Responses queued for a path are
 * served in order; the last one repeats.
 */
public final class FixtureServer implements AutoCloseable {

	public record Reply(int status, String contentType, byte[] body, Map<String, String> headers) {
		public static Reply of(int status, String contentType, String body) {
			return new Reply(status, contentType, body.getBytes(StandardCharsets.UTF_8), Map.of());
		}

		public static Reply json(String body) {
			return of(200, "application/json; charset=utf-8", body);
		}

		public static Reply xml(String body) {
			return of(200, "application/rss+xml; charset=utf-8", body);
		}
	}

	public record Request(String method, String path, String query, Headers headers, String body) {}

	private final HttpServer server;
	private final Map<String, Deque<Reply>> replies = new ConcurrentHashMap<>();
	private final List<Request> requests = new CopyOnWriteArrayList<>();

	public FixtureServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange -> {
			try (exchange) {
				String path = exchange.getRequestURI().getPath();
				String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				requests.add(new Request(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
						exchange.getRequestHeaders(), body));
				Deque<Reply> queue = replies.get(path);
				Reply reply;
				if (queue == null || queue.isEmpty()) {
					reply = Reply.of(404, "text/plain", "no fixture for " + path);
				} else {
					synchronized (queue) {
						reply = queue.size() > 1 ? queue.pollFirst() : queue.peekFirst();
					}
				}
				if (reply.contentType() != null) {
					exchange.getResponseHeaders().set("Content-Type", reply.contentType());
				}
				reply.headers().forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
				exchange.sendResponseHeaders(reply.status(), reply.body().length == 0 ? -1 : reply.body().length);
				if (reply.body().length > 0) {
					try (OutputStream out = exchange.getResponseBody()) {
						out.write(reply.body());
					}
				}
			}
		});
		server.start();
	}

	/** Queues replies for {@code path}; the last one is repeated for further requests. */
	public FixtureServer on(String path, Reply... queued) {
		Deque<Reply> q = new ArrayDeque<>(List.of(queued));
		replies.put(path, q);
		return this;
	}

	public String url(String path) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + path;
	}

	public List<Request> requests() {
		return requests;
	}

	public Request lastRequest() {
		if (requests.isEmpty()) {
			throw new AssertionError("no request was made");
		}
		return requests.get(requests.size() - 1);
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
