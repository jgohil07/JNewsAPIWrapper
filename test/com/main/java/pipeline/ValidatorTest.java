package com.main.java.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.main.java.core.NewsItem;
import com.main.java.sources.RawItem;
import com.main.java.sources.SourceConfig;

class ValidatorTest {

	static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");

	static SourceConfig cfg(int maxAgeHours) {
		return new SourceConfig("src", "Source", "rss", "https://s.example/feed", null, "in", "news", "en",
				List.of("india-markets"), List.of("markets"), maxAgeHours, true, "default", null, null, false, 10, null);
	}

	static RawItem item(String title, String url, Instant published) {
		return RawItem.of(title, url, "<p>Summary text</p>", published, published == null ? null : published.toString());
	}

	@Test
	void validItemsBecomeNewsItems() {
		Validator.Outcome o = Validator.validate(cfg(72),
				List.of(item("Sensex &amp; Nifty", "https://s.example/a?utm_source=rss", NOW.minusSeconds(60))), NOW, NOW);
		assertEquals(SourceHealth.OK, o.health().status());
		NewsItem i = o.items().get(0);
		assertEquals("Sensex & Nifty", i.title());
		assertEquals("Summary text", i.summary());
		assertEquals("https://s.example/a?utm_source=rss", i.url(), "original URL is kept");
		assertEquals(Validator.id("https://s.example/a"), i.id(), "id uses the canonical URL");
		assertEquals("markets", i.topics().get(0).id());
		assertEquals("feed", i.topics().get(0).by());
	}

	@Test
	void staleSourceIsExcluded() {
		Validator.Outcome o = Validator.validate(cfg(72),
				List.of(item("Old headline", "https://s.example/a", NOW.minus(Duration.ofDays(900)))), NOW, NOW);
		assertEquals(SourceHealth.STALE, o.health().status());
		assertTrue(o.items().isEmpty());
		assertTrue(o.health().error().contains("limit 72 h"));
	}

	@Test
	void tooManyInvalidItemsFailTheSource() {
		List<RawItem> raw = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			raw.add(item("Good " + i, "https://s.example/" + i, NOW));
		}
		raw.add(item("No date", "https://s.example/x", null));
		raw.add(item("Script link", "javascript:alert(1)", NOW));
		raw.add(item("", "https://s.example/y", NOW));
		Validator.Outcome o = Validator.validate(cfg(72), raw, NOW, NOW);
		assertEquals(SourceHealth.FAILED, o.health().status());
		assertTrue(o.items().isEmpty());
		assertEquals(3, o.health().rejected());
	}

	@Test
	void fewInvalidItemsAreDroppedAndNoted() {
		List<RawItem> raw = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			raw.add(item("Good " + i, "https://s.example/" + i, NOW));
		}
		raw.add(item("Future", "https://s.example/f", NOW.plus(Duration.ofHours(2))));
		Validator.Outcome o = Validator.validate(cfg(72), raw, NOW, NOW);
		assertEquals(SourceHealth.OK, o.health().status());
		assertEquals(9, o.items().size());
		assertTrue(o.health().note().contains("date in the future"));
	}

	@Test
	void emptyFeedFails() {
		assertEquals(SourceHealth.FAILED, Validator.validate(cfg(72), List.of(), NOW, NOW).health().status());
	}

	@Test
	void urlChecks() {
		assertNull(Validator.checkUrl("https://a.example/x"));
		assertEquals("link is not http(s)", Validator.checkUrl("ftp://a.example/x"));
		assertEquals("link has no host", Validator.checkUrl("https:///x"));
		assertEquals("link has user info", Validator.checkUrl("https://user:pw@a.example/"));
		assertEquals("malformed link", Validator.checkUrl("https://a.example/a b"));
	}

	@Test
	void summaryEqualToTitleIsDropped() {
		RawItem r = RawItem.of("Same", "https://s.example/a", "Same", NOW, "x");
		assertNull(Validator.validate(cfg(72), List.of(r), NOW, NOW).items().get(0).summary());
	}
}
