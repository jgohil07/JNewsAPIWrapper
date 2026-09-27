package com.main.java.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.main.java.core.NewsItem;
import com.main.java.core.NewsItem.SourceRef;
import com.main.java.core.NewsItem.Topic;

class DeduperTest {

	static final Instant T = Instant.parse("2026-09-27T08:00:00Z");

	static NewsItem item(String source, String title, String url, Instant at, String category) {
		return new NewsItem(Validator.id(url), title, url, new SourceRef(source, source), at, at, null, "news", "in", "en",
				List.of(category), List.of(new Topic(category, "feed")), List.of(), List.of());
	}

	@Test
	void canonicalFormIgnoresTrackingWwwFragmentAndSlash() {
		assertEquals("https://bbc.co.uk/news/x?id=1",
				Deduper.canonical("http://www.BBC.co.uk/news/x/?utm_source=rss&at_medium=RSS&id=1#top"));
	}

	@Test
	void sameArticleInTwoSectionFeedsMergesWithoutCoverage() {
		List<NewsItem> out = Deduper.merge(List.of(
				item("et-stocks", "Stocks to watch this week in the market", "https://et.example/a?utm_source=x", T, "india-markets"),
				item("et-markets", "Stocks to watch this week in the market", "https://et.example/a", T, "economy")),
				Map.of("et-markets", 10, "et-stocks", 11));
		assertEquals(1, out.size());
		assertEquals("et-markets", out.get(0).source().id());
		assertTrue(out.get(0).alsoCoveredBy().isEmpty());
		assertEquals(List.of("economy", "india-markets"), out.get(0).categories());
	}

	@Test
	void sameHeadlineFromAnotherPublisherIsCoverage() {
		List<NewsItem> out = Deduper.merge(List.of(
				item("mint", "RBI keeps repo rate unchanged at 5.5 per cent", "https://mint.example/rbi", T.plusSeconds(600), "economy"),
				item("et", "RBI keeps repo rate unchanged at 5.5 per cent!", "https://et.example/rbi", T, "economy")),
				Map.of("et", 10, "mint", 12));
		assertEquals(1, out.size());
		assertEquals("et", out.get(0).source().id());
		assertEquals("mint", out.get(0).alsoCoveredBy().get(0).sourceId());
	}

	@Test
	void shortOrDistantHeadlinesAreNotMerged() {
		assertEquals(2, Deduper.merge(List.of(
				item("a", "Live updates", "https://a.example/1", T, "world"),
				item("b", "Live updates", "https://b.example/1", T, "world")), Map.of()).size());
		assertEquals(2, Deduper.merge(List.of(
				item("a", "Monsoon arrives in Kerala ahead of schedule", "https://a.example/1", T, "india"),
				item("b", "Monsoon arrives in Kerala ahead of schedule", "https://b.example/1", T.plus(Duration.ofDays(400)), "india")),
				Map.of()).size());
	}
}
