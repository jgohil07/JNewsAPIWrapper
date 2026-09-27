package com.main.java.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class DatesTest {

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	@Test
	void rfc822Variants() {
		Instant expected = Instant.parse("2026-09-27T06:24:51Z");
		assertEquals(expected, Dates.parse("Sun, 27 Sep 2026 11:54:51 +0530", null).orElseThrow());
		assertEquals(expected, Dates.parse("Sun, 27 Sep 2026 06:24:51 GMT", null).orElseThrow());
		assertEquals(expected, Dates.parse("27 Sep 2026 11:54:51 IST", null).orElseThrow());
		assertEquals(expected, Dates.parse("Sun, 27 Sep 2026 11:54:51 +05:30", null).orElseThrow());
		assertEquals(Instant.parse("2026-09-05T06:24:00Z"), Dates.parse("Sat, 5 Sep 2026 06:24 GMT", null).orElseThrow());
	}

	@Test
	void missingZoneNeedsADeclaredDefault() {
		assertTrue(Dates.parse("Fri, 25 Sep 2026 21:50:00", null).isEmpty());
		assertEquals(Instant.parse("2026-09-25T16:20:00Z"), Dates.parse("Fri, 25 Sep 2026 21:50:00", IST).orElseThrow());
		assertTrue(Dates.parse("2026-09-25T21:50:00", null).isEmpty());
		assertEquals(Instant.parse("2026-09-25T16:20:00Z"), Dates.parse("2026-09-25T21:50:00", IST).orElseThrow());
	}

	@Test
	void iso8601() {
		assertEquals(Instant.parse("2026-09-27T06:00:00Z"), Dates.parse("2026-09-27T06:00:00Z", null).orElseThrow());
		assertEquals(Instant.parse("2026-09-27T06:00:00Z"), Dates.parse("2026-09-27T11:30:00+05:30", null).orElseThrow());
	}

	@Test
	void nsePattern() {
		assertEquals(Instant.parse("2026-09-27T07:09:15Z"),
				Dates.parse("27-Sep-2026 12:39:15", "dd-MMM-yyyy HH:mm:ss", IST).orElseThrow());
		assertTrue(Dates.parse("2026-09-27 12:39:15", "dd-MMM-yyyy HH:mm:ss", IST).isEmpty());
	}

	@Test
	void rejectsAmbiguousOrInvalid() {
		for (String bad : new String[] { "", "yesterday", "Sun, 27 Sep 26 11:54:51 GMT", "31 Feb 2026 10:00:00 GMT",
				"27 Sep 2026 25:00:00 GMT", "27 Sep 2026 10:00:00 XYZ", "27 Foo 2026 10:00:00 GMT" }) {
			assertTrue(Dates.parse(bad, IST).isEmpty(), bad);
		}
	}
}
