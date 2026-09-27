package com.main.java.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.main.java.core.Json;
import com.main.java.core.NewsItem;
import com.main.java.pipeline.Aggregator;
import com.main.java.pipeline.Envelope;
import com.main.java.pipeline.SourceHealth;

/**
 * Writes results and decides the exit code. Data goes to stdout only when the run is trustworthy; the health summary
 * always goes to stderr.
 *
 * @author jgohil
 */
final class Output {

	enum Format {
		json, jsonl, table
	}

	private Output() {}

	static int emit(Envelope env, Aggregator.Status status, Format format, boolean strict, boolean allowPartial,
			boolean quiet, PrintStream out, PrintStream err) {
		summarise(env, err, quiet);
		if (status == Aggregator.Status.FAILED || (strict && status == Aggregator.Status.PARTIAL)) {
			err.println("jnews: FAILED - not writing data (" + reason(env, status, strict) + ")");
			return ExitCodes.FAILED;
		}
		try {
			switch (format) {
			case json -> out.println(Json.OUTPUT.writerWithDefaultPrettyPrinter().writeValueAsString(env));
			case jsonl -> {
				for (NewsItem i : env.items()) {
					out.println(Json.OUTPUT.writeValueAsString(i));
				}
			}
			case table -> table(env.items(), out);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		out.flush();
		if (out.checkError()) {
			err.println("jnews: FAILED - could not write the output (closed pipe or full disk); treat it as missing");
			return ExitCodes.FAILED;
		}
		if (status == Aggregator.Status.PARTIAL && !allowPartial) {
			return ExitCodes.PARTIAL;
		}
		return ExitCodes.OK;
	}

	private static String reason(Envelope env, Aggregator.Status status, boolean strict) {
		if (strict && status == Aggregator.Status.PARTIAL) {
			return "--strict and some sources are not healthy";
		}
		List<String> missing = env.categories().stream().filter(c -> !c.ok()).map(c -> c.id() + " " + c.healthy() + "/" + c.minHealthy()).toList();
		return missing.isEmpty() ? "no healthy source" : "categories below minimum: " + String.join(", ", missing);
	}

	static void summarise(Envelope env, PrintStream err, boolean quiet) {
		long ok = env.sources().stream().filter(SourceHealth::isOk).count();
		err.printf("jnews: %s - %d item(s), %d/%d source(s) ok%n", env.status().toUpperCase(), env.items().size(), ok,
				env.sources().size());
		if (quiet) {
			return;
		}
		for (SourceHealth h : env.sources()) {
			if (!h.isOk()) {
				err.printf("  %-8s %-26s %s%n", h.status().toUpperCase(), h.id(), h.error() == null ? "" : h.error());
			} else if (h.note() != null) {
				err.printf("  %-8s %-26s %s%n", "NOTE", h.id(), h.note());
			}
		}
	}

	static void table(List<NewsItem> items, PrintStream out) {
		DateTimeFormatter f = DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());
		Instant now = Instant.now();
		for (NewsItem i : items) {
			String age = age(Duration.between(i.publishedAt(), now));
			String src = i.source().id();
			String title = i.title().length() > 110 ? i.title().substring(0, 109) + "…" : i.title();
			String extra = i.alsoCoveredBy().isEmpty() ? "" : " (+" + i.alsoCoveredBy().size() + ")";
			out.printf("%s %5s  %-22s %s%s%n", f.format(i.publishedAt()), age, src.length() > 22 ? src.substring(0, 22) : src,
					title, extra);
		}
	}

	static String age(Duration d) {
		if (d.isNegative()) {
			return "now";
		}
		if (d.toMinutes() < 60) {
			return d.toMinutes() + "m";
		}
		if (d.toHours() < 48) {
			return d.toHours() + "h";
		}
		return d.toDays() + "d";
	}
}
