package com.main.java.sources;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.main.java.core.Dates;
import com.main.java.core.HttpFetcher;
import com.main.java.core.NewsException;
import com.main.java.core.NewsItem.SymbolTag;

/**
 * NSE corporate announcements for one symbol (JSON API used by nseindia.com). Every record must carry the requested
 * symbol; anything else means the API changed and the whole response is rejected.
 *
 * @author jgohil
 */
public class NseSymbolAnnouncementsSource extends JsonApiSource {

	public static final String API = "https://www.nseindia.com/api/corporate-announcements?index=equities&symbol=";

	private final String symbol;
	private volatile String note;

	public NseSymbolAnnouncementsSource(SourceConfig config, HttpFetcher http, String symbol) {
		super(config, http);
		this.symbol = symbol.toUpperCase(Locale.ROOT);
	}

	@Override
	public List<RawItem> fetch() {
		String base = config.url() != null ? config.url() : API;
		JsonNode root = getJson(URI.create(base + java.net.URLEncoder.encode(symbol, java.nio.charset.StandardCharsets.UTF_8)),
				Map.of("User-Agent", HttpFetcher.BROWSER_USER_AGENT, "Accept", "application/json",
						"Referer", "https://www.nseindia.com/companies-listing/corporate-filings-announcements"));
		List<RawItem> all = mapArray(root, "announcements");
		// Announcements without a document carry attchmntFile "-": they have no page of their own to link to, so
		// they are left out and counted (see note()) rather than given a made-up URL.
		List<RawItem> linked = all.stream().filter(i -> i.url() != null && i.url().startsWith("https://")).toList();
		int skipped = all.size() - linked.size();
		if (skipped * 2 > all.size()) {
			// Normally ~15% have no document (e.g. "News Verification"); a majority means the format changed.
			throw new NewsException(config.id() + ": " + skipped + " of " + all.size()
					+ " announcements have no document link; the response format may have changed");
		}
		note = skipped > 0 ? skipped + " announcement(s) without a document skipped" : null;
		return linked;
	}

	@Override
	public String note() {
		return note;
	}

	@Override
	protected RawItem map(JsonNode a) {
		String sym = text(a, "symbol");
		if (sym == null || !sym.equalsIgnoreCase(symbol)) {
			throw new NewsException(config.id() + ": record for '" + sym + "' in the response for '" + symbol + "'");
		}
		String company = text(a, "sm_name");
		String desc = text(a, "desc");
		String title = company == null ? null : desc == null ? company : company + ": " + desc;
		String date = text(a, "an_dt");
		return new RawItem(title, text(a, "attchmntFile"), text(a, "attchmntText"),
				Dates.parse(date, "dd-MMM-yyyy HH:mm:ss", config.zone()).orElse(null), date, null,
				List.of(new SymbolTag(symbol, "exact")));
	}
}
