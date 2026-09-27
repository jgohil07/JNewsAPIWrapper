package com.main.java.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.main.java.core.NewsItem;
import com.main.java.core.NewsItem.Coverage;
import com.main.java.core.NewsItem.SourceRef;
import com.main.java.core.NewsItem.SymbolTag;
import com.main.java.core.NewsItem.Topic;
import com.main.java.pipeline.Aggregator;
import com.main.java.pipeline.Envelope;
import com.main.java.pipeline.SourceHealth;
import com.main.java.pipeline.Validator;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

/** The machine contract: schema-valid JSON on success, nothing on stdout on failure, documented exit codes. */
class OutputContractTest {

	static final Instant T = Instant.parse("2026-09-27T08:00:00Z");

	static Aggregator.Result result(Aggregator.Status status) {
		NewsItem item = new NewsItem(Validator.id("https://et.example/a"), "RBI keeps repo rate unchanged",
				"https://et.example/a", new SourceRef("et-economy", "Economic Times · Economy"), T.minusSeconds(600), T,
				null, "news", "in", "en", List.of("economy"),
				List.of(new Topic("economy", "feed"), new Topic("rbi", "rule")),
				List.of(new SymbolTag("MBAPL", "exact")),
				List.of(new Coverage("mint-economy", "Mint · Economy", "https://mint.example/b", T.minusSeconds(300))));
		SourceHealth ok = new SourceHealth("et-economy", "Economic Times · Economy", "https://et.example/", List.of("economy"),
				false, SourceHealth.OK, 1, 0, T.minusSeconds(600), T.minusSeconds(600), 72, 120, null, null);
		SourceHealth bad = new SourceHealth("bs-economy", "Business Standard · Economy", null, List.of("economy"), false,
				SourceHealth.FAILED, 0, 0, null, null, 72, 90, "HTTP 403 from https://bs.example/rss", null);
		return new Aggregator.Result(T, status, List.of(item), List.of(ok, bad),
				List.of(new Aggregator.CategoryStatus("economy", "Economy & Policy", "india-finance", 1, 2, true,
						status != Aggregator.Status.FAILED)));
	}

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private final ByteArrayOutputStream err = new ByteArrayOutputStream();

	int emit(Aggregator.Status status, Output.Format format, boolean strict, boolean allowPartial) {
		Aggregator.Result r = result(status);
		return Output.emit(Envelope.of(r, Map.of("command", "test")), status, format, strict, allowPartial, false,
				new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
	}

	static Schema schema() throws Exception {
		try (InputStream in = OutputContractTest.class.getResourceAsStream("/schema/news-envelope.v1.json")) {
			return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(in);
		}
	}

	@Test
	void jsonOutputMatchesTheSchema() throws Exception {
		assertEquals(ExitCodes.OK, emit(Aggregator.Status.OK, Output.Format.json, false, false));
		String json = out.toString(StandardCharsets.UTF_8);
		var errors = schema().validate(json, InputFormat.JSON);
		assertTrue(errors.isEmpty(), errors.toString());
		assertTrue(json.contains("\"published_at\" : \"2026-09-27T07:50:00Z\""), "ISO-8601 UTC timestamps");
	}

	@Test
	void schemaRejectsDriftedDocuments() throws Exception {
		emit(Aggregator.Status.OK, Output.Format.json, false, false);
		String json = out.toString(StandardCharsets.UTF_8);
		assertTrue(!schema().validate(json.replace("\"schema_version\" : 1", "\"schema_version\" : 2"), InputFormat.JSON).isEmpty());
		assertTrue(!schema().validate(json.replace("\"match\" : \"exact\"", "\"match\" : \"guess\""), InputFormat.JSON).isEmpty());
		assertTrue(!schema().validate(json.replace("\"status\" : \"ok\",\n  \"categories\"", "\"status\" : \"failed\",\n  \"categories\""), InputFormat.JSON).isEmpty());
	}

	@Test
	void failedRunWritesNothingToStdout() {
		assertEquals(ExitCodes.FAILED, emit(Aggregator.Status.FAILED, Output.Format.json, false, false));
		assertEquals("", out.toString(StandardCharsets.UTF_8));
		String e = err.toString(StandardCharsets.UTF_8);
		assertTrue(e.contains("FAILED"));
		assertTrue(e.contains("bs-economy"));
		assertTrue(e.contains("economy 1/2"));
	}

	@Test
	void failedWriteIsAFailure() {
		// Review finding 6: a closed pipe or full disk must not exit 0.
		PrintStream broken = new PrintStream(new java.io.OutputStream() {
			@Override
			public void write(int b) throws java.io.IOException {
				throw new java.io.IOException("closed");
			}
		}, true, StandardCharsets.UTF_8);
		int code = Output.emit(Envelope.of(result(Aggregator.Status.OK), Map.of()), Aggregator.Status.OK, Output.Format.json,
				false, false, true, broken, new PrintStream(err, true, StandardCharsets.UTF_8));
		assertEquals(ExitCodes.FAILED, code);
	}

	@Test
	void cliStreamsAreUtf8WhateverTheLocale() {
		// Review finding 2.
		JNewsCli cli = new JNewsCli();
		assertEquals(StandardCharsets.UTF_8, cli.out.charset());
		assertEquals(StandardCharsets.UTF_8, cli.err.charset());
	}

	@Test
	void partialExitCodes() {
		assertEquals(ExitCodes.PARTIAL, emit(Aggregator.Status.PARTIAL, Output.Format.jsonl, false, false));
		assertEquals(1, out.toString(StandardCharsets.UTF_8).lines().count(), "jsonl: one item per line");
		out.reset();
		assertEquals(ExitCodes.OK, emit(Aggregator.Status.PARTIAL, Output.Format.jsonl, false, true));
		out.reset();
		assertEquals(ExitCodes.FAILED, emit(Aggregator.Status.PARTIAL, Output.Format.json, true, false));
		assertEquals("", out.toString(StandardCharsets.UTF_8));
	}
}
