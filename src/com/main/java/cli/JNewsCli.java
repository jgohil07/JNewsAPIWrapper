package com.main.java.cli;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import com.main.java.config.ConfigException;
import com.main.java.config.Env;
import com.main.java.core.HttpFetcher;
import com.main.java.core.NewsException;
import com.main.java.pipeline.Aggregator;
import com.main.java.pipeline.Classifier;
import com.main.java.pipeline.Envelope;
import com.main.java.pipeline.SourceHealth;
import com.main.java.pipeline.SymbolQuery;
import com.main.java.sources.CategoryConfig;
import com.main.java.sources.NseSymbolMaster;
import com.main.java.sources.SourceConfig;
import com.main.java.sources.SourceRegistry;
import com.main.java.site.SiteBuilder;
import com.main.java.site.SiteServer;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * {@code jnews}: command-line entry point.
 *
 * @author jgohil
 */
@Command(name = "jnews", mixinStandardHelpOptions = true, version = Envelope.GENERATOR,
		description = "Multi-source news with health checks. Fails loud: no data is written when it cannot be trusted.",
		subcommands = { JNewsCli.SourcesCmd.class, JNewsCli.FetchCmd.class, JNewsCli.IndiaCmd.class, JNewsCli.SiteCmd.class },
		footer = { "", "Exit codes: 0 ok, 1 failed (no data written), 2 usage/config error, 3 partial (data written, some sources unhealthy)." })
public final class JNewsCli implements Callable<Integer> {

	@Option(names = "--env-file", description = "Settings file (default: .env in the working directory).")
	Path envFile;

	/** Always UTF-8: under a C/POSIX locale the JVM would otherwise turn non-ASCII text into '?'. */
	PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
	PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
	Clock clock = Clock.systemUTC();
	HttpFetcher http = HttpFetcher.defaults();

	private SourceRegistry registry;
	private Supplier<NseSymbolMaster> master;

	public static void main(String[] args) {
		System.exit(run(args));
	}

	public static int run(String... args) {
		return commandLine(new JNewsCli()).execute(args);
	}

	static CommandLine commandLine(JNewsCli root) {
		CommandLine cl = new CommandLine(root);
		cl.setOut(new PrintWriter(new OutputStreamWriter(root.out, StandardCharsets.UTF_8), true));
		cl.setErr(new PrintWriter(new OutputStreamWriter(root.err, StandardCharsets.UTF_8), true));
		cl.setCaseInsensitiveEnumValuesAllowed(true);
		cl.setExecutionStrategy(pr -> {
			try {
				root.applyEnvFile();
			} catch (ConfigException e) {
				root.err.println("jnews: error: " + e.getMessage());
				return ExitCodes.USAGE;
			}
			return new CommandLine.RunLast().execute(pr);
		});
		cl.setExecutionExceptionHandler((ex, cmd, pr) -> {
			int code = (ex instanceof ConfigException || ex instanceof IllegalArgumentException) ? ExitCodes.USAGE : ExitCodes.FAILED;
			root.err.println("jnews: " + (code == ExitCodes.USAGE ? "error: " : "FAILED: ") + ex.getMessage());
			return code;
		});
		cl.setParameterExceptionHandler((ex, args) -> {
			root.err.println("jnews: " + ex.getMessage());
			ex.getCommandLine().usage(root.err);
			return ExitCodes.USAGE;
		});
		return cl;
	}

	@Override
	public Integer call() {
		CommandLine.usage(this, err);
		return ExitCodes.USAGE;
	}

	void applyEnvFile() {
		if (envFile != null) {
			if (!Files.isRegularFile(envFile)) {
				throw new ConfigException("--env-file " + envFile + " does not exist");
			}
			System.setProperty(Env.ENV_FILE_PROPERTY, envFile.toString());
			Env.reload();
		}
	}

	SourceRegistry registry() {
		if (registry == null) {
			registry = SourceRegistry.load();
		}
		return registry;
	}

