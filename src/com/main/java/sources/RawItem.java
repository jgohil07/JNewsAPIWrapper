package com.main.java.sources;

import java.time.Instant;
import java.util.List;

import com.main.java.core.NewsItem.SymbolTag;

/**
 * An item as read from a source, before validation.
 *
 * @param published   parsed date, or null when {@code publishedRaw} could not be read
 * @param sourceName  publisher named by an aggregating feed (Google News), or null
 * @param symbols     symbols the source itself attached (exchange filings)
 * @author jgohil
 */
public record RawItem(String title, String url, String summaryHtml, Instant published, String publishedRaw,
		String sourceName, List<SymbolTag> symbols) {

	public RawItem {
		symbols = symbols == null ? List.of() : List.copyOf(symbols);
	}

	public static RawItem of(String title, String url, String summaryHtml, Instant published, String publishedRaw) {
		return new RawItem(title, url, summaryHtml, published, publishedRaw, null, List.of());
	}
}
