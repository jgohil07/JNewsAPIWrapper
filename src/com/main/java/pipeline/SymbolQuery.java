package com.main.java.pipeline;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.main.java.core.HttpFetcher;
import com.main.java.core.NewsItem;
import com.main.java.core.NewsItem.SymbolTag;
import com.main.java.sources.NewsSource;
import com.main.java.sources.NseSymbolAnnouncementsSource;
import com.main.java.sources.NseSymbolMaster;
import com.main.java.sources.RssSource;
import com.main.java.sources.SourceConfig;
import com.main.java.sources.SourceRegistry;

/**
 * News for one NSE symbol: the exchange's own announcements for the symbol ({@code match: exact}), plus headlines
 * from Google News search and the India finance feeds that name the company ({@code match: name}). Name matches are
 * whole-phrase and skip occurrences inside a longer proper noun ("Bank of India" in "State Bank of India").
 *
 * @author jgohil
 */
public final class SymbolQuery {

	/** Same rule Thesis-Engine applies to symbols. */
	public static final Pattern SYMBOL = Pattern.compile("^[A-Z0-9&-]{1,20}$");

	private static final Pattern SUFFIX = Pattern.compile(
			"(?i)[\\s,]+(limited|ltd\\.?|private limited|pvt\\.? ltd\\.?)$");

	static final Set<String> FINANCE_CATEGORIES = Set.of("india-markets", "india-business", "economy");

	private final SourceRegistry registry;
	private final Aggregator aggregator;
	private final HttpFetcher http;
	private final Clock clock;

	public SymbolQuery(SourceRegistry registry, Aggregator aggregator, HttpFetcher http, Clock clock) {
		this.registry = registry;
		this.aggregator = aggregator;
		this.http = http;
		this.clock = clock;
	}

	/**
	 * @param company the verified listing (from {@link NseSymbolMaster})
	 * @param since   window; also bounds the Google News search
	 */
	public Aggregator.Result run(NseSymbolMaster.Company company, Duration since, Duration deadline) {
		String name = shortName(company.name());
		Pattern namePattern = namePattern(name);

		SourceConfig nseCfg = new SourceConfig("nse-symbol", "NSE · Announcements for " + company.symbol(), "nse-api",
				NseSymbolAnnouncementsSource.API, "https://www.nseindia.com/get-quotes/equity?symbol="
						+ URLEncoder.encode(company.symbol(), StandardCharsets.UTF_8),
				"in", "filing", "en", List.of("filings"), List.of("filings"), 24 * 365 * 5, false, "browser",
				"Asia/Kolkata", "dd-MMM-yyyy HH:mm:ss", false, 1, null);
		long days = Math.max(1, (since.toHours() + 23) / 24);
		String q = "\"" + name + "\" when:" + days + "d";
		SourceConfig googleCfg = new SourceConfig("google-news-symbol", "Google News · search", "rss",
				"https://news.google.com/rss/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
						+ "&hl=en-IN&gl=IN&ceid=IN:en",
				"https://news.google.com/", "in", "news", "en", List.of("india-business"), List.of(), 24 * 30, false,
				"default", null, null, false, 40, null);

		List<NewsSource> direct = List.of(new NseSymbolAnnouncementsSource(nseCfg, http, company.symbol()),
				new RssSource(googleCfg, http));
		Map<String, Validator.Outcome> outcomes = new LinkedHashMap<>(aggregator.fetchSources(direct, deadline));
		outcomes.putAll(aggregator.fetchAll(registry.sourcesFor(FINANCE_CATEGORIES).stream()
				.filter(s -> !s.fallback()).toList(), deadline));

		Instant now = clock.instant();
		Instant cutoff = now.minus(since);
		List<NewsItem> matched = new ArrayList<>();
		for (Map.Entry<String, Validator.Outcome> e : outcomes.entrySet()) {
			boolean exact = e.getKey().equals(nseCfg.id());
			for (NewsItem item : e.getValue().items()) {
				if (item.publishedAt().isBefore(cutoff)) {
					continue;
				}
				if (exact) {
					matched.add(item);
				} else if (mentions(namePattern, item)) {
					matched.add(item.withSymbols(List.of(new SymbolTag(company.symbol(), "name"))));
				}
			}
		}
		Map<String, Integer> priority = new HashMap<>();
		registry.sources().values().forEach(s -> priority.put(s.id(), s.priority()));
		priority.put(nseCfg.id(), nseCfg.priority());
		priority.put(googleCfg.id(), googleCfg.priority());
		List<NewsItem> items = Deduper.merge(matched, priority).stream()
				.sorted(Comparator.comparing(NewsItem::publishedAt).reversed().thenComparing(NewsItem::id))
				.toList();

		List<SourceHealth> health = outcomes.values().stream().map(Validator.Outcome::health).toList();
		boolean nseOk = outcomes.get(nseCfg.id()).health().isOk();
		boolean anyBad = health.stream().anyMatch(h -> !h.isOk() && !SourceHealth.SKIPPED.equals(h.status()));
		boolean anyOk = health.stream().anyMatch(SourceHealth::isOk);
		Aggregator.Status status = !anyOk ? Aggregator.Status.FAILED
				: (!nseOk || anyBad) ? Aggregator.Status.PARTIAL : Aggregator.Status.OK;
		List<Aggregator.CategoryStatus> cats = List.of(new Aggregator.CategoryStatus("filings", "NSE Filings",
				"india-finance", nseOk ? 1 : 0, 1, true, nseOk));
		return new Aggregator.Result(now, status, items, health, cats);
	}