	/** Loads NSE's symbol list once; returns null (and remembers why) when it cannot be loaded. */
	Supplier<NseSymbolMaster> master() {
		if (master == null) {
			master = new Supplier<>() {
				private NseSymbolMaster value;
				private boolean tried;

				@Override
				public synchronized NseSymbolMaster get() {
					if (!tried) {
						tried = true;
						try {
							value = NseSymbolMaster.load(http, NseSymbolMaster.DEFAULT_URL);
						} catch (NewsException e) {
							err.println("jnews: note: NSE symbol list unavailable (" + e.getMessage() + ")");
						}
					}
					return value;
				}
			};
		}
		return master;
	}

	Aggregator aggregator() {
		return new Aggregator(registry(), Aggregator.defaultFactory(http, master()), Classifier.load(), clock);
	}

	/** Options shared by the data commands. */
	static final class DataOptions {
		@Option(names = "--format", defaultValue = "json", description = "json (envelope), jsonl (one item per line) or table. Default: ${DEFAULT-VALUE}.")
		Output.Format format;

		@Option(names = "--since", converter = Durations.class, description = "Only items published within this window, e.g. 6h, 2d.")
		Duration since;

		@Option(names = "--topic", split = ",", description = "Only items with one of these topics (see `jnews sources --topics`).")
		List<String> topics = new ArrayList<>();

		@Option(names = "--limit", description = "At most this many items (newest first).")
		Integer limit;

		@Option(names = "--strict", description = "Treat a partial result as a failure: exit 1 and write no data.")
		boolean strict;

		@Option(names = "--allow-partial", description = "Exit 0 instead of 3 when some sources are unhealthy.")
		boolean allowPartial;

		@Option(names = "--timeout", converter = Durations.class, defaultValue = "1m", description = "Per-source time limit. Default: 1m.")
		Duration timeout;

		@Option(names = { "-q", "--quiet" }, description = "Only the one-line summary on stderr.")
		boolean quiet;

		void check() {
			if (strict && allowPartial) {
				throw new IllegalArgumentException("--strict and --allow-partial cannot be combined");
			}
			if (limit != null && limit < 1) {
				throw new IllegalArgumentException("--limit must be at least 1");
			}
		}
	}

	static int emit(JNewsCli root, Aggregator.Result r, Map<String, Object> query, DataOptions o) {
		Aggregator.Result limited = o.limit == null || r.items().size() <= o.limit ? r
				: new Aggregator.Result(r.generatedAt(), r.status(), r.items().subList(0, o.limit), r.sources(), r.categories());
		return Output.emit(Envelope.of(limited, query), r.status(), o.format, o.strict, o.allowPartial, o.quiet, root.out, root.err);
	}

	static Map<String, Object> query(String command, Set<String> categories, DataOptions o) {
		Map<String, Object> q = new LinkedHashMap<>();
		q.put("command", command);
		q.put("categories", List.copyOf(categories));
		q.put("topics", List.copyOf(o.topics));
		q.put("since_hours", o.since == null ? null : o.since.toMinutes() / 60.0);
		q.put("limit", o.limit);
		return q;
	}

	@Command(name = "sources", description = "List configured sources and categories; --check fetches and validates every source.")
	static final class SourcesCmd implements Callable<Integer> {
		@ParentCommand
		JNewsCli root;

		@Option(names = "--check", description = "Fetch and validate every source now.")
		boolean check;

		@Option(names = "--topics", description = "List topic ids instead.")
		boolean topics;

