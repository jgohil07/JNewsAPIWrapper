package com.main.java.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.spi.ToolProvider;

import org.junit.jupiter.api.Test;

/**
 * Guards the public API of the original (2017) classes against regressions: every public or protected member recorded
 * in {@code legacy-api-snapshot.txt} (taken with javap before any change) must still exist with the same signature.
 * New members may be added.
 */
class LegacyApiSignatureTest {

	private static final String[] LEGACY_CLASSES = {
			"aggreators.ArticleAggreator", "aggreators.SourcesAggreator", "base.Constants", "models.Article",
			"models.Articles", "models.Source", "models.Sources", "models.UrlsToLogos",
			"startup.InitializeNewsWrapper", "utils.JsonFactory" };

	@Test
	void everyLegacySignatureStillExists() throws IOException {
		Set<String> expected = new LinkedHashSet<>(snapshot());
		Set<String> actual = new LinkedHashSet<>(currentSignatures());

		List<String> missing = new ArrayList<>();
		for (String line : expected) {
			if (!actual.contains(line)) {
				missing.add(line);
			}
		}
		assertTrue(missing.isEmpty(), "Legacy public API changed or removed:\n  " + String.join("\n  ", missing));
	}

	@Test
	void snapshotCoversAllLegacyClasses() throws IOException {
		long headers = snapshot().stream().filter(l -> l.endsWith("{")).count();
		assertEquals(LEGACY_CLASSES.length, headers);
	}

	private static List<String> snapshot() throws IOException {
		try (InputStream in = LegacyApiSignatureTest.class.getResourceAsStream("/legacy-api-snapshot.txt")) {
			Objects.requireNonNull(in, "legacy-api-snapshot.txt missing from test resources");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();
		}
	}

	private static List<String> currentSignatures() {
		ToolProvider javap = ToolProvider.findFirst("javap").orElseThrow(() -> new IllegalStateException("javap not available"));
		Path classes = Paths.get("target", "classes");
		List<String> args = new ArrayList<>(List.of("-protected", "-cp", classes.toString()));
		for (String c : LEGACY_CLASSES) {
			args.add("com.main.java." + c);
		}
		StringWriter out = new StringWriter();
		StringWriter err = new StringWriter();
		int rc = javap.run(new PrintWriter(out), new PrintWriter(err), args.toArray(new String[0]));
		assertEquals(0, rc, "javap failed: " + err);
		List<String> lines = new ArrayList<>();
		for (String line : out.toString().lines().toList()) {
			if (line.startsWith("Compiled from") || line.equals("}") || line.isBlank()) {
				continue;
			}
			lines.add(line.startsWith("  ") ? line.substring(2) : line);
		}
		return lines;
	}
}
