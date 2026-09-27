package com.main.java.aggreators;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.java.config.ConfigException;
import com.main.java.core.FetchException;
import com.main.java.core.HttpFetcher;
import com.main.java.core.NewsApiException;

/**
 * Shared request logic for the News API v1 aggregators. Builds properly encoded query strings, sends the key in the
 * {@code X-Api-Key} header (never in the URL), and turns every failure into an exception instead of {@code null}.
 *
 * @author jgohil
 */
final class NewsApiV1Client {

	static final String API_KEY_SETTING = "NEWSAPI_KEY";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private NewsApiV1Client() {}

	/**
	 * GETs {@code uri} with {@code params} and maps the JSON body to {@code type}.
	 *
	 * @param apiKey key to send, or null to send none
	 * @throws FetchException     on transport failure or a non-JSON error response
	 * @throws NewsApiException   when the API answers {@code "status": "error"} or anything but {@code "ok"}
	 */
	static <T> T get(String uri, String apiKey, Map<String, String> params, Class<T> type) {
		if (uri == null || uri.isBlank()) {
			throw new IllegalArgumentException("News API URI must not be empty");
		}
		URI target = URI.create(uri + query(params));
		Map<String, String> headers = new HashMap<>();
		headers.put("Accept", "application/json");
		if (apiKey != null) {
			headers.put("X-Api-Key", apiKey);
		}
		HttpFetcher.Response response = HttpFetcher.defaults().fetch("GET", target, headers, null);

		JsonNode root;
		try {
			root = MAPPER.readTree(response.body());
		} catch (IOException e) {
			HttpFetcher.requireSuccess(response);
			throw new NewsApiException(null, "Response from " + HttpFetcher.redact(target) + " is not valid JSON");
		}
		if (root == null || !root.isObject()) {
			HttpFetcher.requireSuccess(response);
			throw new NewsApiException(null, "Response from " + HttpFetcher.redact(target) + " is not a JSON object");
		}
		String status = root.path("status").asText(null);
		if (!"ok".equals(status)) {
			String code = root.path("code").asText(null);
			String message = root.path("message").asText("status was " + status);
			throw new NewsApiException(code, message + " (HTTP " + response.status() + " from "
					+ HttpFetcher.redact(target) + ")");
		}
		HttpFetcher.requireSuccess(response);
		try {
			return MAPPER.treeToValue(root, type);
		} catch (JsonProcessingException e) {
			throw new NewsApiException(null, "Unexpected JSON shape from " + HttpFetcher.redact(target) + ": "
					+ e.getOriginalMessage());
		}
	}

	/** @return {@code explicit} when given, otherwise the configured key; never null */
	static String requireKey(String explicit, String configured) {
		if (explicit != null && !explicit.isBlank()) {
			return explicit;
		}
		if (configured != null && !configured.isBlank()) {
			return configured;
		}
		throw new ConfigException("Missing required setting " + API_KEY_SETTING
				+ ". Set it as an environment variable or in .env (see .env.example).");
	}

	/** @throws IllegalArgumentException when {@code value} is non-null and not one of {@code allowed} */
	static void requireOneOf(String name, String value, String[] allowed) {
		if (value != null && Arrays.stream(allowed).noneMatch(value::equals)) {
			throw new IllegalArgumentException("Unsupported " + name + " '" + value + "'. News API v1 supports: "
					+ String.join(", ", allowed));
		}
	}

	/** Parameters in insertion order; null values are omitted. */
	static Map<String, String> params(String... keyValues) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < keyValues.length; i += 2) {
			if (keyValues[i + 1] != null) {
				map.put(keyValues[i], keyValues[i + 1]);
			}
		}
		return map;
	}

	static String query(Map<String, String> params) {
		if (params == null || params.isEmpty()) {
			return "";
		}
		StringJoiner q = new StringJoiner("&", "?", "");
		params.forEach((k, v) -> q.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
				+ URLEncoder.encode(v, StandardCharsets.UTF_8)));
		return q.toString();
	}
}
