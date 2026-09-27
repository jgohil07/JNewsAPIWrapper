package com.main.java.sources;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.main.java.core.NewsException;

/**
 * News API v2 top headlines. Configured URL carries the fixed parameters (e.g. {@code ?country=in&category=business});
 * the key goes in the {@code X-Api-Key} header. Free plan: development use only, 24 h delay (see research notes).
 *
 * @author jgohil
 */
public class NewsApiV2Source extends JsonApiSource {

	public NewsApiV2Source(SourceConfig config, com.main.java.core.HttpFetcher http) {
		super(config, http);
	}

	@Override
	public List<RawItem> fetch() {
		JsonNode root = getJson(URI.create(config.url()), Map.of("X-Api-Key", key(), "Accept", "application/json"));
		String status = text(root, "status");
		if (!"ok".equals(status)) {
			throw new NewsException(config.id() + ": " + text(root, "code") + ": " + text(root, "message"));
		}
		return mapArray(root.get("articles"), "articles");
	}

	@Override
	protected RawItem map(JsonNode a) {
		String title = text(a, "title");
		if ("[Removed]".equals(title)) {
			return null;
		}
		JsonNode src = a.get("source");
		return item(title, text(a, "url"), text(a, "description"), text(a, "publishedAt"),
				src == null ? null : text(src, "name"));
	}
}
