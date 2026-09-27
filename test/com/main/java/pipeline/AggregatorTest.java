package com.main.java.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.main.java.core.NewsException;
import com.main.java.core.NewsItem;
import com.main.java.sources.NewsSource;
import com.main.java.sources.RawItem;
import com.main.java.sources.SourceConfig;
import com.main.java.sources.SourceRegistry;

class AggregatorTest {

	static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");

	static final String REGISTRY = """
			{"categories":[
			  {"id":"mk","label":"Markets","group":"india-finance","minHealthy":2,"required":true},
			  {"id":"fl","label":"Filings","group":"india-finance","minHealthy":1,"required":false}],
			 "sources":[
			  %s,
			  %s,
			  %s,
			  %s,
			  %s]}""".formatted(
			src("a", "mk", false, true, 10), src("b", "mk", false, true, 12), src("c", "mk", false, true, 14),
			src("n", "fl", false, true, 5), src("fb", "mk", true, false, 50));

	static String src(String id, String cat, boolean fallback, boolean isPublic, int prio) {
		return """
				{"id":"%s","name":"%s","type":"rss","url":"https://%s.example/feed","homepage":null,"region":"in","kind":"news",
				 "language":"en","categories":["%s"],"topics":[],"maxAgeHours":48,"public":%s,"agent":"default",
				 "defaultZone":null,"datePattern":null,"fallback":%s,"priority":%d,"keyEnv":null}"""
				.formatted(id, id.toUpperCase(), id, cat, isPublic, fallback, prio);
	}

	/** Scripted source: returns items, throws, sleeps or reports itself unavailable. */
	static final class Stub implements NewsSource {
		final SourceConfig cfg;
		final Supplier<List<RawItem>> body;
		final String unavailable;

		Stub(SourceConfig cfg, Supplier<List<RawItem>> body, String unavailable) {
			this.cfg = cfg;
			this.body = body;
			this.unavailable = unavailable;
		}

		@Override
		public SourceConfig config() {
			return cfg;
		}

		@Override
		public List<RawItem> fetch() {
			return body.get();
		}

		@Override
		public String unavailableReason() {
			return unavailable;
		}
	}

	private final SourceRegistry registry = SourceRegistry.parse(REGISTRY.getBytes(StandardCharsets.UTF_8));
	private final Map<String, Supplier<List<RawItem>>> behaviour = new HashMap<>();
	private final Map<String, String> unavailable = new HashMap<>();
	private final List<String> fetched = new java.util.concurrent.CopyOnWriteArrayList<>();

