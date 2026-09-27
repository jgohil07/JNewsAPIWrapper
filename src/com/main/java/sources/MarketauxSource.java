package com.main.java.sources;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * MarketAux {@code /v1/news/all}. The key is the {@code api_token} query parameter. Free plan: 100 requests a day,
 * 3 articles per request.
 *
 * @author jgohil
 */
public class MarketauxSource extends JsonApiSource {

	public MarketauxSource(SourceConfig config, com.main.java.core.HttpFetcher http) {
		super(config, http);
	}

	@Override
	public List<RawItem> fetch() {
		String sep = config.url().contains("?") ? "&" : "?";
		URI uri = URI.create(config.url() + sep + query(Map.of("api_token", key())).substring(1));
		JsonNode root = getJson(uri, Map.of("Accept", "application/json"));
		return mapArray(root.get("data"), "data");
	}

	@Override
	protected RawItem map(JsonNode a) {
		return item(text(a, "title"), text(a, "url"), text(a, "description"), text(a, "published_at"),
				text(a, "source"));
	}
}
