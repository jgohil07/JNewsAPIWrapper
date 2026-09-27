package com.main.java.sources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.main.java.core.NewsException;

class SourceRegistryTest {

	private final SourceRegistry registry = SourceRegistry.load();

	@Test
	void bundledRegistryIsValid() {
		assertTrue(registry.sources().size() >= 40);
		assertEquals(13, registry.categories().size());
	}

	@Test
	void financeIsAParentOfTheFinanceSections() {
		assertTrue(registry.isParent("finance"));
		assertEquals(java.util.List.of("india-markets", "india-business", "economy", "filings", "business"), registry.children("finance"));
		assertEquals(Set.of("india-markets", "india-business", "economy", "filings", "business", "tech"),
				registry.expand(Set.of("finance", "tech")));
		assertTrue(registry.sourcesFor(Set.of("finance")).isEmpty(), "a parent holds no sources itself");
		assertFalse(registry.leaves().contains("finance"));
		for (String top : new String[] { "india", "world", "tech", "science", "health", "entertainment", "sports" }) {
			assertEquals(top, registry.category(top).group());
			assertFalse(registry.isParent(top));
		}
	}

	@Test
	void hierarchyMistakesAreRejected() {
		String cats = """
				{"categories":[{"id":"p","label":"P","group":"p","minHealthy":0,"required":false},
				 {"id":"c","label":"C","group":"p","minHealthy":1,"required":true},
				 {"id":"g","label":"G","group":"c","minHealthy":1,"required":true}],"sources":[]}""";
		assertThrows(NewsException.class, () -> SourceRegistry.parse(cats.getBytes(StandardCharsets.UTF_8)),
				"a sub-category of a sub-category");
		String sourceOnParent = """
				{"categories":[{"id":"p","label":"P","group":"p","minHealthy":0,"required":false},
				 {"id":"c","label":"C","group":"p","minHealthy":1,"required":true}],
				 "sources":[{"id":"a","name":"A","type":"rss","url":"https://a.example/f","homepage":null,"region":"world","kind":"news",
				 "language":"en","categories":["p"],"topics":[],"maxAgeHours":24,"public":true,"agent":"default",
				 "defaultZone":null,"datePattern":null,"fallback":false,"priority":1,"keyEnv":null}]}""";
		assertThrows(NewsException.class, () -> SourceRegistry.parse(sourceOnParent.getBytes(StandardCharsets.UTF_8)),
				"sources belong to leaf categories");
	}

	@Test
	void keyedAndAggregatorSourcesNeverReachThePublicSite() {
		for (SourceConfig s : registry.sources().values()) {
			if (s.keyEnv() != null || s.url().contains("news.google.com")) {
				assertFalse(s.isPublic(), s.id());
				assertTrue(s.fallback(), s.id());
			}
		}
	}

	@Test
	void everyCategoryCanMeetItsMinimumFromPublicPrimariesAlone() {
		for (CategoryConfig c : registry.categories().values()) {
			long n = registry.sourcesFor(Set.of(c.id())).stream().filter(SourceConfig::isPublic).filter(s -> !s.fallback()).count();
			// A required category must survive one source outage, or the site build would fail on a single hiccup.
			long needed = c.required() ? c.minHealthy() + 1 : c.minHealthy();
			assertTrue(n >= needed, c.id() + " has only " + n + " public primaries, needs " + needed);
		}
	}

	@Test
	void everySourceUrlIsHttps() {
		registry.sources().values().forEach(s -> assertTrue(s.url().startsWith("https://"), s.id()));
	}

	@Test
	void invalidConfigurationIsRejected() {
		String base = """
				{"categories":[{"id":"x","label":"X","group":"x","minHealthy":1,"required":true}],
				 "sources":[{"id":"a","name":"A","type":"rss","url":"%s","homepage":null,"region":"world","kind":"news",
				 "language":"en","categories":["x"],"topics":[],"maxAgeHours":24,"public":true,"agent":"default",
				 "defaultZone":null,"datePattern":null,"fallback":false,"priority":1,"keyEnv":null}]}""";
		SourceRegistry.parse(base.formatted("https://ok.example/feed").getBytes(StandardCharsets.UTF_8));
		assertThrows(NewsException.class, () -> SourceRegistry.parse(base.formatted("http://plain.example/feed").getBytes(StandardCharsets.UTF_8)));
		assertThrows(NewsException.class, () -> SourceRegistry.parse(base.formatted("https://ok.example/feed")
				.replace("\"priority\":1", "\"priority\":1,\"typo\":2").getBytes(StandardCharsets.UTF_8)));
		assertThrows(IllegalArgumentException.class, () -> registry.category("nope"));
	}
}
