package com.main.java.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class SymbolQueryTest {

	@Test
	void shortNameDropsOnlyLegalSuffixes() {
		assertEquals("Madhya Bharat Agro Products", SymbolQuery.shortName("Madhya Bharat Agro Products Limited"));
		assertEquals("Bank of India", SymbolQuery.shortName("Bank of India"));
		assertEquals("Oil and Natural Gas Corporation", SymbolQuery.shortName("Oil and Natural Gas Corporation Limited"));
		assertEquals("Acme Foods", SymbolQuery.shortName("Acme Foods Pvt. Ltd."));
	}

	@Test
	void matchesWholeNameAsWrittenOrInCapitals() {
		Pattern p = SymbolQuery.namePattern("Reliance Industries");
		assertTrue(SymbolQuery.mentions(p, "Reliance Industries Q2 profit rises 9%"));
		assertTrue(SymbolQuery.mentions(p, "Why RELIANCE INDUSTRIES shares fell"));
		assertTrue(SymbolQuery.mentions(p, "Markets: Reliance Industries leads gains"));
		assertFalse(SymbolQuery.mentions(p, "reliance industries in general"), "lower case is not the company");
		assertFalse(SymbolQuery.mentions(p, "Reliance Industriesque"));
	}

	@Test
	void ignoresNameInsideALongerProperNoun() {
		Pattern p = SymbolQuery.namePattern("Bank of India");
		assertFalse(SymbolQuery.mentions(p, "State Bank of India raises lending rates"));
		assertTrue(SymbolQuery.mentions(p, "Bank of India raises lending rates"));
		assertTrue(SymbolQuery.mentions(p, "Shares of Bank of India rise; State Bank of India flat"));
		assertTrue(SymbolQuery.mentions(p, "Buy Bank of India, says broker"));
	}

	@Test
	void ignoresNameAtTheStartOfALongerName() {
		// Review finding 5.
		assertFalse(SymbolQuery.mentions(SymbolQuery.namePattern("Arvind"), "Arvind Kejriwal slams the Centre"));
		assertFalse(SymbolQuery.mentions(SymbolQuery.namePattern("Atul"), "Atul Kumar appointed chairman"));
		assertFalse(SymbolQuery.mentions(SymbolQuery.namePattern("Mahindra & Mahindra"), "Mahindra & Mahindra Financial Services raises funds"));
		assertFalse(SymbolQuery.mentions(SymbolQuery.namePattern("Tata Motors"), "Tata Motors Passenger Vehicles cuts prices"));
		assertFalse(SymbolQuery.mentions(SymbolQuery.namePattern("PTC India"), "PTC India Financial Services posts loss"));
		assertTrue(SymbolQuery.mentions(SymbolQuery.namePattern("Tata Motors"), "Tata Motors Q2 profit jumps 20%"));
		assertTrue(SymbolQuery.mentions(SymbolQuery.namePattern("Reliance Industries"), "Reliance Industries Shares Rise On Jio Listing Buzz"));
		assertTrue(SymbolQuery.mentions(SymbolQuery.namePattern("Mahindra & Mahindra"), "Mahindra & Mahindra, Maruti lead auto rally"));
	}

	@Test
	void validSymbols() {
		assertTrue(SymbolQuery.SYMBOL.matcher("M&M").matches());
		assertTrue(SymbolQuery.SYMBOL.matcher("BAJAJ-AUTO").matches());
		assertFalse(SymbolQuery.SYMBOL.matcher("tcs").matches());
		assertFalse(SymbolQuery.SYMBOL.matcher("TCS;rm").matches());
	}
}
