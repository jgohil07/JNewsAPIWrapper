package com.main.java.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.main.java.aggreators.ArticleAggreator;
import com.main.java.aggreators.SourcesAggreator;
import com.main.java.base.Constants;
import com.main.java.config.ConfigException;
import com.main.java.core.FetchException;
import com.main.java.core.NewsApiException;
import com.main.java.models.Articles;
import com.main.java.models.Source;
import com.main.java.models.Sources;
import com.main.java.testutil.FixtureServer;
import com.main.java.testutil.FixtureServer.Reply;

/** Behaviour of the original News API v1 wrapper after the 2.0 fixes, against a local fixture server. */
class LegacyNewsApiTest {

	/** Gives the test access to the protected static configuration of {@link Constants}. */
	static final class Config extends Constants {
		static String[] save() {
			return new String[] { baseURI, apiKey };
		}

		static void restore(String[] saved) {
			baseURI = saved[0];
			apiKey = saved[1];
		}

		static void point(String base, String key) {
			baseURI = base;
			apiKey = key;
		}
	}

	private static final String ARTICLES_JSON = """
			{"status":"ok","source":"cnn","sortBy":"top","articles":[
			 {"author":"A","title":"Rupee — ₹ gains","description":"d","url":"https://example.com/a",
			  "urlToImage":"https://example.com/a.jpg","publishedAt":"2026-09-27T06:00:00Z"}]}""";

	private static final String SOURCES_JSON = """
			{"status":"ok","sources":[{"id":"cnn","name":"CNN","description":"x","url":"https://cnn.com",
			 "category":"general","language":"en","country":"us",
			 "urlsToLogos":{"small":"","medium":"","large":""},"sortBysAvailable":["top"]}]}""";

	private FixtureServer server;
	private String[] saved;

	@BeforeEach
	void setUp() throws IOException {
		server = new FixtureServer();
		saved = Config.save();
		Config.point(server.url("/"), "test-key-123");
	}

	@AfterEach
	void tearDown() {
		Config.restore(saved);
		server.close();
	}

	@Test
	void articlesMapSourceAndSortByAndSendKeyInHeaderOnly() {
		server.on("/v1/articles", Reply.json(ARTICLES_JSON));

		Articles a = new ArticleAggreator().getArticlesForSource("cnn");

		assertEquals("cnn", a.getSource(), "bug 1: source used to be mapped from 'sourceDefault' and stay null");
		assertEquals("top", a.getSortBy());
		assertEquals("Rupee — ₹ gains", a.getArticles().get(0).getTitle(), "bug 9: UTF-8 decoding");
		FixtureServer.Request r = server.lastRequest();
		assertEquals("source=cnn&sortBy=top", r.query(), "bug 3: defaults used to be sent as bare tokens");
		assertEquals("test-key-123", r.headers().getFirst("X-Api-Key"));
		assertFalse(r.query().contains("test-key-123"), "key must not appear in the URL");
	}

	@Test
	void noArgVariantUsesDefaultSource() {
		server.on("/v1/articles", Reply.json(ARTICLES_JSON));
		new ArticleAggreator().getArticlesForSource();
		assertEquals("source=cnn&sortBy=top", server.lastRequest().query());
	}

	@Test
	void queryValuesAreEncoded() {
		server.on("/v1/articles", Reply.json(ARTICLES_JSON.replace("\"cnn\"", "\"a&b=c\"")));
		new ArticleAggreator().getArticlesForSource("a&b=c");
		assertEquals("source=a%26b%3Dc&sortBy=top", server.lastRequest().query(), "bug 7");
	}

