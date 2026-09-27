package com.main.java.cli;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Parses {@code 90m}, {@code 24h}, {@code 2d}, {@code 1w} (between 1 minute and 31 days).
 *
 * @author jgohil
 */
public final class Durations implements ITypeConverter<Duration> {

	private static final Pattern P = Pattern.compile("^(\\d{1,4})([mhdw])$");
	static final Duration MIN = Duration.ofMinutes(1);
	static final Duration MAX = Duration.ofDays(31);

	@Override
	public Duration convert(String value) {
		return parse(value);
	}

	public static Duration parse(String value) {
		Matcher m = P.matcher(value == null ? "" : value.strip());
		if (!m.matches()) {
			throw new TypeConversionException("'" + value + "' is not a duration like 90m, 24h, 2d or 1w");
		}
		long n = Long.parseLong(m.group(1));
		Duration d = switch (m.group(2)) {
		case "m" -> Duration.ofMinutes(n);
		case "h" -> Duration.ofHours(n);
		case "d" -> Duration.ofDays(n);
		default -> Duration.ofDays(7 * n);
		};
		if (d.compareTo(MIN) < 0 || d.compareTo(MAX) > 0) {
			throw new TypeConversionException("'" + value + "' must be between 1m and 31d");
		}
		return d;
	}
}