		@Override
		public Integer call() {
			SourceRegistry reg = root.registry();
			if (topics) {
				Classifier.load().labels().forEach((id, label) -> root.out.printf("%-14s %s%n", id, label));
				return ExitCodes.OK;
			}
			if (!check) {
				for (CategoryConfig c : reg.categories().values()) {
					boolean sub = !c.group().equals(c.id());
					String indent = sub ? "    " : "";
					if (reg.isParent(c.id())) {
						root.out.printf("%n[%s] %s  (parent of %s)%n", c.id(), c.label(), String.join(", ", reg.children(c.id())));
						continue;
					}
					root.out.printf("%n%s[%s] %s  (needs %d healthy%s)%n", indent, c.id(), c.label(), c.minHealthy(),
							c.required() ? ", required" : "");
					for (SourceConfig s : reg.sourcesFor(Set.of(c.id()))) {
						root.out.printf("%s  %-26s %-9s %-7s %s%s%n", indent, s.id(), s.type(), s.isPublic() ? "public" : "cli-only",
								s.name(), s.fallback() ? "  [fallback" + (s.keyEnv() != null ? ", needs " + s.keyEnv() : "") + "]" : "");
					}
				}
				return ExitCodes.OK;
			}
			Aggregator agg = root.aggregator();
			var outcomes = agg.fetchAll(List.copyOf(reg.sources().values()), Duration.ofMinutes(1));
			int bad = 0;
			root.out.printf("%-26s %-8s %5s %4s %8s %6s  %s%n", "SOURCE", "STATUS", "ITEMS", "REJ", "NEWEST", "MS", "DETAIL");
			for (var o : outcomes.values()) {
				SourceHealth h = o.health();
				if (!h.isOk() && !SourceHealth.SKIPPED.equals(h.status())) {
					bad++;
				}
				String newest = h.newestAt() == null ? "-" : Output.age(Duration.between(h.newestAt(), root.clock.instant()));
				String detail = h.error() != null ? h.error() : h.note() != null ? h.note() : "";
				root.out.printf("%-26s %-8s %5d %4d %8s %6d  %s%n", h.id(), h.status(), h.items(), h.rejected(), newest,
						h.durationMs(), detail);
			}
			root.err.printf("jnews: %d of %d source(s) not healthy%n", bad, outcomes.size());
			return bad == 0 ? ExitCodes.OK : ExitCodes.PARTIAL;
		}
	}

	@Command(name = "fetch", description = "Headlines for any categories (default: all).")
	static final class FetchCmd implements Callable<Integer> {
		@ParentCommand
		JNewsCli root;

		@Mixin
		DataOptions o;

		@Option(names = "--category", split = ",", description = "Category ids (see `jnews sources`).")
		List<String> categories = new ArrayList<>();

		@Option(names = "--public-only", description = "Only sources allowed on the public site (what the site shows).")
		boolean publicOnly;

		@Option(names = "--no-fallback", description = "Never query fallback sources.")
		boolean noFallback;

		@Override
		public Integer call() {
			o.check();
			SourceRegistry reg = root.registry();
			Set<String> cats = categories.isEmpty() ? reg.categories().keySet() : new LinkedHashSet<>(categories);
			cats.forEach(reg::category);
			Aggregator.Result r = root.aggregator().run(new Aggregator.Options(cats, o.since, publicOnly, !noFallback,
					Set.copyOf(o.topics), o.timeout));
			return emit(root, r, query("fetch", cats, o), o);
		}
	}

	@Command(name = "india", description = { "India finance feed for ingestion (e.g. Thesis-Engine): markets, companies, economy & policy, NSE filings.",
			"With --symbol: NSE announcements for the symbol (exact) plus headlines naming the company." })
	static final class IndiaCmd implements Callable<Integer> {
		static final Set<String> CATEGORIES = Set.of("india-markets", "india-business", "economy", "filings");

		@ParentCommand
		JNewsCli root;

		@Mixin
		DataOptions o;

		@Option(names = "--symbol", description = "NSE symbol, e.g. MBAPL. Must be on NSE's equity list.")
		String symbol;

		@Option(names = "--no-fallback", description = "Never query fallback sources.")
		boolean noFallback;

