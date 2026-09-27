package com.main.java.site;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import com.main.java.core.Json;
import com.main.java.core.NewsException;
import com.main.java.core.NewsItem;
import com.main.java.pipeline.Classifier;
import com.main.java.pipeline.Envelope;
import com.main.java.sources.CategoryConfig;
import com.main.java.sources.SourceRegistry;

/**
 * Writes the static site: the UI files bundled under {@code /site} plus
 * <ul>
 * <li>{@code data/latest.json} - the full envelope (every public item, category and source health);</li>
 * <li>{@code data/india.json} - the India finance subset, same schema;</li>
 * <li>{@code data/site.json} - labels, groups, topics and refresh timing for the UI.</li>
 * </ul>
 * The output directory must be new, empty, or a previous build (marker file), so a wrong {@code --out} can never
 * overwrite unrelated files.
 *
 * @author jgohil
 */
public final class SiteBuilder {

	static final String MARKER = ".jnews-site";
	static final List<String> ASSETS = List.of("index.html", "app.css", "app.js", "favicon.svg");
	static final Set<String> INDIA_FINANCE = Set.of("india-markets", "india-business", "economy", "filings");

	private final SourceRegistry registry;
	private final Classifier classifier;

	public SiteBuilder(SourceRegistry registry, Classifier classifier) {
		this.registry = registry;
		this.classifier = classifier;
	}

	public void build(Envelope env, Path out, int refreshHours) throws IOException {
		prepare(out);
		Path data = Files.createDirectories(out.resolve("data"));
		for (String asset : ASSETS) {
			try (InputStream in = SiteBuilder.class.getResourceAsStream("/site/" + asset)) {
				Objects.requireNonNull(in, "site asset missing from the jar: " + asset);
				Files.copy(in, out.resolve(asset), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		write(data.resolve("latest.json"), env);
		List<NewsItem> india = env.items().stream().filter(i -> i.categories().stream().anyMatch(INDIA_FINANCE::contains)).toList();
		write(data.resolve("india.json"), new Envelope(env.schemaVersion(), env.generator(), env.generatedAt(),
				Map.of("command", "site", "categories", List.copyOf(INDIA_FINANCE)), env.status(),
				env.categories().stream().filter(c -> INDIA_FINANCE.contains(c.id())).toList(),
				env.sources().stream().filter(s -> s.categories().stream().anyMatch(INDIA_FINANCE::contains)).toList(), india));
		write(data.resolve("site.json"), siteMeta(env, refreshHours));
		Files.writeString(out.resolve(".nojekyll"), "");
		Files.writeString(out.resolve(MARKER), "Built by " + Envelope.GENERATOR + "\n");
	}

	private Map<String, Object> siteMeta(Envelope env, int refreshHours) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("schema_version", Envelope.SCHEMA_VERSION);
		m.put("generated_at", env.generatedAt());
		m.put("refresh_hours", refreshHours);
		m.put("next_refresh_at", env.generatedAt().plus(Duration.ofHours(refreshHours)));
		m.put("stale_after_hours", refreshHours + 2);
		m.put("very_stale_after_hours", 24);
		List<Map<String, Object>> cats = new ArrayList<>();
		for (CategoryConfig c : registry.categories().values()) {
			cats.add(Map.of("id", c.id(), "label", c.label(), "group", c.group()));
		}
		m.put("categories", cats);
		m.put("topics", classifier.labels());
		return m;
	}

	private static void write(Path file, Object value) throws IOException {
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Json.OUTPUT.writeValue(tmp.toFile(), value);
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static void prepare(Path out) throws IOException {
		if (Files.exists(out)) {
			if (!Files.isDirectory(out)) {
				throw new NewsException("--out " + out + " is not a directory");
			}
			boolean empty;
			try (Stream<Path> s = Files.list(out)) {
				empty = s.findAny().isEmpty();
			}
			if (!empty && !Files.exists(out.resolve(MARKER))) {
				throw new NewsException("--out " + out + " is not empty and was not written by jnews; refusing to overwrite it");
			}
		} else {
			Files.createDirectories(out);
		}
	}
}
