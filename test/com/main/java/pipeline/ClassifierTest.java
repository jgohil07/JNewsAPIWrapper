package com.main.java.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.main.java.core.NewsItem;
import com.main.java.core.NewsItem.SourceRef;
import com.main.java.core.NewsItem.Topic;

class ClassifierTest {

	private final Classifier classifier = Classifier.load();

	static NewsItem item(String title, String category, List<Topic> topics) {
		return new NewsItem("id", title, "https://x.example/", new SourceRef("s", "S"), Instant.EPOCH, Instant.EPOCH, null,
				"news", "in", "en", List.of(category), topics, List.of(), List.of());
	}

	static boolean has(NewsItem i, String topic, String by) {
		return i.topics().stream().anyMatch(t -> t.id().equals(topic) && t.by().equals(by));
	}

	@Test
	void ruleTopicsAreMarkedAsRules() {
		NewsItem i = classifier.classify(item("Acme files DRHP for Rs 500 crore IPO; RBI nod pending", "india-markets", List.of()));
		assertTrue(has(i, "ipo", "rule"));
		assertTrue(has(i, "rbi", "rule"));
	}

	@Test
	void feedTopicWins() {
		NewsItem i = classifier.classify(item("IPO market cools", "india-markets", List.of(new Topic("ipo", "feed"))));
		assertEquals(1, i.topics().stream().filter(t -> t.id().equals("ipo")).count());
		assertTrue(has(i, "ipo", "feed"));
	}

	@Test
	void acronymsAreCaseSensitive() {
		assertFalse(has(classifier.classify(item("A pat on the back for the team", "india-business", List.of())), "earnings", "rule"));
		assertTrue(has(classifier.classify(item("Acme PAT rises 12%", "india-business", List.of())), "earnings", "rule"));
	}

	@Test
	void rulesOnlyApplyToTheirCategories() {
		assertFalse(has(classifier.classify(item("NASA mission reports record IPO of data", "science", List.of())), "ipo", "rule"));
	}
}
