package com.main.java.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.main.java.core.FetchException;
import com.main.java.core.HttpFetcher;
import com.main.java.core.NewsException;

/**
 * Generic JSON-over-HTTP helpers from the original project, kept for source compatibility.
 * <p>
 * Behaviour since 2.0: every method throws {@link FetchException} on a transport error or a non-2xx status (HTTP 403
 * used to be treated as success) and {@link NewsException} on a body that is not the expected JSON, instead of
 * printing a stack trace and returning {@code null}. {@code null} is returned only for an empty 2xx body. Header name
 * and value arrays must have the same length. {@code parameters} is a pre-encoded query string appended after
 * {@code ?}.
 *
 * @author jgohil
 * @deprecated use {@link HttpFetcher} and Jackson directly.
 */
@Deprecated
public class JsonFactory {

	private static final String JSON_UTF8 = "application/json;charset=UTF-8";

	/**
	 * HTTP POST request with URL, Headers and JSON value and any parameters
	 *
	 * @param url
	 * @param json
	 * @param header
	 * @param parameters
	 * @return the response object, or null for an empty body
	 */
	public JSONObject postRequestJSON(String url, String json, String[] headerNames, String[] headerValues, String parameters){
		return toObject(send("POST", url, parameters, headerNames, headerValues, json), url);
	}

	/**
	 * HTTP GET Request with URL, header and parameters.
	 *
	 * @param url
	 * @param header
	 * @param parameters
	 * @return the object, or the array's objects, of the response
	 */
	public List<JSONObject> getRequest(String url, String[] headerNames, String[] headerValues, String parameters) {
		return toList(send("GET", url, parameters, headerNames, headerValues, null), url);
	}

	/**
	 *
	 * @param url
	 * @param header
	 * @return the object, or the array's objects, of the response
	 */
	public List<JSONObject> getRequestForRest(String url, String[] headerNames, String[] headerValues, String parameters) {
		return toList(send("GET", url, parameters, headerNames, headerValues, null), url);
	}

	/**
	 *
	 * HTTP POST requests with only URL and header are handled here - Ex Logout API
	 * @param url
	 * @param header
	 * @return the response object, or null for an empty body
	 */
	public JSONObject postRequestHeader(String url, String[] headerNames, String[] headerValues) {
		return toObject(send("POST", url, null, headerNames, headerValues, ""), url);
	}

	/**
	 * RESTful Get Method
	 *
	 * @param url
	 * @param header
	 * @return the response body
	 */
	public String httpGetRequestJson(String url, String[] headerNames, String[] headerValues, String parameters) {
		return send("GET", url, parameters, headerNames, headerValues, null);
	}

	/**
	 * HTTP GET method with default headers only
	 *
	 * @param url
	 * @param parameters
	 * @return the response body
	 */
	public String httpGetRequestJson(String url, String parameters) {
		return send("GET", url, parameters, null, null, null);
	}

	/**
	 * RESTFul POST Method
	 *
	 * @param url
	 * @param query
	 * @param header
	 * @return the response body
	 */
	public String httpPostRequestJson(String url, String query, String[] headerNames, String[] headerValues) {
		return send("POST", url, null, headerNames, headerValues, query);
	}

	/**
	 * RESTFul DELETE Method
	 *
	 * @param url
	 * @param header
	 * @return the response body
	 */
	public String httpDeleteRequestJson(String url, String[] headerNames, String[] headerValues) {
		return send("DELETE", url, null, headerNames, headerValues, null);
	}

	/**
	 * RESTFul PUT Method
	 *
	 * @param url
	 * @param query
	 * @param header
	 * @return the response body
	 */
	public String httpPutRequestJson(String url, String query, String[] headerNames, String[] headerValues, String parameters) {
		return send("PUT", url, parameters, headerNames, headerValues, query);
	}

	private static String send(String method, String url, String parameters, String[] headerNames,
			String[] headerValues, String body) {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("url must not be empty");
		}
		Map<String, String> headers = headers(headerNames, headerValues);
		if (body != null) {
			headers.putIfAbsent("Content-Type", JSON_UTF8);
		}
		URI uri = URI.create(parameters == null ? url : url + "?" + parameters);
		HttpFetcher.Response response = HttpFetcher.defaults().fetch(method, uri, headers, body);
		HttpFetcher.requireSuccess(response);
		return response.text();
	}

	private static Map<String, String> headers(String[] names, String[] values) {
		Map<String, String> headers = new HashMap<>();
		if (names == null && values == null) {
			return headers;
		}
		if (names == null || values == null || names.length != values.length) {
			throw new IllegalArgumentException("headerNames and headerValues must both be given with the same length");
		}
		for (int i = 0; i < names.length; i++) {
			headers.put(names[i], values[i]);
		}
		return headers;
	}

	private static JSONObject toObject(String content, String url) {
		if (content.isBlank()) {
			return null;
		}
		try {
			return new JSONObject(content);
		} catch (JSONException e) {
			throw new NewsException("Response from " + HttpFetcher.redact(URI.create(url)) + " is not a JSON object", e);
		}
	}

	private static List<JSONObject> toList(String content, String url) {
		String trimmed = content.strip();
		List<JSONObject> list = new ArrayList<JSONObject>();
		try {
			if (trimmed.startsWith("{")) {
				list.add(new JSONObject(trimmed));
				return list;
			}
			if (trimmed.startsWith("[")) {
				JSONArray array = new JSONArray(trimmed);
				for (int i = 0; i < array.length(); i++) {
					list.add(array.getJSONObject(i));
				}
				return list;
			}
		} catch (JSONException e) {
			throw new NewsException("Response from " + HttpFetcher.redact(URI.create(url)) + " is not valid JSON", e);
		}
		throw new NewsException("Response from " + HttpFetcher.redact(URI.create(url)) + " is not JSON");
	}
}
