package com.main.java.sources;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * GNews v4 top headlines. The API only accepts the key as the {@code apikey} query parameter; error messages redact
 * query strings. Free plan: non-commercial development use only.
 *
 * @author jgohil
 */
public class GNewsSource extends JsonApiSource {

	public GNewsSource(SourceConfig config, com.main.java.core.HttpFetcher http) {
		super(config, http);
	}

	@Override
	public List<RawItem> fetch() {
		String sep = config.url().contains("?") ? "&" : "?";
		URI uri = URI.create(config.url() + sep + query(Map.of("apikey", key())).substring(1));
		JsonNode root = getJson(uri, Map.of("Accept", "application/json"));
		return mapArray(root.get("articles"), "articles");
	}

	@Override
	protected RawItem map(JsonNode a) {
		JsonNode src = a.get("source");
		return item(text(a, "title"), text(a, "url"), text(a, "description"), text(a, "publishedAt"),
				src == null ? null : text(src, "name"));
	}
}
