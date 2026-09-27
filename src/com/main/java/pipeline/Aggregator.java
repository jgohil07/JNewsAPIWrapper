package com.main.java.pipeline;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

import com.main.java.core.NewsItem;
import com.main.java.sources.CategoryConfig;
import com.main.java.sources.NewsSource;
import com.main.java.sources.SourceConfig;
import com.main.java.sources.SourceRegistry;

/**
 * Fetches sources in parallel, validates each one, falls back to extra sources for categories with too few healthy
 * ones, merges duplicates, classifies, filters and sorts. The result always says which sources and categories were
 * healthy; callers decide how loudly to fail from {@link Result#status()}.
 *
 * @author jgohil
 */
public final class Aggregator {

	/** How a run went overall. */
	public enum Status {
		/** Every queried source is ok (skipped fallbacks do not count). */
		OK,
		/** Some sources failed or are stale, but every required category has enough healthy sources. */
		PARTIAL,
		/** A required category is below its minimum of healthy sources. */
		FAILED
	}

	/**
	 * @param categories  categories to build
	 * @param since       keep items published within this window, or null for all
	 * @param publicOnly  use only sources marked public (the site build)
	 * @param useFallback query fallback sources for categories below their minimum (never with publicOnly)
	 * @param topics      keep only items with one of these topics, or empty for all
	 * @param deadline    per-source time limit, retries included
	 */
	public record Options(Set<String> categories, Duration since, boolean publicOnly, boolean useFallback,
			Set<String> topics, Duration deadline) {

		public Options {
			categories = Set.copyOf(categories);
			topics = topics == null ? Set.of() : Set.copyOf(topics);
			deadline = deadline == null ? Duration.ofSeconds(60) : deadline;
		}
	}

	/** Health of one category. */
	public record CategoryStatus(String id, String label, String group, int healthy, int minHealthy, boolean required,
			boolean ok) {}

	public record Result(Instant generatedAt, Status status, List<NewsItem> items, List<SourceHealth> sources,
			List<CategoryStatus> categories) {}

	private final SourceRegistry registry;
	private final Function<SourceConfig, NewsSource> factory;
	private final Classifier classifier;
	private final Clock clock;

	/**
	 * @param factory builds the fetcher for a source; normally {@code SourceRegistry.create(cfg, http, master)}
	 */
	public Aggregator(SourceRegistry registry, Function<SourceConfig, NewsSource> factory, Classifier classifier,
			Clock clock) {
		this.registry = registry;
		this.factory = factory;
		this.classifier = classifier;
		this.clock = clock;
	}

	public Result run(Options options) {
		for (String c : options.categories()) {
			registry.category(c);
		}
		List<SourceConfig> candidates = registry.sourcesFor(options.categories()).stream()
				.filter(s -> !options.publicOnly() || s.isPublic())
				.toList();
		List<SourceConfig> primaries = candidates.stream().filter(s -> !s.fallback()).toList();
		Map<String, Validator.Outcome> outcomes = new LinkedHashMap<>(fetchAll(primaries, options.deadline()));

		if (options.useFallback() && !options.publicOnly()) {
			Set<SourceConfig> extra = new LinkedHashSet<>();
			for (String c : options.categories()) {
				if (healthy(outcomes, c) < registry.category(c).minHealthy()) {
					candidates.stream().filter(SourceConfig::fallback).filter(s -> s.categories().contains(c)).forEach(extra::add);
				}
			}
			outcomes.putAll(fetchAll(new ArrayList<>(extra), options.deadline()));
		}

		List<CategoryStatus> categories = new ArrayList<>();
		boolean requiredMissing = false;
		boolean anyMissing = false;
		for (String c : orderedCategories(options.categories())) {
			CategoryConfig cc = registry.category(c);
			int healthy = healthy(outcomes, c);
			boolean ok = healthy >= cc.minHealthy();
			categories.add(new CategoryStatus(cc.id(), cc.label(), cc.group(), healthy, cc.minHealthy(), cc.required(), ok));
			if (!ok) {
				anyMissing = true;
				requiredMissing |= cc.required();
			}
		}
		List<SourceHealth> health = outcomes.values().stream().map(Validator.Outcome::health).toList();
		boolean anyBad = health.stream().anyMatch(h -> !h.isOk() && !SourceHealth.SKIPPED.equals(h.status()));
		Status status = requiredMissing ? Status.FAILED : (anyBad || anyMissing) ? Status.PARTIAL : Status.OK;

		Instant now = clock.instant();
		Instant cutoff = options.since() == null ? null : now.minus(options.since());
		// The window applies to every copy before merging, so a story stays when any source published it inside it.
		List<NewsItem> all = new ArrayList<>();
		outcomes.values().forEach(o -> o.items().stream()
				.filter(i -> cutoff == null || !i.publishedAt().isBefore(cutoff))
				.forEach(all::add));
		Map<String, Integer> priority = new HashMap<>();
		registry.sources().values().forEach(s -> priority.put(s.id(), s.priority()));
		List<NewsItem> items = Deduper.merge(all, priority).stream()
				.map(classifier::classify)
				.filter(i -> options.topics().isEmpty() || i.topics().stream().anyMatch(t -> options.topics().contains(t.id())))
				.sorted(Comparator.comparing(NewsItem::publishedAt).reversed()
						.thenComparingInt(i -> priority.getOrDefault(i.source().id(), Integer.MAX_VALUE))
						.thenComparing(NewsItem::id))
				.toList();
		return new Result(now, status, items, health, categories);
	}

