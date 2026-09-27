package com.main.java.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import picocli.CommandLine.TypeConversionException;

/** Usage errors exit 2 before any network access. */
class CliUsageTest {

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private final ByteArrayOutputStream err = new ByteArrayOutputStream();

	int run(String... args) {
		JNewsCli root = new JNewsCli();
		root.out = new PrintStream(out, true, StandardCharsets.UTF_8);
		root.err = new PrintStream(err, true, StandardCharsets.UTF_8);
		return JNewsCli.commandLine(root).execute(args);
	}

	@Test
	void usageErrorsExitTwoWithEmptyStdout() {
		assertEquals(ExitCodes.USAGE, run("india", "--symbol", "bad sym!"));
		assertEquals(ExitCodes.USAGE, run("fetch", "--category", "nope"));
		assertEquals(ExitCodes.USAGE, run("india", "--since", "5y"));
		assertEquals(ExitCodes.USAGE, run("india", "--strict", "--allow-partial"));
		assertEquals(ExitCodes.USAGE, run("fetch", "--format", "xml"));
		assertEquals(ExitCodes.USAGE, run("--env-file", "does-not-exist.env", "sources"));
		assertEquals(ExitCodes.USAGE, run());
		assertEquals("", out.toString(StandardCharsets.UTF_8));
		assertTrue(err.toString(StandardCharsets.UTF_8).contains("not a valid NSE symbol"));
	}

	@Test
	void listingSourcesNeedsNoNetwork() {
		assertEquals(ExitCodes.OK, run("sources"));
		String s = out.toString(StandardCharsets.UTF_8);
		assertTrue(s.contains("[india-markets]"));
		assertTrue(s.contains("cli-only"));
	}

	@Test
	void durations() {
		assertEquals(Duration.ofMinutes(90), Durations.parse("90m"));
		assertEquals(Duration.ofDays(14), Durations.parse("2w"));
		assertThrows(TypeConversionException.class, () -> Durations.parse("0m"));
		assertThrows(TypeConversionException.class, () -> Durations.parse("32d"));
		assertThrows(TypeConversionException.class, () -> Durations.parse("-1h"));
	}
}