		@Override
		public Integer call() {
			o.check();
			Duration since = o.since != null ? o.since : Duration.ofDays(2);
			o.since = since;
			if (symbol == null) {
				Aggregator.Result r = root.aggregator().run(new Aggregator.Options(CATEGORIES, since, false, !noFallback,
						Set.copyOf(o.topics), o.timeout));
				return emit(root, r, query("india", CATEGORIES, o), o);
			}
			String sym = symbol.strip().toUpperCase(Locale.ROOT);
			if (!SymbolQuery.SYMBOL.matcher(sym).matches()) {
				throw new IllegalArgumentException("--symbol '" + symbol + "' is not a valid NSE symbol");
			}
			if (!o.topics.isEmpty()) {
				throw new IllegalArgumentException("--topic cannot be combined with --symbol");
			}
			NseSymbolMaster m = root.master().get();
			if (m == null) {
				root.err.println("jnews: FAILED - cannot verify the symbol without NSE's equity list");
				return ExitCodes.FAILED;
			}
			NseSymbolMaster.Company company = m.company(sym)
					.orElseThrow(() -> new IllegalArgumentException("'" + sym + "' is not on NSE's equity list"));
			Aggregator.Result r = new SymbolQuery(root.registry(), root.aggregator(), root.http, root.clock)
					.run(company, since, o.timeout);
			Map<String, Object> q = query("india", Set.of("filings"), o);
			q.put("symbol", company.symbol());
			q.put("company", company.name());
			q.put("isin", company.isin());
			return emit(root, r, q, o);
		}
	}

	@Command(name = "site", description = "Static site: build it, or serve a built one locally.",
			subcommands = { SiteCmd.Build.class, SiteCmd.Serve.class })
	static final class SiteCmd implements Callable<Integer> {
		@ParentCommand
		JNewsCli root;

		@Override
		public Integer call() {
			CommandLine.usage(this, root.err);
			return ExitCodes.USAGE;
		}

		@Command(name = "build", description = "Fetch the public sources and write the site (HTML + data/*.json). Fails when a required category is below its minimum.")
		static final class Build implements Callable<Integer> {
			@ParentCommand
			SiteCmd site;

			@Option(names = "--out", required = true, description = "Output directory (created; existing files are replaced).")
			Path out;

			@Option(names = "--since", converter = Durations.class, defaultValue = "3d", description = "Window of items to publish. Default: 3d.")
			Duration since;

			@Option(names = "--timeout", converter = Durations.class, defaultValue = "1m", description = "Per-source time limit. Default: 1m.")
			Duration timeout;

			@Option(names = "--refresh-hours", defaultValue = "6", description = "Planned refresh interval shown on the page. Default: 6.")
			int refreshHours;

			@Override
			public Integer call() throws Exception {
				JNewsCli root = site.root;
				SourceRegistry reg = root.registry();
				Aggregator.Result r = root.aggregator().run(new Aggregator.Options(reg.categories().keySet(), since, true,
						false, Set.of(), timeout));
				Envelope env = Envelope.of(r, Map.of("command", "site", "since_hours", since.toHours(), "public_only", true));
				Output.summarise(env, root.err, false);
				if (r.status() == Aggregator.Status.FAILED) {
					root.err.println("jnews: FAILED - site not written; the previous deployment stays live");
					return ExitCodes.FAILED;
				}
				new SiteBuilder(reg, Classifier.load()).build(env, out, refreshHours);
				root.err.println("jnews: site written to " + out.toAbsolutePath().normalize());
				return r.status() == Aggregator.Status.OK ? ExitCodes.OK : ExitCodes.PARTIAL;
			}
		}

		@Command(name = "serve", description = "Serve a built site on http://127.0.0.1:PORT (local preview only).")
		static final class Serve implements Callable<Integer> {
			@ParentCommand
			SiteCmd site;

			@Option(names = "--dir", required = true, description = "Directory written by `site build`.")
			Path dir;

			@Option(names = "--port", defaultValue = "8080", description = "Port. Default: 8080.")
			int port;

			@Override
			public Integer call() throws Exception {
				if (port < 1024 || port > 65535) {
					throw new IllegalArgumentException("--port must be 1024-65535");
				}
				SiteServer.serve(dir, port, site.root.err);
				return ExitCodes.OK;
			}
		}
	}
}
