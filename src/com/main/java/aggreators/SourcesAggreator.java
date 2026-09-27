package com.main.java.aggreators;

import com.main.java.base.Constants;
import com.main.java.core.NewsApiException;
import com.main.java.models.Source;
import com.main.java.models.Sources;

/**
 * News API v1 sources, optionally filtered by language, country and category.
 * <p>
 * Every method either returns a complete {@link Sources} or throws: {@link com.main.java.core.FetchException},
 * {@link NewsApiException} or {@link IllegalArgumentException} (unsupported filter value, which News API would
 * otherwise answer with a silent empty list). The sources endpoint does not need an API key; the configured key is sent
 * when present.
 *
 * @author jgohil
 *
 */
public class SourcesAggreator extends Constants {

	public SourcesAggreator() {}

	/**
	 *
	 * @param uriSource full sources endpoint, e.g. https://newsapi.org/v1/sources
	 * @param apiKey News API key, or null
	 * @param language one of {@link Constants#languageSupported}, or null for all
	 * @param country one of {@link Constants#countrySupported}, or null for all
	 * @return the sources, never null
	 */
	public Sources getAllSources(String uriSource, String apiKey, String language, String country) {
		return fetch(uriSource, apiKey, language, country, null);
	}

	/**
	 * Picks up required parameters directly from the constants defined
	 *
	 */
	public Sources getAllSources() {
		return fetch(sourcesUri(), null, languageDefault, countryDefault, null);
	}

	/**
	 *
	 * @param uriSource full sources endpoint, e.g. https://newsapi.org/v1/sources
	 * @param apiKey News API key, or null
	 * @param language one of {@link Constants#languageSupported}, or null for all
	 * @param country one of {@link Constants#countrySupported}, or null for all
	 * @param category one of {@link Constants#categorySupported}, or null for all
	 * @return the sources, never null
	 */
	public Sources getSourcesForCategory(String uriSource, String apiKey, String language, String country, String category) {
		return fetch(uriSource, apiKey, language, country, category);
	}

	/**
	 *
	 * @param category one of {@link Constants#categorySupported}
	 * @return the sources, never null
	 */
	public Sources getSourcesForCategory(String category) {
		return fetch(sourcesUri(), null, languageDefault, countryDefault, category);
	}

	/**
	 * Picks up required parameters directly from the constants file
	 *
	 * @return the sources, never null
	 */
	public Sources getSourcesForCategory()
	{
		return fetch(sourcesUri(), null, languageDefault, countryDefault, categoryDefault);
	}

	private static String sourcesUri() {
		return baseURI + apiVersion + sourceURIPath;
	}

	private static Sources fetch(String uri, String explicitKey, String language, String country, String category) {
		NewsApiV1Client.requireOneOf("language", language, languageSupported);
		NewsApiV1Client.requireOneOf("country", country, countrySupported);
		NewsApiV1Client.requireOneOf("category", category, categorySupported);
		String key = explicitKey != null ? explicitKey : apiKey;
		Sources sources = NewsApiV1Client.get(uri, key,
				NewsApiV1Client.params("language", language, "country", country, "category", category), Sources.class);
		if (sources.getSources() == null) {
			throw new NewsApiException(null, "Response has no sources list");
		}
		for (Source s : sources.getSources()) {
			requireMatch("language", language, s.getLanguage(), s);
			requireMatch("country", country, s.getCountry(), s);
			requireMatch("category", category, s.getCategory(), s);
		}
		return sources;
	}

	private static void requireMatch(String field, String requested, String actual, Source s) {
		if (requested != null && actual != null && !requested.equals(actual)) {
			throw new NewsApiException(null, "Requested " + field + " '" + requested + "' but source '" + s.getId()
					+ "' has '" + actual + "'");
		}
	}
}