	@Test
	void unsupportedSortByIsRejectedBeforeAnyRequest() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> new ArticleAggreator().getArticlesForSource("latest", "cnn"));
		assertTrue(e.getMessage().contains("top"));
		assertTrue(server.requests().isEmpty());
	}

	@Test
	void mismatchedSourceInResponseFailsLoud() {
		server.on("/v1/articles", Reply.json(ARTICLES_JSON.replace("\"source\":\"cnn\"", "\"source\":\"bbc-news\"")));
		assertThrows(NewsApiException.class, () -> new ArticleAggreator().getArticlesForSource("cnn"));
	}

	@Test
	void apiErrorBecomesNewsApiExceptionWithCode() {
		server.on("/v1/articles", Reply.of(401, "application/json",
				"{\"status\":\"error\",\"code\":\"apiKeyInvalid\",\"message\":\"Your API key is invalid.\"}"));
		NewsApiException e = assertThrows(NewsApiException.class, () -> new ArticleAggreator().getArticlesForSource("cnn"));
		assertEquals("apiKeyInvalid", e.getCode());
		assertFalse(e.getMessage().contains("test-key-123"));
	}

	@Test
	void http403IsAFailureNotData() {
		server.on("/v1/articles", Reply.of(403, "text/html", "<html>Access Denied</html>"));
		FetchException e = assertThrows(FetchException.class, () -> new ArticleAggreator().getArticlesForSource("cnn"));
		assertEquals(403, e.getStatus(), "bug 5: 403 used to be read as a successful body");
	}

	@Test
	void okStatusWithoutArticlesFailsLoud() {
		server.on("/v1/articles", Reply.json("{\"status\":\"ok\",\"source\":\"cnn\",\"sortBy\":\"top\"}"));
		assertThrows(NewsApiException.class, () -> new ArticleAggreator().getArticlesForSource("cnn"));
	}

	@Test
	void missingKeyIsAConfigError() {
		Config.point(server.url("/"), null);
		ConfigException e = assertThrows(ConfigException.class, () -> new ArticleAggreator().getArticlesForSource("cnn"));
		assertTrue(e.getMessage().contains("NEWSAPI_KEY"));
		assertTrue(server.requests().isEmpty());
	}

	@Test
	void sourcesMapCategoryLanguageCountry() {
		server.on("/v1/sources", Reply.json(SOURCES_JSON));

		Sources s = new SourcesAggreator().getSourcesForCategory("general");

		Source cnn = s.getSources().get(0);
		assertEquals("general", cnn.getCategory(), "bug 2: used to be mapped from 'categoryDefault'");
		assertEquals("en", cnn.getLanguage());
		assertEquals("us", cnn.getCountry());
		assertEquals("language=en&country=us&category=general", server.lastRequest().query());
	}

	@Test
	void explicitVariantOmitsNullFilters() {
		server.on("/v1/sources", Reply.json(SOURCES_JSON));
		new SourcesAggreator().getAllSources(server.url("/v1/sources"), null, "en", null);
		assertEquals("language=en", server.lastRequest().query());
	}

	@Test
	void obsoleteCategoryIsRejectedInsteadOfSilentlyEmpty() {
		assertThrows(IllegalArgumentException.class, () -> new SourcesAggreator().getSourcesForCategory("sport"));
		assertTrue(server.requests().isEmpty());
	}

	@Test
	void sourceNotMatchingRequestedFilterFailsLoud() {
		server.on("/v1/sources", Reply.json(SOURCES_JSON.replace("\"category\":\"general\"", "\"category\":\"sports\"")));
		assertThrows(NewsApiException.class, () -> new SourcesAggreator().getSourcesForCategory("general"));
	}

	@Test
	void sourcesWorkWithoutKey() {
		Config.point(server.url("/"), null);
		server.on("/v1/sources", Reply.json(SOURCES_JSON));
		new SourcesAggreator().getAllSources();
		assertNull(server.lastRequest().headers().getFirst("X-Api-Key"));
	}

	@Test
	void nonJsonSuccessIsAFailure() {
		server.on("/v1/sources", Reply.of(200, "text/html", "<html>maintenance</html>"));
		assertThrows(NewsApiException.class, () -> new SourcesAggreator().getAllSources());
	}
}
