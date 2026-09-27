package com.main.java.pipeline;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.main.java.core.NewsItem;
import com.main.java.core.NewsItem.Coverage;
import com.main.java.core.NewsItem.SymbolTag;
import com.main.java.core.NewsItem.Topic;

/**
 * Merges copies of the same story: the same canonical URL (one article in several section feeds), or the same
 * normalised headline from different sources within {@link #TITLE_WINDOW}. The copy from the preferred source (lowest
 * priority value, then earliest) is kept; the others are listed in {@code alsoCoveredBy}.
 *
 * @author jgohil
 */
public final class Deduper {

	static final Duration TITLE_WINDOW = Duration.ofHours(36);
	/** Shorter headlines ("Live updates") are too generic to merge on. */
	static final int MIN_TITLE_KEY = 25;

	private static final Set<String> TRACKING = Set.of("fbclid", "gclid", "dclid", "mc_cid", "mc_eid", "ref",
			"ref_src", "cmpid", "icid", "ocid", "at_medium", "at_campaign", "at_link_id", "at_link_origin", "at_ptr_name",
			"at_bbc_team", "ns_mchannel", "ns_source", "ns_campaign", "ns_linkname", "ns_fee", "rss", "from", "cid",
			"smid", "partner");

	private Deduper() {}

	/**
	 * Lower-cased scheme and host without {@code www.}, no fragment, no tracking parameters, sorted remaining
	 * parameters, no trailing slash. Used only for identity; items keep their original URL.
	 */
	public static String canonical(String url) {
		try {
			URI u = new URI(url.strip());
			String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
			if (host.startsWith("www.")) {
				host = host.substring(4);
			}
			String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
			if (path.length() > 1 && path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			String query = "";
			if (u.getRawQuery() != null) {
				query = Arrays.stream(u.getRawQuery().split("&"))
						.filter(p -> !p.isEmpty())
						.filter(p -> {
							String k = p.split("=", 2)[0].toLowerCase(Locale.ROOT);
							return !k.startsWith("utm_") && !TRACKING.contains(k);
						})
						.sorted()
						.collect(Collectors.joining("&"));
			}
			String port = u.getPort() == -1 ? "" : ":" + u.getPort();
			return "https://" + host + port + path + (query.isEmpty() ? "" : "?" + query);
		} catch (URISyntaxException e) {
			return url.strip();
		}
	}

	/** Normalised headline used to spot the same story across sources, or null when too short to trust. */
	static String titleKey(String title) {
		String s = Normalizer.normalize(title, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
		s = s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
		return s.length() < MIN_TITLE_KEY ? null : s;
	}

	/**
	 * @param priority source id to priority (lower is preferred)
	 * @return one item per story, primary copy first chosen by priority then time
	 */
	public static List<NewsItem> merge(List<NewsItem> items, Map<String, Integer> priority) {
		int n = items.size();
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) {
			parent[i] = i;
		}
		Map<String, Integer> byId = new HashMap<>();
		Map<String, List<Integer>> byTitle = new HashMap<>();
		for (int i = 0; i < n; i++) {
			NewsItem it = items.get(i);
			Integer same = byId.putIfAbsent(it.id(), i);
			if (same != null) {
				union(parent, same, i);
			}
			String key = titleKey(it.title());
			if (key != null) {
				List<Integer> list = byTitle.computeIfAbsent(key, k -> new ArrayList<>());
				for (int j : list) {
					Duration gap = Duration.between(items.get(j).publishedAt(), it.publishedAt()).abs();
					if (gap.compareTo(TITLE_WINDOW) <= 0) {
						union(parent, j, i);
					}
				}
				list.add(i);
			}
		}
		Map<Integer, List<NewsItem>> groups = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(items.get(i));
		}
		Comparator<NewsItem> preferred = Comparator
				.comparingInt((NewsItem it) -> priority.getOrDefault(it.source().id(), Integer.MAX_VALUE))
				.thenComparing(NewsItem::publishedAt)
				.thenComparing(NewsItem::id);
		List<NewsItem> out = new ArrayList<>();
		for (List<NewsItem> g : groups.values()) {
			g.sort(preferred);
			out.add(combine(g));
		}
		return out;
	}

	private static NewsItem combine(List<NewsItem> group) {
		NewsItem primary = group.get(0);
		if (group.size() == 1) {
			return primary;
		}
		Set<String> categories = new LinkedHashSet<>(primary.categories());
		Map<String, Topic> topics = new LinkedHashMap<>();
		primary.topics().forEach(t -> topics.put(t.id(), t));
		Set<SymbolTag> symbols = new LinkedHashSet<>(primary.symbols());
		Map<String, Coverage> coverage = new LinkedHashMap<>();
		Set<String> seenSources = new LinkedHashSet<>();
		seenSources.add(primary.source().id());
		for (NewsItem other : group.subList(1, group.size())) {
			categories.addAll(other.categories());
			for (Topic t : other.topics()) {
				topics.merge(t.id(), t, (a, b) -> "feed".equals(a.by()) ? a : b);
			}
			symbols.addAll(other.symbols());
			// The same article re-listed in another section feed is not extra coverage.
			if (!other.id().equals(primary.id()) && seenSources.add(other.source().id())) {
				coverage.putIfAbsent(other.id(), new Coverage(other.source().id(), other.source().name(), other.url(),
						other.publishedAt()));
			}
		}
		return new NewsItem(primary.id(), primary.title(), primary.url(), primary.source(), primary.publishedAt(),
				primary.fetchedAt(), primary.summary(), primary.kind(), primary.region(), primary.language(),
				List.copyOf(categories), List.copyOf(topics.values()), List.copyOf(symbols), List.copyOf(coverage.values()));
	}

	private static int find(int[] p, int i) {
		while (p[i] != i) {
			p[i] = p[p[i]];
			i = p[i];
		}
		return i;
	}

	private static void union(int[] p, int a, int b) {
		int ra = find(p, a);
		int rb = find(p, b);
		if (ra != rb) {
			p[Math.max(ra, rb)] = Math.min(ra, rb);
		}
	}
}
