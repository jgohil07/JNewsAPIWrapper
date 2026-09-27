package com.main.java.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Configuration lookup: real environment variables first, then a local {@code .env} file.
 * <p>
 * Values are secrets and are never logged or included in exception messages; only key names are.
 *
 * @author jgohil
 */
public final class Env {

	/** System property that points to an alternative .env file. */
	public static final String ENV_FILE_PROPERTY = "jnews.envFile";

	private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	private static volatile Map<String, String> fileValues;

	private Env() {}

	/**
	 * @return the value for {@code key}, or empty when it is unset or blank
	 */
	public static Optional<String> get(String key) {
		String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			value = fileValues().get(key);
		}
		return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
	}

	/**
	 * @return the value for {@code key}
	 * @throws ConfigException when the key is unset or blank
	 */
	public static String require(String key) {
		return get(key).orElseThrow(() -> new ConfigException(
				"Missing required setting " + key + ". Set it as an environment variable or in .env (see .env.example)."));
	}

	/** Forgets the cached .env contents; the next lookup re-reads the file. */
	public static void reload() {
		fileValues = null;
	}

	private static Map<String, String> fileValues() {
		Map<String, String> values = fileValues;
		if (values == null) {
			synchronized (Env.class) {
				values = fileValues;
				if (values == null) {
					values = load(Paths.get(System.getProperty(ENV_FILE_PROPERTY, ".env")));
					fileValues = values;
				}
			}
		}
		return values;
	}

	/**
	 * Parses a .env file: {@code KEY=VALUE} lines, {@code #} comments, optional {@code export } prefix and
	 * matching single or double quotes around the value. Any other line is an error, reported by line number only.
	 *
	 * @return the parsed values, or an empty map when the file does not exist
	 */
	static Map<String, String> load(Path file) {
		if (!Files.exists(file)) {
			return Collections.emptyMap();
		}
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot read " + file, e);
		}
		Map<String, String> values = new HashMap<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (line.startsWith("export ")) {
				line = line.substring("export ".length()).strip();
			}
			int eq = line.indexOf('=');
			String key = eq > 0 ? line.substring(0, eq).strip() : "";
			if (!KEY.matcher(key).matches()) {
				throw new ConfigException("Malformed line " + (i + 1) + " in " + file + " (expected KEY=VALUE)");
			}
			String value = line.substring(eq + 1).strip();
			if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'')
					&& value.charAt(value.length() - 1) == value.charAt(0)) {
				value = value.substring(1, value.length() - 1);
			}
			values.put(key, value);
		}
		return Collections.unmodifiableMap(values);
	}
}
