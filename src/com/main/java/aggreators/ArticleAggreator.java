package com.main.java.aggreators;

import com.main.java.base.Constants;
import com.main.java.core.NewsApiException;
import com.main.java.models.Articles;

/**
 * Articles of one News API v1 source.
 * <p>
 * Every method either returns a complete, validated {@link Articles} or throws: {@link com.main.java.core.FetchException}
 * (network or HTTP failure), {@link NewsApiException} (API error or a response that does not match the request),
 * {@link com.main.java.config.ConfigException} (no API key) or {@link IllegalArgumentException} (unsupported sortBy).
 *
 * @author jgohil
 *
 */
public class ArticleAggreator extends Constants {

	/**
	 *
	 * @param uriSource full articles endpoint, e.g. https://newsapi.org/v1/articles
	 * @param apiKey News API key; when null the configured NEWSAPI_KEY is used
	 * @param sortBy one of {@link Constants#sortbySupported}, or null for the API default
	 * @param source source id, e.g. cnn
	 * @return the articles, never null
	 */
	public Articles getArticlesForSource(String uriSource, String apiKey, String sortBy, String source) {
		return fetch(uriSource, apiKey, sortBy, source);
	}

	/**
	 *
	 * @param sortBy one of {@link Constants#sortbySupported}
	 * @param source source id, e.g. cnn
	 * @return the articles, never null
	 */
	public Articles getArticlesForSource(String sortBy, String source) {
		return fetch(articlesUri(), null, sortBy, source);
	}

	/**
	 *
	 * @param source source id, e.g. cnn
	 * @return the articles for the default sort order, never null
	 */
	public Articles getArticlesForSource(String source) {
		return fetch(articlesUri(), null, sortByDefault, source);
	}

	/**
	 * Picks up required parameters directly from the constants file
	 *
	 * @return the articles of the default source in the default sort order, never null
	 */
	public Articles getArticlesForSource() {
		return fetch(articlesUri(), null, sortByDefault, sourceDefault);
	}

	private static String articlesUri() {
		return baseURI + apiVersion + articlesURIPath;
	}

	private static Articles fetch(String uri, String explicitKey, String sortBy, String source) {
		if (source == null || source.isBlank()) {
			throw new IllegalArgumentException("source must not be empty");
		}
		NewsApiV1Client.requireOneOf("sortBy", sortBy, sortbySupported);
		String key = NewsApiV1Client.requireKey(explicitKey, apiKey);
		Articles articles = NewsApiV1Client.get(uri, key,
				NewsApiV1Client.params("source", source, "sortBy", sortBy), Articles.class);

		if (articles.getArticles() == null) {
			throw new NewsApiException(null, "Response for source '" + source + "' has no articles list");
		}
		if (articles.getSource() != null && !articles.getSource().equals(source)) {
			throw new NewsApiException(null, "Requested source '" + source + "' but the API returned '"
					+ articles.getSource() + "'");
		}
		if (sortBy != null && articles.getSortBy() != null && !articles.getSortBy().equals(sortBy)) {
			throw new NewsApiException(null, "Requested sortBy '" + sortBy + "' but the API returned '"
					+ articles.getSortBy() + "' articles");
		}
		return articles;
	}
}