	private Aggregator aggregator() {
		return new Aggregator(registry, cfg -> new Stub(cfg, () -> {
			fetched.add(cfg.id());
			return behaviour.getOrDefault(cfg.id(), () -> items(cfg.id(), 2)).get();
		}, unavailable.get(cfg.id())), Classifier.of(List.of()), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	static List<RawItem> items(String id, int n) {
		java.util.ArrayList<RawItem> out = new java.util.ArrayList<>();
		for (int i = 0; i < n; i++) {
			Instant t = NOW.minus(Duration.ofHours(i + 1));
			out.add(RawItem.of("Headline number " + i + " from source " + id, "https://" + id + ".example/" + i, null, t, t.toString()));
		}
		return out;
	}

	static Aggregator.Options opts(boolean publicOnly, boolean fallback, String... cats) {
		return new Aggregator.Options(Set.of(cats), null, publicOnly, fallback, Set.of(), Duration.ofSeconds(5));
	}

	@Test
	void allHealthyIsOkAndSortedNewestFirst() {
		Aggregator.Result r = aggregator().run(opts(false, true, "mk", "fl"));
		assertEquals(Aggregator.Status.OK, r.status());
		assertEquals(8, r.items().size());
		for (int i = 1; i < r.items().size(); i++) {
			assertFalse(r.items().get(i).publishedAt().isAfter(r.items().get(i - 1).publishedAt()));
		}
		assertFalse(fetched.contains("fb"), "fallback not needed");
	}

	@Test
	void oneFailureIsPartialAndReported() {
		behaviour.put("b", () -> {
			throw new NewsException("HTTP 403 from https://b.example/feed");
		});
		Aggregator.Result r = aggregator().run(opts(true, false, "mk"));
		assertEquals(Aggregator.Status.PARTIAL, r.status());
		SourceHealth b = r.sources().stream().filter(h -> h.id().equals("b")).findFirst().orElseThrow();
		assertEquals(SourceHealth.FAILED, b.status());
		assertTrue(b.error().contains("403"));
		assertTrue(r.items().stream().noneMatch(i -> i.source().id().equals("b")));
	}

	@Test
	void requiredCategoryBelowMinimumFails() {
		behaviour.put("a", () -> List.of());
		behaviour.put("b", () -> items("b", 1).stream()
				.map(x -> RawItem.of(x.title(), x.url(), null, NOW.minus(Duration.ofDays(30)), "old")).toList());
		Aggregator.Result r = aggregator().run(opts(true, false, "mk"));
		assertEquals(Aggregator.Status.FAILED, r.status());
		assertFalse(r.categories().get(0).ok());
		assertEquals(1, r.categories().get(0).healthy());
	}

	@Test
	void fallbackFillsTheGapForTheCliButNeverForTheSite() {
		behaviour.put("a", () -> {
			throw new NewsException("down");
		});
		behaviour.put("b", () -> {
			throw new NewsException("down");
		});
		Aggregator.Result cli = aggregator().run(opts(false, true, "mk"));
		assertTrue(fetched.contains("fb"));
		assertEquals(Aggregator.Status.PARTIAL, cli.status(), "c + fallback meet the minimum of 2");
		assertTrue(cli.items().stream().anyMatch(i -> i.source().id().equals("fb")));

		fetched.clear();
		Aggregator.Result site = aggregator().run(opts(true, true, "mk"));
		assertFalse(fetched.contains("fb"));
		assertEquals(Aggregator.Status.FAILED, site.status());
	}

	@Test
	void unavailableSourcesAreSkippedNotFailed() {
		unavailable.put("n", "KEY not set");
		Aggregator.Result r = aggregator().run(opts(false, false, "fl", "mk"));
		assertEquals(SourceHealth.SKIPPED, r.sources().stream().filter(h -> h.id().equals("n")).findFirst().orElseThrow().status());
		assertEquals(Aggregator.Status.PARTIAL, r.status(), "filings (not required) has no healthy source");
	}

	@Test
	void slowSourceTimesOut() {
		behaviour.put("c", () -> {
			try {
				Thread.sleep(10_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return List.of();
		});
		Aggregator.Result r = aggregator().run(new Aggregator.Options(Set.of("mk"), null, true, false, Set.of(), Duration.ofMillis(500)));
		SourceHealth c = r.sources().stream().filter(h -> h.id().equals("c")).findFirst().orElseThrow();
		assertEquals(SourceHealth.FAILED, c.status());
		assertTrue(c.error().contains("timed out"));
	}

	@Test
	void windowKeepsAStoryWhenAnyCopyIsInside() {
		// Review finding 3: preferred source published 30 h ago, another 2 h ago; --since 24h must keep the story.
		String title = "Sensex closes at a record high on strong FII inflows";
		behaviour.put("a", () -> List.of(RawItem.of(title, "https://a.example/x", null, NOW.minus(Duration.ofHours(30)), "t")));
		behaviour.put("b", () -> List.of(RawItem.of(title, "https://b.example/x", null, NOW.minus(Duration.ofHours(2)), "t")));
		Aggregator.Result r = aggregator().run(new Aggregator.Options(Set.of("mk"), Duration.ofHours(24), true, false, Set.of(), null));
		assertTrue(r.items().stream().anyMatch(i -> i.title().equals(title)), "story must survive the window");
	}

	@Test
	void sinceWindowFiltersItems() {
		Aggregator.Result r = aggregator().run(new Aggregator.Options(Set.of("mk"), Duration.ofMinutes(90), true, false, Set.of(), null));
		assertEquals(3, r.items().size());
		for (NewsItem i : r.items()) {
			assertTrue(i.publishedAt().isAfter(NOW.minus(Duration.ofMinutes(90))));
		}
	}
}
