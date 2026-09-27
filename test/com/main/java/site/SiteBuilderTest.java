package com.main.java.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.java.cli.OutputContractTestAccess;
import com.main.java.core.NewsException;
import com.main.java.pipeline.Aggregator;
import com.main.java.pipeline.Classifier;
import com.main.java.pipeline.Envelope;
import com.main.java.sources.SourceRegistry;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

class SiteBuilderTest {

	@TempDir
	Path dir;

	private final SiteBuilder builder = new SiteBuilder(SourceRegistry.load(), Classifier.load());

	private static Envelope envelope() {
		return Envelope.of(OutputContractTestAccess.result(Aggregator.Status.OK), Map.of("command", "site"));
	}

	@Test
	void writesAssetsAndSchemaValidData() throws Exception {
		Path out = dir.resolve("site");
		builder.build(envelope(), out, 6);
		for (String f : new String[] { "index.html", "app.css", "app.js", "favicon.svg", ".nojekyll", SiteBuilder.MARKER,
				"data/latest.json", "data/india.json", "data/site.json" }) {
			assertTrue(Files.isRegularFile(out.resolve(f)), f);
		}
		try (InputStream in = SiteBuilderTest.class.getResourceAsStream("/schema/news-envelope.v1.json")) {
			var schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(in);
			for (String f : new String[] { "data/latest.json", "data/india.json" }) {
				var errors = schema.validate(Files.readString(out.resolve(f)), InputFormat.JSON);
				assertTrue(errors.isEmpty(), f + ": " + errors);
			}
		}
		JsonNode meta = new ObjectMapper().readTree(out.resolve("data/site.json").toFile());
		assertEquals(6, meta.get("refresh_hours").asInt());
		assertEquals(13, meta.get("categories").size());
		JsonNode finance = null;
		for (JsonNode c : meta.get("categories")) {
			if (c.get("id").asText().equals("finance")) {
				finance = c;
			}
			if (c.get("id").asText().equals("india-markets")) {
				assertEquals("finance", c.get("parent").asText());
			}
		}
		assertEquals(5, finance.get("children").size());
		assertTrue(finance.get("parent").isNull());
		assertEquals("IPOs", meta.get("topics").get("ipo").asText());
	}

	@Test
	void rebuildsOverItsOwnOutput() throws IOException {
		Path out = dir.resolve("site");
		builder.build(envelope(), out, 6);
		builder.build(envelope(), out, 6);
		assertTrue(Files.exists(out.resolve("data/latest.json")));
		try (var s = Files.list(out.resolve("data"))) {
			assertFalse(s.anyMatch(p -> p.toString().endsWith(".tmp")));
		}
	}

	@Test
	void refusesToWriteIntoAForeignDirectory() throws IOException {
		Path foreign = Files.createDirectories(dir.resolve("home"));
		Files.writeString(foreign.resolve("notes.txt"), "keep me");
		assertThrows(NewsException.class, () -> builder.build(envelope(), foreign, 6));
		assertEquals("keep me", Files.readString(foreign.resolve("notes.txt")));
		assertFalse(Files.exists(foreign.resolve("index.html")));
	}

	@Test
	void pageHasStrictCspAndNoInlineScript() throws IOException {
		Path out = dir.resolve("site");
		builder.build(envelope(), out, 6);
		String html = Files.readString(out.resolve("index.html"));
		assertTrue(html.contains("script-src 'self'"));
		assertFalse(html.contains("unsafe-inline"));
		assertFalse(html.matches("(?s).*<script>(?!</script>).*"), "no inline script blocks");
		String js = Files.readString(out.resolve("app.js"));
		assertFalse(java.util.regex.Pattern.compile("\\.(inner|outer)HTML\\s*=|insertAdjacentHTML|document\\.write").matcher(js).find(),
				"DOM is built with textContent only");
		assertFalse(js.contains("eval("));
	}
}
