package com.main.java.pipeline;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.main.java.core.NewsItem;
import com.main.java.core.NewsItem.SourceRef;
import com.main.java.core.NewsItem.Topic;
import com.main.java.core.Text;
import com.main.java.sources.RawItem;
import com.main.java.sources.SourceConfig;

/**
 * Turns raw items into validated {@link NewsItem}s and judges the source. Invalid items are dropped and counted,
 * never repaired; a source with too many invalid items, no items, or only old items is not used at all.
 *
 * @author jgohil
 */
public final class Validator {

	static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(15);
	static final Instant EARLIEST = Instant.parse("2000-01-01T00:00:00Z");
	/** More than this share of invalid items fails the whole source (its format probably changed). */
	static final double MAX_REJECT_RATIO = 0.2;
	static final int MAX_TITLE = 400;
	static final int MAX_URL = 2048;
	static final int MAX_SUMMARY = 500;

	/** Validated items (empty unless the source is ok) and the source's health. */
	public record Outcome(List<NewsItem> items, SourceHealth health) {}

	private Validator() {}

	public static Outcome validate(SourceConfig cfg, List<RawItem> raw, Instant fetchedAt, Instant now) {
		Map<String, NewsItem> valid = new LinkedHashMap<>();
		Map<String, Integer> reasons = new LinkedHashMap<>();
		int rejected = 0;
		Instant newest = null;
		for (RawItem r : raw) {
			String reason = check(r, now);
			if (reason != null) {
				rejected++;
				reasons.merge(reason, 1, Integer::sum);
				continue;
			}
			NewsItem item = toItem(cfg, r, fetchedAt);
			valid.putIfAbsent(item.id(), item);
			if (newest == null || item.publishedAt().isAfter(newest)) {
				newest = item.publishedAt();
			}
		}
		List<NewsItem> items = new ArrayList<>(valid.values());
		String error = null;
		String status = SourceHealth.OK;
		if (raw.isEmpty()) {
			status = SourceHealth.FAILED;
			error = "feed lists no items";
		} else if (rejected > raw.size() * MAX_REJECT_RATIO) {
			status = SourceHealth.FAILED;
			error = rejected + " of " + raw.size() + " items invalid (" + summarise(reasons) + ")";
		} else if (newest.isBefore(now.minus(Duration.ofHours(cfg.maxAgeHours())))) {
			status = SourceHealth.STALE;
			error = "newest item is " + Duration.between(newest, now).toHours() + " h old (limit " + cfg.maxAgeHours() + " h)";
		}
		SourceHealth health = new SourceHealth(cfg.id(), cfg.name(), cfg.homepage(), cfg.categories(), cfg.fallback(),
				status, items.size(), rejected, newest, cfg.maxAgeHours(), 0, error,
				rejected > 0 && error == null ? rejected + " item(s) dropped: " + summarise(reasons) : null);
		return new Outcome(SourceHealth.OK.equals(status) ? items : List.of(), health);
	}

	/** Health for a source that could not be read at all. */
	public static SourceHealth failed(SourceConfig cfg, String status, String error) {
		return new SourceHealth(cfg.id(), cfg.name(), cfg.homepage(), cfg.categories(), cfg.fallback(), status, 0, 0,
				null, cfg.maxAgeHours(), 0, error, null);
	}

	/** @return why {@code r} is invalid, or null */
	static String check(RawItem r, Instant now) {
		String title = Text.plain(r.title());
		if (title == null || title.isEmpty()) {
			return "no title";
		}
		if (title.length() > MAX_TITLE) {
			return "title too long";
		}
		String urlProblem = checkUrl(r.url());
		if (urlProblem != null) {
			return urlProblem;
		}
		if (r.published() == null) {
			return r.publishedRaw() == null || r.publishedRaw().isBlank() ? "no date" : "unreadable date";
		}
		if (r.published().isAfter(now.plus(FUTURE_TOLERANCE))) {
			return "date in the future";
		}
		if (r.published().isBefore(EARLIEST)) {
			return "date before 2000";
		}
		return null;
	}

	static String checkUrl(String url) {
		if (url == null || url.isBlank()) {
			return "no link";
		}
		if (url.length() > MAX_URL) {
			return "link too long";
		}
		try {
			URI u = new URI(url.strip());
			String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
			if (!scheme.equals("http") && !scheme.equals("https")) {
				return "link is not http(s)";
			}
			if (u.getHost() == null || u.getHost().isBlank()) {
				return "link has no host";
			}
			if (u.getRawUserInfo() != null) {
				return "link has user info";
			}
		} catch (URISyntaxException e) {
			return "malformed link";
		}
		return null;
	}

	private static NewsItem toItem(SourceConfig cfg, RawItem r, Instant fetchedAt) {
		String url = r.url().strip();
		String title = Text.plain(r.title());
		String summary = Text.plain(r.summaryHtml(), MAX_SUMMARY);
		if (summary != null && (summary.isEmpty() || summary.equalsIgnoreCase(title))) {
			summary = null;
		}
		String name = r.sourceName() == null || r.sourceName().isBlank() ? cfg.name()
				: cfg.name() + " · " + Text.plain(r.sourceName());
		Set<Topic> topics = new LinkedHashSet<>();
		for (String t : cfg.topics()) {
			topics.add(new Topic(t, "feed"));
		}
		return new NewsItem(id(url), title, url, new SourceRef(cfg.id(), name), r.published(), fetchedAt, summary,
				cfg.kind(), cfg.region(), cfg.language(), cfg.categories(), List.copyOf(topics), r.symbols(), List.of());
	}

	/** sha256 (hex) of the canonical form of {@code url}. */
	public static String id(String url) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(Deduper.canonical(url).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(d);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String summarise(Map<String, Integer> reasons) {
		StringBuilder sb = new StringBuilder();
		reasons.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : ", ").append(v).append(' ').append(k));
		return sb.toString();
	}
}
