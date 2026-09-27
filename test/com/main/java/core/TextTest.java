package com.main.java.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextTest {

	@Test
	void stripsTagsScriptsAndDecodesEntities() {
		assertEquals("Sensex up 1% & Nifty – flat",
				Text.plain("<p>Sensex <b>up</b> 1% &amp; Nifty &ndash; flat</p><script>alert(1)</script>"));
	}

	@Test
	void doubleEscapedMarkupIsRemoved() {
		assertEquals("Rupee gains", Text.plain("&lt;p&gt;Rupee &lt;em&gt;gains&lt;/em&gt;&lt;/p&gt;"));
	}

	@Test
	void keepsComparisonsThatAreNotTags() {
		assertEquals("profit < 5% and margin > 3%", Text.plain("profit &lt; 5% and margin &gt; 3%"));
	}

	@Test
	void numericEntitiesAndInvalidOnes() {
		assertEquals("₹ ₹ &#xD800;", Text.plain("&#8377; &#x20B9; &#xD800;"));
	}

	@Test
	void truncatesAtWordBoundary() {
		String s = Text.plain("alpha beta gamma delta epsilon", 17);
		assertEquals("alpha beta gamma…", s);
		assertTrue(s.length() <= 17);
	}

	@Test
	void nullStaysNull() {
		assertNull(Text.plain(null));
	}
}
