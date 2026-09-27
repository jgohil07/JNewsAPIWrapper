package com.main.java.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.main.java.core.NewsItem;

/**
 * Top-level output document, schema {@code schema/news-envelope.v1.json}. {@code status} is {@code ok} or
 * {@code partial}; a failed run produces no envelope on stdout.
 *
 * @author jgohil
 */
public record Envelope(int schemaVersion, String generator, Instant generatedAt, Map<String, Object> query,
		String status, List<Aggregator.CategoryStatus> categories, List<SourceHealth> sources, List<NewsItem> items) {

	public static final int SCHEMA_VERSION = 1;
	public static final String GENERATOR = "JNewsAPIWrapper 2.0";

	public static Envelope of(Aggregator.Result r, Map<String, Object> query) {
		return new Envelope(SCHEMA_VERSION, GENERATOR, r.generatedAt(), query, r.status().name().toLowerCase(),
				r.categories(), r.sources(), r.items());
	}
}