	/** "Madhya Bharat Agro Products Limited" to "Madhya Bharat Agro Products". */
	static String shortName(String registered) {
		String s = registered.strip();
		String prev;
		do {
			prev = s;
			s = SUFFIX.matcher(s).replaceAll("").strip();
		} while (!s.equals(prev) && !s.isEmpty());
		return s.isEmpty() ? registered.strip() : s;
	}

	/** Whole-phrase match, case-sensitive as registered or in capitals ("Oil India" never matches "oil india"). */
	static Pattern namePattern(String shortName) {
		return Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + phrase(shortName) + "|" + phrase(shortName.toUpperCase(Locale.ROOT))
				+ ")(?![\\p{L}\\p{N}])");
	}

	private static String phrase(String name) {
		StringBuilder re = new StringBuilder();
		String[] words = name.split("\\s+");
		for (int i = 0; i < words.length; i++) {
			re.append(i == 0 ? "" : "[\\s-]+").append(Pattern.quote(words[i]));
		}
		return re.toString();
	}

	static boolean mentions(Pattern name, NewsItem item) {
		return mentions(name, item.title()) || (item.summary() != null && mentions(name, item.summary()));
	}

	/** True when {@code text} names the company and the match is not the tail of a longer capitalised name. */
	static boolean mentions(Pattern name, String text) {
		Matcher m = name.matcher(text);
		while (m.find()) {
			String before = text.substring(0, m.start()).stripTrailing();
			int sp = Math.max(before.lastIndexOf(' '), before.lastIndexOf('\n'));
			String prevWord = before.substring(sp + 1);
			boolean partOfLongerName = !prevWord.isEmpty() && Character.isUpperCase(prevWord.codePointAt(0))
					&& !isSentenceBoundary(before) && !COMMON_LEADS.contains(prevWord.toLowerCase(Locale.ROOT));
			if (!partOfLongerName) {
				return true;
			}
		}
		return false;
	}

	/** Capitalised words that commonly precede a company name without being part of it. */
	private static final Set<String> COMMON_LEADS = Set.of("the", "at", "on", "in", "for", "from", "by", "with", "and",
			"or", "why", "how", "what", "shares", "stock", "stocks", "buy", "sell", "hold", "q1", "q2", "q3", "q4");

	private static boolean isSentenceBoundary(String before) {
		if (before.isEmpty()) {
			return true;
		}
		char c = before.charAt(before.length() - 1);
		return c == '.' || c == ':' || c == '|' || c == '-' || c == '–' || c == '—' || c == '"' || c == '“';
	}
}
