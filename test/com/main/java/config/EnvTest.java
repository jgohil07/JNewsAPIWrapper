package com.main.java.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvTest {

	@TempDir
	Path dir;

	@Test
	void parsesCommentsQuotesAndExport() throws IOException {
		Path f = dir.resolve(".env");
		Files.writeString(f, """
				# comment
				A=1
				export B = "two words"
				C='single'
				EMPTY=

				""");
		Map<String, String> v = Env.load(f);
		assertEquals("1", v.get("A"));
		assertEquals("two words", v.get("B"));
		assertEquals("single", v.get("C"));
		assertEquals("", v.get("EMPTY"));
	}

	@Test
	void malformedLineNamesLineButNotContent() throws IOException {
		Path f = dir.resolve(".env");
		Files.writeString(f, "GOOD=1\nsupersecretvalue\n");
		ConfigException e = assertThrows(ConfigException.class, () -> Env.load(f));
		assertTrue(e.getMessage().contains("line 2"));
		assertFalse(e.getMessage().contains("supersecretvalue"));
	}

	@Test
	void missingFileIsEmpty() {
		assertTrue(Env.load(dir.resolve("absent")).isEmpty());
	}

	@Test
	void requireNamesTheMissingKey() {
		ConfigException e = assertThrows(ConfigException.class, () -> Env.require("JNEWS_TEST_SURELY_UNSET_KEY"));
		assertTrue(e.getMessage().contains("JNEWS_TEST_SURELY_UNSET_KEY"));
	}
}
