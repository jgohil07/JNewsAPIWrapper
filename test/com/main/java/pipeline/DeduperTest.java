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
	void sameSourceRecurringHeadlinesStaySeparate() {
		// Review finding 1: two NSE-style "Company: Updates" items from one source must never collapse.
		List<NewsItem> out = Deduper.merge(List.of(
				item("mint", "Reliance Industries Limited quarterly update for investors", "https://m.example/1", T, "economy"),
				item("mint", "Reliance Industries Limited quarterly update for investors", "https://m.example/2", T.plusSeconds(3600), "economy")),
				Map.of());
		assertEquals(2, out.size());
	}

	@Test
	void filingsAreNeverMergedOnTitle() {
		NewsItem a = filing("Reliance Industries Limited: Credit Rating", "https://nse.example/a.pdf", T);
		NewsItem b = new NewsItem(Validator.id("https://nse.example/b.pdf"), a.title(), "https://nse.example/b.pdf",
				new SourceRef("other-exchange-feed", "x"), T.plusSeconds(60), T, null, "filing", "in", "en",
				List.of("filings"), List.of(), List.of(), List.of());
		assertEquals(2, Deduper.merge(List.of(a, b), Map.of()).size());
	}

	@Test
	void titleGroupsDoNotChain() {
		List<NewsItem> out = Deduper.merge(List.of(
				item("a", "Monsoon arrives in Kerala ahead of schedule", "https://a.example/1", T, "india"),
				item("b", "Monsoon arrives in Kerala ahead of schedule", "https://b.example/1", T.plus(Duration.ofHours(30)), "india"),
				item("c", "Monsoon arrives in Kerala ahead of schedule", "https://c.example/1", T.plus(Duration.ofHours(60)), "india")),
				Map.of());
		assertEquals(2, out.size(), "c is 60 h from the anchor, so it starts its own group");
	}

	static NewsItem filing(String title, String url, Instant at) {
		return new NewsItem(Validator.id(url), title, url, new SourceRef("nse-symbol", "NSE"), at, at, null, "filing",
				"in", "en", List.of("filings"), List.of(), List.of(), List.of());
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
