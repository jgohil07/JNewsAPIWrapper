package com.main.java.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Small, strict HTTP GET/POST client on {@link java.net.http.HttpClient}.
 * <ul>
 * <li>only http and https URLs;</li>
 * <li>connect and request timeouts;</li>
 * <li>a hard cap on the response body size;</li>
 * <li>bounded retries with backoff on 429 and 5xx and on transport errors;</li>
 * <li>error messages carry the URL without its query string.</li>
 * </ul>
 * Instances are immutable and thread-safe.
 *
 * @author jgohil
 */
public final class HttpFetcher {

	public static final String USER_AGENT = "JNewsAPIWrapper/2.0 (+https://github.com/jgohil07/JNewsAPIWrapper)";

	/**
	 * Browser agent for publishers whose CDNs drop other agents (NSE, Business Standard; checked 2026-09-27). The
	 * project token stays at the end so the traffic remains identifiable.
	 */
	public static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/128 Safari/537.36 JNewsAPIWrapper/2.0";

	private static final HttpFetcher DEFAULT = builder().build();

	/** A response whose status the caller has not yet judged. */
	public record Response(int status, byte[] body, HttpHeaders headers, URI uri) {

		/** @return the body decoded with the charset from Content-Type, or UTF-8 */
		public String text() {
			return new String(body, charset());
		}

		public Optional<String> contentType() {
			return headers.firstValue("content-type");
		}

		public boolean isSuccess() {
			return status >= 200 && status < 300;
		}

		private Charset charset() {
			String ct = contentType().orElse("");
			for (String part : ct.split(";")) {
				String p = part.trim();
				if (p.toLowerCase(Locale.ROOT).startsWith("charset=")) {
					try {
						return Charset.forName(p.substring(8).replace("\"", "").trim());
					} catch (RuntimeException e) {
						return StandardCharsets.UTF_8;
					}
				}
			}
			return StandardCharsets.UTF_8;
		}
	}

	/** Sleeps between retries; replaced in tests. */
	@FunctionalInterface
	public interface Sleeper {
		void sleep(Duration d) throws InterruptedException;
	}

	private final HttpClient client;
	private final Duration requestTimeout;
	private final long maxBodyBytes;
	private final int maxRetries;
	private final String userAgent;
	private final Sleeper sleeper;

	private HttpFetcher(Builder b) {
		this.client = HttpClient.newBuilder()
				.connectTimeout(b.connectTimeout)
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		this.requestTimeout = b.requestTimeout;
		this.maxBodyBytes = b.maxBodyBytes;
		this.maxRetries = b.maxRetries;
		this.userAgent = b.userAgent;
		this.sleeper = b.sleeper;
	}

	public static HttpFetcher defaults() {
		return DEFAULT;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * GET that must succeed.
	 *
	 * @throws FetchException on transport failure, a non-2xx status after retries, or an oversized body
	 */
	public Response get(URI uri, Map<String, String> headers) {
		Response r = fetch("GET", uri, headers, null);
		requireSuccess(r);
		return r;
	}

	/**
	 * Sends a request and returns the final response whatever its status (after retries on 429/5xx), so callers can
	 * read an API's own error body. Transport failures and oversized bodies still throw.
	 *
	 * @param method GET, POST, PUT or DELETE
	 * @param body   request body for POST/PUT, or null
	 */
	public Response fetch(String method, URI uri, Map<String, String> headers, String body) {
		checkScheme(uri);
		HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
				.timeout(requestTimeout)
				.header("User-Agent", userAgent)
				.header("Accept-Encoding", "identity");
		if (headers != null) {
			headers.forEach((k, v) -> {
				if (k != null && v != null) {
					rb.setHeader(k, v);
				}
			});
		}
		HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
		HttpRequest request = rb.method(method, publisher).build();

		int attempt = 0;
		while (true) {
			try {
				HttpResponse<InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
				byte[] bytes = readCapped(resp.body(), uri);
				Response r = new Response(resp.statusCode(), bytes, resp.headers(), resp.uri());
				if (attempt < maxRetries && isRetryable(r.status())) {
					backoff(attempt++, r.headers().firstValue("retry-after"));
					continue;
				}
				return r;
			} catch (IOException e) {
				if (attempt < maxRetries) {
					backoff(attempt++, Optional.empty());
					continue;
				}
				throw new FetchException(method + " " + redact(uri) + " failed: " + e.getClass().getSimpleName()
						+ (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"), e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new FetchException(method + " " + redact(uri) + " interrupted", e);
			}
		}
	}

	/** @throws FetchException when {@code r} is not 2xx */
	public static void requireSuccess(Response r) {
		if (!r.isSuccess()) {
			throw new FetchException("HTTP " + r.status() + " from " + redact(r.uri()), r.status());
		}
	}

	/** @return scheme, host, port and path of {@code uri}, never its query or user info */
	public static String redact(URI uri) {
		if (uri == null) {
			return "<null>";
		}
		StringBuilder sb = new StringBuilder();
		sb.append(uri.getScheme()).append("://").append(uri.getHost());
		if (uri.getPort() != -1) {
			sb.append(':').append(uri.getPort());
		}
		sb.append(uri.getRawPath() == null ? "" : uri.getRawPath());
		if (uri.getRawQuery() != null) {
			sb.append("?…");
		}
		return sb.toString();
	}

	private static void checkScheme(URI uri) {
		String scheme = uri == null ? null : uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
			throw new FetchException("Only absolute http(s) URLs are allowed: " + (uri == null ? "<null>" : redact(uri)), -1);
		}
	}

	private static boolean isRetryable(int status) {
		return status == 429 || (status >= 500 && status <= 599);
	}

	private byte[] readCapped(InputStream in, URI uri) throws IOException {
		try (in) {
			byte[] bytes = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBodyBytes + 1));
			if (bytes.length > maxBodyBytes) {
				throw new FetchException("Response from " + redact(uri) + " exceeds " + maxBodyBytes + " bytes", -1);
			}
			return bytes;
		}
	}

	private void backoff(int attempt, Optional<String> retryAfter) {
		long seconds = 1L << attempt; // 1s, 2s, 4s ...
		if (retryAfter.isPresent()) {
			try {
				seconds = Math.max(seconds, Long.parseLong(retryAfter.get().trim()));
			} catch (NumberFormatException ignored) {
				// HTTP-date form: keep the exponential delay
			}
		}
		try {
			sleeper.sleep(Duration.ofSeconds(Math.min(seconds, 30)));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new FetchException("Interrupted while backing off", e);
		}
	}

	public static final class Builder {
		private Duration connectTimeout = Duration.ofSeconds(10);
		private Duration requestTimeout = Duration.ofSeconds(20);
		private long maxBodyBytes = 5L * 1024 * 1024;
		private int maxRetries = 2;
		private String userAgent = USER_AGENT;
		private Sleeper sleeper = d -> Thread.sleep(d.toMillis());

		public Builder connectTimeout(Duration d) {
			this.connectTimeout = d;
			return this;
		}

		public Builder requestTimeout(Duration d) {
			this.requestTimeout = d;
			return this;
		}

		public Builder maxBodyBytes(long n) {
			this.maxBodyBytes = n;
			return this;
		}

		public Builder maxRetries(int n) {
			this.maxRetries = n;
			return this;
		}

		public Builder userAgent(String ua) {
			this.userAgent = ua;
			return this;
		}

		public Builder sleeper(Sleeper s) {
			this.sleeper = s;
			return this;
		}

		public HttpFetcher build() {
			return new HttpFetcher(this);
		}
	}
}
