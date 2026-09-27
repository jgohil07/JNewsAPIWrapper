package com.main.java.core;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict date parsing for feeds. A date that cannot be read unambiguously is rejected (empty result), never guessed.
 * A missing time zone is accepted only when the source declares its default zone.
 *
 * @author jgohil
 */
public final class Dates {

	private static final Pattern RFC822 = Pattern.compile(
			"^(?:[A-Za-z]{3,9},?\\s+)?(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?\\s+(\\d{4})\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:\\.\\d+)?\\s*(\\S*)$");

	private static final Pattern OFFSET = Pattern.compile("^([+-])(\\d{2}):?(\\d{2})$");

	private static final Map<String, String> MONTHS = Map.ofEntries(Map.entry("jan", "01"), Map.entry("feb", "02"),
			Map.entry("mar", "03"), Map.entry("apr", "04"), Map.entry("may", "05"), Map.entry("jun", "06"),
			Map.entry("jul", "07"), Map.entry("aug", "08"), Map.entry("sep", "09"), Map.entry("oct", "10"),
			Map.entry("nov", "11"), Map.entry("dec", "12"));

	/** Zone abbreviations seen in feeds. IST is India Standard Time here (feeds in scope are Indian). */
	private static final Map<String, ZoneId> ABBREVIATIONS = Map.ofEntries(Map.entry("GMT", ZoneOffset.UTC),
			Map.entry("UT", ZoneOffset.UTC), Map.entry("UTC", ZoneOffset.UTC), Map.entry("Z", ZoneOffset.UTC),
			Map.entry("IST", ZoneOffset.ofHoursMinutes(5, 30)), Map.entry("EST", ZoneOffset.ofHours(-5)),
			Map.entry("EDT", ZoneOffset.ofHours(-4)), Map.entry("CST", ZoneOffset.ofHours(-6)),
			Map.entry("CDT", ZoneOffset.ofHours(-5)), Map.entry("MST", ZoneOffset.ofHours(-7)),
			Map.entry("MDT", ZoneOffset.ofHours(-6)), Map.entry("PST", ZoneOffset.ofHours(-8)),
			Map.entry("PDT", ZoneOffset.ofHours(-7)), Map.entry("BST", ZoneOffset.ofHours(1)),
			Map.entry("CET", ZoneOffset.ofHours(1)), Map.entry("CEST", ZoneOffset.ofHours(2)));

	private Dates() {}

	/**
	 * Parses RFC 822/1123 dates (with or without weekday, zone offset or abbreviation) and ISO-8601 date-times.
	 *
	 * @param defaultZone zone for values without one, or null to reject such values
	 */
	public static Optional<Instant> parse(String raw, ZoneId defaultZone) {
		if (raw == null) {
			return Optional.empty();
		}
		String s = raw.strip();
		if (s.isEmpty()) {
			return Optional.empty();
		}
		Optional<Instant> iso = parseIso(s, defaultZone);
		if (iso.isPresent()) {
			return iso;
		}
		return parseRfc822(s, defaultZone);
	}

	/** Parses with an explicit pattern (e.g. NSE's {@code dd-MMM-yyyy HH:mm:ss}) in {@code zone}. */
	public static Optional<Instant> parse(String raw, String pattern, ZoneId zone) {
		if (raw == null || pattern == null || zone == null) {
			return Optional.empty();
		}
		try {
			DateTimeFormatter f = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
			return Optional.of(LocalDateTime.parse(raw.strip(), f).atZone(zone).toInstant());
		} catch (DateTimeParseException e) {
			return Optional.empty();
		}
	}

	private static Optional<Instant> parseIso(String s, ZoneId defaultZone) {
		try {
			return Optional.of(OffsetDateTime.parse(s).toInstant());
		} catch (DateTimeParseException e) {
			// fall through
		}
		if (defaultZone != null) {
			try {
				return Optional.of(LocalDateTime.parse(s).atZone(defaultZone).toInstant());
			} catch (DateTimeParseException e) {
				// fall through
			}
		}
		return Optional.empty();
	}

	private static Optional<Instant> parseRfc822(String s, ZoneId defaultZone) {
		Matcher m = RFC822.matcher(s);
		if (!m.matches()) {
			return Optional.empty();
		}
		String month = MONTHS.get(m.group(2).substring(0, 3).toLowerCase(Locale.ROOT));
		if (month == null) {
			return Optional.empty();
		}
		ZoneId zone = zone(m.group(7), defaultZone);
		if (zone == null) {
			return Optional.empty();
		}
		try {
			LocalDateTime local = LocalDateTime.of(Integer.parseInt(m.group(3)), Integer.parseInt(month),
					Integer.parseInt(m.group(1)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)),
					m.group(6) == null ? 0 : Integer.parseInt(m.group(6)));
			return Optional.of(local.atZone(zone).toInstant());
		} catch (DateTimeException e) {
			return Optional.empty();
		}
	}

	private static ZoneId zone(String token, ZoneId defaultZone) {
		if (token == null || token.isEmpty()) {
			return defaultZone;
		}
		Matcher o = OFFSET.matcher(token);
		if (o.matches()) {
			int h = Integer.parseInt(o.group(2));
			int mm = Integer.parseInt(o.group(3));
			if (h > 18 || mm > 59) {
				return null;
			}
			int sign = o.group(1).equals("-") ? -1 : 1;
			return ZoneOffset.ofHoursMinutes(sign * h, sign * mm);
		}
		return ABBREVIATIONS.get(token.toUpperCase(Locale.ROOT));
	}
}
