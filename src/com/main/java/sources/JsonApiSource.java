package com.main.java.sources;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.java.config.Env;
import com.main.java.core.Dates;
import com.main.java.core.HttpFetcher;
import com.main.java.core.NewsException;

/**
 * Base for JSON news APIs. Subclasses build the request and map one article node.
 *
 * @author jgohil
 */
public abstract class JsonApiSource implements NewsSource {

	protected static final ObjectMapper MAPPER = new ObjectMapper();

	protected final SourceConfig config;
	protected final HttpFetcher http;

	protected JsonApiSource(SourceConfig config, HttpFetcher http) {
		this.config = config;
		this.http = http;
	}

	@Override
	public SourceConfig config() {
		return config;
	}

	@Override
	public String unavailableReason() {
		if (config.keyEnv() != null && Env.get(config.keyEnv()).isEmpty()) {
			return config.keyEnv() + " not set";
		}
		return null;
	}

	protected String key() {
		return Env.require(config.keyEnv());
	}

	/** GETs and parses JSON; an error body (non-2xx) is reported with the API's own message when it has one. */
	protected JsonNode getJson(URI uri, Map<String, String> headers) {
		HttpFetcher.Response r = http.fetch("GET", uri, headers, null);
		JsonNode root;
		try {
			root = MAPPER.readTree(r.body());
		} catch (IOException e) {
			HttpFetcher.requireSuccess(r);
			throw new NewsException(config.id() + ": response is not JSON");
		}
		if (!r.isSuccess()) {
			String msg = redactKey(firstText(root, "message", "error", "errors"));
			throw new NewsException(config.id() + ": HTTP " + r.status()
					+ (msg == null ? "" : " (" + msg.substring(0, Math.min(200, msg.length())) + ")"));
		}
		return root;
	}

	/** APIs sometimes quote the rejected key in their error text; never pass it on. */
	protected String redactKey(String message) {
		if (message == null || config.keyEnv() == null) {
			return message;
		}
		String key = Env.get(config.keyEnv()).orElse(null);
		return key == null ? message : message.replace(key, "***");
	}

	protected List<RawItem> mapArray(JsonNode array, String what) {
		if (array == null || !array.isArray()) {
			throw new NewsException(config.id() + ": response has no " + what + " array");
		}
		List<RawItem> out = new ArrayList<>();
		for (JsonNode n : array) {
			RawItem item = map(n);
			if (item != null) {
				out.add(item);
			}
		}
		return out;
	}

	/** @return the item, or null for entries the API marks as removed */
	protected abstract RawItem map(JsonNode article);

	protected RawItem item(String title, String url, String summary, String date, String sourceName) {
		return new RawItem(title, url, summary, Dates.parse(date, config.zone()).orElse(null), date, sourceName, List.of());
	}

	protected static String text(JsonNode n, String field) {
		JsonNode v = n.get(field);
		return v == null || v.isNull() ? null : v.asText();
	}

	protected static String query(Map<String, String> params) {
		StringJoiner q = new StringJoiner("&", "?", "");
		params.forEach((k, v) -> q.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
				+ URLEncoder.encode(v, StandardCharsets.UTF_8)));
		return q.toString();
	}

	private static String firstText(JsonNode root, String... fields) {
		if (root == null) {
			return null;
		}
		for (String f : fields) {
			JsonNode v = root.get(f);
			if (v != null && !v.isNull()) {
				return v.isValueNode() ? v.asText() : v.toString();
			}
		}
		return null;
	}
}
