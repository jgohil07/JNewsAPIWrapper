package com.main.java.sources;

import java.time.ZoneId;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One configured source, from {@code resources/sources.json}.
 *
 * @param type        {@code rss}, {@code nse-rss}, {@code newsapi-v2}, {@code gnews} or {@code marketaux}
 * @param region      {@code in} or {@code world}
 * @param kind        {@code news}, {@code filing} or {@code policy}
 * @param categories  site sections this source feeds
 * @param topics      topics every item of this source carries (the feed's own section)
 * @param maxAgeHours the source is STALE when its newest item is older than this
 * @param isPublic    may feed the public site (keyless sources whose terms allow linking)
 * @param agent       {@code default} or {@code browser} (for CDNs that drop other agents)
 * @param defaultZone zone for dates without one, or null to reject such dates
 * @param datePattern explicit date pattern (e.g. NSE), or null for RFC 822 / ISO 8601
 * @param fallback    queried only when a category has too few healthy primary sources (CLI only)
 * @param priority    lower wins when duplicates are merged
 * @param keyEnv      setting that holds the API key, for keyed sources
 * @author jgohil
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SourceConfig(String id, String name, String type, String url, String homepage, String region,
		String kind, String language, List<String> categories, List<String> topics, int maxAgeHours,
		@com.fasterxml.jackson.annotation.JsonProperty("public") boolean isPublic, String agent, String defaultZone,
		String datePattern, boolean fallback, int priority, String keyEnv) {

	public SourceConfig {
		categories = categories == null ? List.of() : List.copyOf(categories);
		topics = topics == null ? List.of() : List.copyOf(topics);
	}

	public ZoneId zone() {
		return defaultZone == null ? null : ZoneId.of(defaultZone);
	}

	public boolean browserAgent() {
		return "browser".equals(agent);
	}
}
