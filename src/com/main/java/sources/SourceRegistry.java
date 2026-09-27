package com.main.java.sources;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.java.core.HttpFetcher;
import com.main.java.core.NewsException;

/**
 * The bundled source and category configuration ({@code resources/sources.json}), validated on load. Source URLs come
 * only from this file, never from user input, so the fetchers cannot be pointed at arbitrary hosts.
 *
 * @author jgohil
 */
public final class SourceRegistry {

	static final Set<String> TYPES = Set.of("rss", "nse-rss", "newsapi-v2", "gnews", "marketaux");
	static final Set<String> KINDS = Set.of("news", "filing", "policy");
	static final Set<String> REGIONS = Set.of("in", "world");

	/** File layout. */
	@com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "_comment" })
	record Document(List<CategoryConfig> categories, List<SourceConfig> sources) {}

	private final Map<String, CategoryConfig> categories;
	private final Map<String, SourceConfig> sources;
	private final Map<String, List<String>> children;

	private SourceRegistry(Document doc) {
		Map<String, CategoryConfig> cats = new LinkedHashMap<>();
		for (CategoryConfig c : doc.categories()) {
			require(c.id() != null && c.id().matches("[a-z0-9-]+"), "category id '" + c.id() + "' is invalid");
			require(cats.put(c.id(), c) == null, "duplicate category " + c.id());
			require(c.minHealthy() >= 0, "category " + c.id() + " has negative minHealthy");
		}
		// Hierarchy: `group` names the top-level category (itself for a top-level one). A top-level category with
		// sub-categories is a parent: it has no sources and only groups its children.
		Map<String, List<String>> kids = new LinkedHashMap<>();
		for (CategoryConfig c : cats.values()) {
			require(c.group() != null && cats.containsKey(c.group()), "category " + c.id() + " has unknown group " + c.group());
			if (!c.group().equals(c.id())) {
				CategoryConfig parent = cats.get(c.group());
				require(parent.group().equals(parent.id()), "category " + c.id() + ": group " + c.group() + " is not top-level");
				kids.computeIfAbsent(c.group(), k -> new ArrayList<>()).add(c.id());
			}
		}
		Set<String> leaves = new java.util.LinkedHashSet<>(cats.keySet());
		leaves.removeAll(kids.keySet());
		Map<String, SourceConfig> srcs = new LinkedHashMap<>();
		for (SourceConfig s : doc.sources()) {
			validate(s, leaves);
			require(srcs.put(s.id(), s) == null, "duplicate source " + s.id());
		}
		this.categories = Collections.unmodifiableMap(cats);
		this.sources = Collections.unmodifiableMap(srcs);
		Map<String, List<String>> frozen = new LinkedHashMap<>();
		kids.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
		this.children = Collections.unmodifiableMap(frozen);
	}

	/** Sub-categories of {@code id} in file order (empty for a leaf). */
	public List<String> children(String id) {
		category(id);
		return children.getOrDefault(id, List.of());
	}

	public boolean isParent(String id) {
		return children.containsKey(id);
	}

	/** Replaces parent categories by their sub-categories; unknown ids throw. */
	public Set<String> expand(Set<String> ids) {
		Set<String> out = new java.util.LinkedHashSet<>();
		for (String id : ids) {
			category(id);
			if (isParent(id)) {
				out.addAll(children.get(id));
			} else {
				out.add(id);
			}
		}
		return out;
	}

	/** Leaf categories (those that hold sources), in file order. */
	public Set<String> leaves() {
		Set<String> out = new java.util.LinkedHashSet<>(categories.keySet());
		out.removeAll(children.keySet());
		return out;
	}

	/** Loads the bundled {@code /sources.json}. */
	public static SourceRegistry load() {
		try (InputStream in = SourceRegistry.class.getResourceAsStream("/sources.json")) {
			Objects.requireNonNull(in, "sources.json missing from the classpath");
			return parse(in.readAllBytes());
		} catch (IOException e) {
			throw new NewsException("Cannot read sources.json", e);
		}
	}

	public static SourceRegistry parse(byte[] json) {
		ObjectMapper m = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
		try {
			return new SourceRegistry(m.readValue(json, Document.class));
		} catch (IOException e) {
			throw new NewsException("sources.json is invalid: " + e.getMessage(), e);
		}
	}

	public Map<String, CategoryConfig> categories() {
		return categories;
	}

	public Map<String, SourceConfig> sources() {
		return sources;
	}

	public CategoryConfig category(String id) {
		CategoryConfig c = categories.get(id);
		if (c == null) {
			throw new IllegalArgumentException("Unknown category '" + id + "'. Known: " + String.join(", ", categories.keySet()));
		}
		return c;
	}

	/** Sources feeding any of {@code categoryIds}, in file order. */
	public List<SourceConfig> sourcesFor(Set<String> categoryIds) {
		List<SourceConfig> out = new ArrayList<>();
		for (SourceConfig s : sources.values()) {
			if (s.categories().stream().anyMatch(categoryIds::contains)) {
				out.add(s);
			}
		}
		return out;
	}

	/** Instantiates the fetcher for {@code config}. */
	public static NewsSource create(SourceConfig config, HttpFetcher http, Supplier<NseSymbolMaster> master) {
		return switch (config.type()) {
		case "rss" -> new RssSource(config, http);
		case "nse-rss" -> new NseAnnouncementsSource(config, http, master);
		case "newsapi-v2" -> new NewsApiV2Source(config, http);
		case "gnews" -> new GNewsSource(config, http);
		case "marketaux" -> new MarketauxSource(config, http);
		default -> throw new NewsException("Unknown source type " + config.type());
		};
	}

	private static void validate(SourceConfig s, Set<String> categoryIds) {
		String id = s.id();
		require(id != null && id.matches("[a-z0-9-]+"), "source id '" + id + "' is invalid");
		require(s.name() != null && !s.name().isBlank(), id + ": name missing");
		require(TYPES.contains(s.type()), id + ": unknown type " + s.type());
		require(KINDS.contains(s.kind()), id + ": unknown kind " + s.kind());
		require(REGIONS.contains(s.region()), id + ": unknown region " + s.region());
		require(!s.categories().isEmpty(), id + ": no categories");
		for (String c : s.categories()) {
			require(categoryIds.contains(c), id + ": unknown or parent category " + c + " (sources belong to leaf categories)");
		}
		require(s.maxAgeHours() > 0, id + ": maxAgeHours must be positive");
		require(s.url() != null, id + ": url missing");
		URI u = URI.create(s.url());
		require("https".equals(u.getScheme()) && u.getHost() != null, id + ": url must be absolute https");
		if (s.homepage() != null) {
			URI h = URI.create(s.homepage());
			require("https".equals(h.getScheme()) && h.getHost() != null, id + ": homepage must be absolute https");
		}
		boolean keyed = s.type().equals("newsapi-v2") || s.type().equals("gnews") || s.type().equals("marketaux");
		require(!keyed || (s.keyEnv() != null && !s.isPublic() && s.fallback()),
				id + ": keyed sources need keyEnv, must be fallback and must not be public (free-tier terms)");
		require(s.agent() == null || s.agent().equals("default") || s.agent().equals("browser"), id + ": bad agent");
		if (s.defaultZone() != null) {
			ZoneId.of(s.defaultZone());
		}
		Set<String> seen = new HashSet<>();
		for (String t : s.topics()) {
			require(seen.add(t), id + ": duplicate topic " + t);
		}
	}

	private static void require(boolean ok, String message) {
		if (!ok) {
			throw new NewsException("sources.json: " + message);
		}
	}
}
