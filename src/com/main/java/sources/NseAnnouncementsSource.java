package com.main.java.sources;

import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.main.java.core.HttpFetcher;
import com.main.java.core.NewsItem.SymbolTag;

/**
 * NSE's "Online announcements" RSS. Items name the company in {@code <title>} and the filing in
 * {@code <description>} ("... has informed the Exchange regarding X |SUBJECT: Y"); dates use
 * {@code dd-MMM-yyyy HH:mm:ss} in IST. The company name is mapped to its symbol only on an exact name match in NSE's
 * own equity list.
 *
 * @author jgohil
 */
public class NseAnnouncementsSource extends RssSource {

	private static final Pattern SUBJECT = Pattern.compile("\\|\\s*SUBJECT:\\s*(.+)$", Pattern.DOTALL);
	private static final Pattern REGARDING = Pattern.compile("informed the Exchange (?:regarding|about)\\s+(.+)$",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private final Supplier<NseSymbolMaster> masterSupplier;
	private volatile NseSymbolMaster master;
	private volatile String note;

	/** @param master supplies the symbol list for exact name matches; may return null to skip symbol tagging */
	public NseAnnouncementsSource(SourceConfig config, HttpFetcher http, Supplier<NseSymbolMaster> master) {
		super(config, http);
		this.masterSupplier = master;
	}

	@Override
	public List<RawItem> fetch() {
		master = masterSupplier == null ? null : masterSupplier.get();
		List<String> notes = new java.util.ArrayList<>();
		if (master == null) {
			notes.add("NSE symbol list unavailable; filings are not tagged with symbols");
		}
		// NSE lists routine notices (e.g. daily ETF NAV declarations) with an empty <link/> by design. They cannot
		// be linked to, so they are left out and counted here instead of being treated as a broken feed.
		List<RawItem> all = super.fetch();
		List<RawItem> linked = all.stream().filter(i -> i.url() != null && !i.url().isBlank()).toList();
		if (linked.size() < all.size()) {
			notes.add((all.size() - linked.size()) + " link-less notice(s) skipped");
		}
		note = notes.isEmpty() ? null : String.join("; ", notes);
		return linked;
	}

	@Override
	public String note() {
		return note;
	}

	@Override
	protected RawItem toRaw(FeedParser.Entry e) {
		String company = e.title() == null ? null : e.title().strip();
		String subject = subject(e.description());
		String title = company == null || company.isEmpty() ? null
				: subject == null ? company : company + ": " + subject;
		List<SymbolTag> symbols = List.of();
		if (master != null && company != null) {
			symbols = master.symbolForExactName(company).map(s -> List.of(new SymbolTag(s, "exact"))).orElse(List.of());
		}
		String summary = e.description() == null ? null : SUBJECT.matcher(e.description()).replaceFirst("").strip();
		return new RawItem(title, e.link() == null ? null : e.link().strip(), summary, parseDate(e.date()),
				e.date(), null, symbols);
	}

	static String subject(String description) {
		if (description == null) {
			return null;
		}
		Matcher m = SUBJECT.matcher(description);
		if (m.find()) {
			return m.group(1).strip();
		}
		m = REGARDING.matcher(description);
		return m.find() ? m.group(1).strip() : null;
	}
}
