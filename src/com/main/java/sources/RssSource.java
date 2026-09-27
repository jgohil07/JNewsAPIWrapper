package com.main.java.sources;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.main.java.core.Dates;
import com.main.java.core.HttpFetcher;

/**
 * An RSS/Atom feed.
 *
 * @author jgohil
 */
public class RssSource implements NewsSource {

	protected final SourceConfig config;
	protected final HttpFetcher http;

	public RssSource(SourceConfig config, HttpFetcher http) {
		this.config = config;
		this.http = http;
	}

	@Override
	public SourceConfig config() {
		return config;
	}

	@Override
	public List<RawItem> fetch() {
		return fetch(config.url());
	}

	protected List<RawItem> fetch(String url) {
		Map<String, String> headers = config.browserAgent()
				? Map.of("User-Agent", HttpFetcher.BROWSER_USER_AGENT, "Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.1")
				: Map.of("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.1");
		HttpFetcher.Response response = http.get(URI.create(url), headers);
		List<FeedParser.Entry> entries = FeedParser.parse(response.body(), "Feed " + config.id());
		List<RawItem> items = new ArrayList<>(entries.size());
		for (FeedParser.Entry e : entries) {
			items.add(toRaw(e));
		}
		return items;
	}

	protected RawItem toRaw(FeedParser.Entry e) {
		String link = e.link() != null && !e.link().isBlank() ? e.link() : permalinkGuid(e.guid());
		String title = e.title();
		// Aggregators (Google News) append " - Publisher" to titles and name the publisher in <source>.
		if (title != null && e.sourceName() != null && title.endsWith(" - " + e.sourceName())) {
			title = title.substring(0, title.length() - e.sourceName().length() - 3);
		}
		return new RawItem(title, link == null ? null : link.strip(), e.description(), parseDate(e.date()), e.date(),
				e.sourceName(), List.of());
	}

	protected Instant parseDate(String raw) {
		if (config.datePattern() != null) {
			return Dates.parse(raw, config.datePattern(), config.zone()).orElse(null);
		}
		return Dates.parse(raw, config.zone()).orElse(null);
	}

	private static String permalinkGuid(String guid) {
		return guid != null && (guid.startsWith("http://") || guid.startsWith("https://")) ? guid : null;
	}
}
