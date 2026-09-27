package com.main.java.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

	/** Interrupts body reads that outlive the deadline (the client's own timeout covers headers only). */
	private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "jnews-http-watchdog");
		t.setDaemon(true);
		return t;
	});

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
	private final Builder builder;

	/** Most redirect hops followed for GET/HEAD. */
	static final int MAX_REDIRECTS = 5;

	private HttpFetcher(Builder b) {
		this.client = HttpClient.newBuilder()
				.connectTimeout(b.connectTimeout)
				.followRedirects(HttpClient.Redirect.NEVER) // followed manually, with target checks
				.build();
		this.requestTimeout = b.requestTimeout;
		this.maxBodyBytes = b.maxBodyBytes;
		this.maxRetries = b.maxRetries;
		this.userAgent = b.userAgent;
		this.sleeper = b.sleeper;
		this.builder = b;
	}

	/** A fetcher with the same settings but a different body-size cap (for sources known to be large). */
	public HttpFetcher withMaxBodyBytes(long n) {
		Builder b = new Builder();
		b.connectTimeout = builder.connectTimeout;
		b.requestTimeout = builder.requestTimeout;
		b.maxBodyBytes = n;
		b.maxRetries = builder.maxRetries;
		b.userAgent = builder.userAgent;
		b.sleeper = builder.sleeper;
		return new HttpFetcher(b);
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
		boolean followable = method.equals("GET") || method.equals("HEAD");
		boolean originPrivate = isNonPublic(uri);
		URI current = uri;
		for (int hop = 0;; hop++) {
			Response r = fetchOnce(method, current, headers, body);
			if (!followable || !isRedirect(r.status())) {
				return r;
			}
			if (hop >= MAX_REDIRECTS) {
				throw new FetchException("Too many redirects from " + redact(uri), r.status());
			}
			String location = r.headers().firstValue("location").orElseThrow(
					() -> new FetchException("Redirect without Location from " + redact(r.uri()), r.status()));
			URI next;
			try {
				next = current.resolve(location.strip());
			} catch (IllegalArgumentException e) {
				throw new FetchException("Malformed redirect from " + redact(current), r.status());
			}
			checkScheme(next);
			if ("https".equalsIgnoreCase(current.getScheme()) && "http".equalsIgnoreCase(next.getScheme())) {
				throw new FetchException("Refusing https-to-http redirect from " + redact(current), r.status());
			}
			if (!originPrivate && isNonPublic(next)) {
				throw new FetchException("Refusing redirect from " + redact(current) + " to a non-public address", r.status());
			}
			current = next;
		}
	}

	private Response fetchOnce(String method, URI uri, Map<String, String> headers, String body) {
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

		// Only idempotent requests are retried; a repeated POST/PUT/DELETE could duplicate side effects.
		int retries = method.equals("GET") || method.equals("HEAD") ? maxRetries : 0;
		int attempt = 0;
		while (true) {
			try {
				HttpResponse<InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
				byte[] bytes = readCapped(resp.body(), uri);
				Response r = new Response(resp.statusCode(), bytes, resp.headers(), resp.uri());
				if (attempt < retries && isRetryable(r.status())) {
					backoff(attempt++, r.headers().firstValue("retry-after"));
					continue;
				}
				return r;
			} catch (IOException e) {
				if (attempt < retries) {
					backoff(attempt++, Optional.empty());
					continue;
				}
				throw new FetchException(method + " " + redact(uri) + " failed: " + e.getClass().getSimpleName()
						+ (e.getMessage() == null ? "" : " (" + scrub(e.getMessage(), uri) + ")"), e);
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

	/** Removes the request's query string (which may carry an API key) from a transport error message. */
	static String scrub(String message, URI uri) {
		String q = uri.getRawQuery();
		String s = message;
		if (q != null && !q.isEmpty()) {
			s = s.replace(q, "…");
			if (uri.getQuery() != null) {
				s = s.replace(uri.getQuery(), "…");
			}
		}
		return s;
	}

	private static void checkScheme(URI uri) {
		String scheme = uri == null ? null : uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
			throw new FetchException("Only absolute http(s) URLs are allowed: " + (uri == null ? "<null>" : redact(uri)), -1);
		}
	}

	private static boolean isRedirect(int status) {
		return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
	}

	/** True when the host is, or resolves to, a loopback, private, link-local, unique-local or wildcard address. */
	static boolean isNonPublic(URI uri) {
		String host = uri.getHost();
		if (host == null) {
			return true;
		}
		String h = host.toLowerCase(Locale.ROOT);
		if (h.equals("localhost") || h.endsWith(".localhost")) {
			return true;
		}
		try {
			for (InetAddress a : InetAddress.getAllByName(h.replace("[", "").replace("]", ""))) {
				byte[] b = a.getAddress();
				boolean uniqueLocal6 = b.length == 16 && (b[0] & 0xfe) == 0xfc;
				boolean cgnat = b.length == 4 && (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64;
				if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()
						|| a.isMulticastAddress() || uniqueLocal6 || cgnat) {
					return true;
				}
			}
			return false;
		} catch (UnknownHostException e) {
			return false; // the request itself then fails with a clear error
		}
	}

	private static boolean isRetryable(int status) {
		return status == 429 || (status >= 500 && status <= 599);
	}

	private byte[] readCapped(InputStream in, URI uri) throws IOException {
		Thread reader = Thread.currentThread();
		// state guarded by 'lock': 0 reading, 1 finished, 2 timed out. The watchdog interrupts only while still
		// reading, and does so inside the lock, so the reader never keeps a stray interrupt after it finished.
		Object lock = new Object();
		int[] state = { 0 };
		AtomicBoolean timedOut = new AtomicBoolean();
		ScheduledFuture<?> dog = WATCHDOG.schedule(() -> {
			synchronized (lock) {
				if (state[0] == 0) {
					state[0] = 2;
					timedOut.set(true);
					reader.interrupt();
				}
			}
		}, requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
		try (in) {
			byte[] bytes = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBodyBytes + 1));
			if (bytes.length > maxBodyBytes) {
				throw new FetchException("Response from " + redact(uri) + " exceeds " + maxBodyBytes + " bytes", -1);
			}
			return bytes;
		} catch (IOException e) {
			if (timedOut.get()) {
				throw new FetchException("Body of " + redact(uri) + " not received within " + requestTimeout.toSeconds() + " s", -1);
			}
			throw e;
		} finally {
			dog.cancel(false);
			synchronized (lock) {
				if (state[0] == 0) {
					state[0] = 1;
				}
			}
			if (timedOut.get()) {
				Thread.interrupted(); // clear the watchdog's interrupt; the timeout is reported above
			}
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
