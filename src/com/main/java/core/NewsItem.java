package com.main.java.core;

import java.time.Instant;
import java.util.List;

/**
 * One validated news item, as emitted by the CLI and the site builder (schema {@code news-envelope.v1}).
 *
 * @param id            sha256 of the canonical URL (hex)
 * @param kind          {@code news}, {@code filing} (exchange announcement) or {@code policy} (regulator)
 * @param region        {@code in} or {@code world}
 * @param categories    site sections from the source configuration (authoritative)
 * @param topics        finer topics, each recording whether it came from the feed section or a keyword rule
 * @param symbols       NSE symbols, each recording how it was matched
 * @param alsoCoveredBy other sources that published the same story
 * @author jgohil
 */
public record NewsItem(String id, String title, String url, SourceRef source, Instant publishedAt, Instant fetchedAt,
		String summary, String kind, String region, String language, List<String> categories, List<Topic> topics,
		List<SymbolTag> symbols, List<Coverage> alsoCoveredBy) {

	public NewsItem {
		categories = List.copyOf(categories);
		topics = List.copyOf(topics);
		symbols = List.copyOf(symbols);
		alsoCoveredBy = List.copyOf(alsoCoveredBy);
	}

	/** Where an item came from. */
	public record SourceRef(String id, String name) {}

	/** @param by {@code feed} (the source's own section) or {@code rule} (keyword rule in taxonomy.json) */
	public record Topic(String id, String by) {}

	/** @param match {@code exact} (the exchange tagged it) or {@code name} (company name found in the text) */
	public record SymbolTag(String symbol, String match) {}

	/** Another source's copy of the same story. */
	public record Coverage(String sourceId, String sourceName, String url, Instant publishedAt) {}

	public NewsItem withTopics(List<Topic> t) {
		return new NewsItem(id, title, url, source, publishedAt, fetchedAt, summary, kind, region, language, categories,
				t, symbols, alsoCoveredBy);
	}

	public NewsItem withSymbols(List<SymbolTag> s) {
		return new NewsItem(id, title, url, source, publishedAt, fetchedAt, summary, kind, region, language, categories,
				topics, s, alsoCoveredBy);
	}

	public NewsItem withAlsoCoveredBy(List<Coverage> c) {
		return new NewsItem(id, title, url, source, publishedAt, fetchedAt, summary, kind, region, language, categories,
				topics, symbols, c);
	}
}