	/** Runs arbitrary sources (e.g. a per-symbol query) with the same validation and deadline rules. */
	public Map<String, Validator.Outcome> fetchAll(List<SourceConfig> configs, Duration deadline) {
		return fetchSources(configs.stream().map(factory).toList(), deadline);
	}

	public Map<String, Validator.Outcome> fetchSources(List<NewsSource> sources, Duration deadline) {
		Map<String, Validator.Outcome> out = new LinkedHashMap<>();
		if (sources.isEmpty()) {
			return out;
		}
		Map<NewsSource, Future<Validator.Outcome>> futures = new LinkedHashMap<>();
		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			for (NewsSource s : sources) {
				futures.put(s, pool.submit(() -> fetchOne(s)));
			}
			long end = System.nanoTime() + deadline.toNanos();
			for (Map.Entry<NewsSource, Future<Validator.Outcome>> e : futures.entrySet()) {
				SourceConfig cfg = e.getKey().config();
				Validator.Outcome o;
				try {
					o = e.getValue().get(Math.max(0, end - System.nanoTime()), TimeUnit.NANOSECONDS);
				} catch (TimeoutException ex) {
					e.getValue().cancel(true);
					o = new Validator.Outcome(List.of(), Validator.failed(cfg, SourceHealth.FAILED,
							"timed out after " + deadline.toSeconds() + " s"));
				} catch (ExecutionException ex) {
					Throwable c = ex.getCause() == null ? ex : ex.getCause();
					o = new Validator.Outcome(List.of(), Validator.failed(cfg, SourceHealth.FAILED, describe(c)));
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted while fetching sources", ex);
				}
				out.put(cfg.id(), o);
			}
			pool.shutdownNow();
		}
		return out;
	}

	private Validator.Outcome fetchOne(NewsSource s) {
		SourceConfig cfg = s.config();
		String unavailable = s.unavailableReason();
		if (unavailable != null) {
			return new Validator.Outcome(List.of(), Validator.failed(cfg, SourceHealth.SKIPPED, unavailable));
		}
		long t0 = System.nanoTime();
		Instant fetchedAt = clock.instant();
		try {
			Validator.Outcome o = Validator.validate(cfg, s.fetch(), fetchedAt, clock.instant(), s.emptyIsHealthy());
			SourceHealth h = o.health().withDuration(elapsedMs(t0));
			if (s.note() != null) {
				h = h.withNote(h.note() == null ? s.note() : h.note() + "; " + s.note());
			}
			return new Validator.Outcome(o.items(), h);
		} catch (RuntimeException e) {
			return new Validator.Outcome(List.of(),
					Validator.failed(cfg, SourceHealth.FAILED, describe(e)).withDuration(elapsedMs(t0)));
		}
	}

	private static long elapsedMs(long t0) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
	}

	static String describe(Throwable t) {
		String m = t.getMessage();
		String s = m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
		return s.length() > 300 ? s.substring(0, 300) + "…" : s;
	}

	private static int healthy(Map<String, Validator.Outcome> outcomes, String category) {
		int n = 0;
		for (Validator.Outcome o : outcomes.values()) {
			if (o.health().isOk() && o.health().categories().contains(category)) {
				n++;
			}
		}
		return n;
	}

	private List<String> orderedCategories(Set<String> wanted) {
		return registry.categories().keySet().stream().filter(wanted::contains).toList();
	}

	/** Convenience for the CLI and the site: a factory over the registry with a lazily loaded NSE symbol list. */
	public static Function<SourceConfig, NewsSource> defaultFactory(com.main.java.core.HttpFetcher http,
			Supplier<com.main.java.sources.NseSymbolMaster> master) {
		return cfg -> SourceRegistry.create(cfg, http, master);
	}
}
