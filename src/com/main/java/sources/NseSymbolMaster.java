package com.main.java.sources;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.main.java.core.HttpFetcher;
import com.main.java.core.NewsException;

/**
 * NSE's list of listed equities ({@code EQUITY_L.csv}): symbol, company name and ISIN.
 *
 * @author jgohil
 */
public final class NseSymbolMaster {

	public static final String DEFAULT_URL = "https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv";

	/** One listed company. */
	public record Company(String symbol, String name, String isin) {}

	private final Map<String, Company> bySymbol;
	private final Map<String, String> symbolByName;

	private NseSymbolMaster(Map<String, Company> bySymbol) {
		this.bySymbol = Collections.unmodifiableMap(bySymbol);
		Map<String, String> byName = new HashMap<>();
		Map<String, Integer> nameCount = new HashMap<>();
		for (Company c : bySymbol.values()) {
			String key = normalise(c.name());
			byName.put(key, c.symbol());
			nameCount.merge(key, 1, Integer::sum);
		}
		// A name shared by two symbols is ambiguous: never map it.
		nameCount.forEach((name, n) -> {
			if (n > 1) {
				byName.remove(name);
			}
		});
		this.symbolByName = Collections.unmodifiableMap(byName);
	}

	/** Downloads and parses the list. */
	public static NseSymbolMaster load(HttpFetcher http, String url) {
		HttpFetcher.Response r = http.get(URI.create(url), Map.of("User-Agent", HttpFetcher.BROWSER_USER_AGENT));
		return parse(new String(r.body(), StandardCharsets.UTF_8));
	}

	/**
	 * @throws NewsException when the header is not the expected one or the list is implausibly short
	 */
	public static NseSymbolMaster parse(String csv) {
		List<String> lines = csv.lines().filter(l -> !l.isBlank()).toList();
		if (lines.isEmpty() || !lines.get(0).replace(" ", "").toUpperCase(Locale.ROOT).startsWith("SYMBOL,NAMEOFCOMPANY,SERIES")) {
			throw new NewsException("NSE equity list has an unexpected header");
		}
		Map<String, Company> map = new HashMap<>();
		for (String line : lines.subList(1, lines.size())) {
			List<String> f = splitCsv(line);
			if (f.size() < 7) {
				throw new NewsException("NSE equity list has a malformed row");
			}
			String symbol = f.get(0).strip();
			map.put(symbol, new Company(symbol, f.get(1).strip(), f.get(6).strip()));
		}
		if (map.size() < 500) {
			throw new NewsException("NSE equity list has only " + map.size() + " rows; refusing to use it");
		}
		return new NseSymbolMaster(map);
	}

	public Optional<Company> company(String symbol) {
		return symbol == null ? Optional.empty() : Optional.ofNullable(bySymbol.get(symbol.strip().toUpperCase(Locale.ROOT)));
	}

	/** @return the symbol whose registered name equals {@code name} (ignoring case and punctuation), if unique */
	public Optional<String> symbolForExactName(String name) {
		return Optional.ofNullable(symbolByName.get(normalise(name)));
	}

	public int size() {
		return bySymbol.size();
	}

	static String normalise(String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
	}

	private static List<String> splitCsv(String line) {
		List<String> out = new java.util.ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					cur.append('"');
					i++;
				} else {
					quoted = !quoted;
				}
			} else if (c == ',' && !quoted) {
				out.add(cur.toString());
				cur.setLength(0);
			} else {
				cur.append(c);
			}
		}
		out.add(cur.toString());
		return out;
	}
}
