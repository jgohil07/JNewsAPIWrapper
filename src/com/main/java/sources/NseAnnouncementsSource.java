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
		// NSE lists daily fund NAV declarations with an empty <link/> by design. Those are left out and counted here.
		// Any other item without a link stays in, so validation counts it as invalid and a feed that stops linking its
		// filings fails loudly instead of shrinking silently.
		List<RawItem> all = super.fetch();
		List<RawItem> kept = all.stream().filter(i -> hasLink(i) || !isNavNotice(i)).toList();
		if (kept.size() < all.size()) {
			notes.add((all.size() - kept.size()) + " link-less NAV notice(s) skipped");
		}
		note = notes.isEmpty() ? null : String.join("; ", notes);
		return kept;
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
		String link = e.link() == null || e.link().isBlank() ? null : e.link().strip();
		return new RawItem(title, link, summary, parseDate(e.date()),
				e.date(), null, symbols);
	}

	private static boolean hasLink(RawItem i) {
		return i.url() != null && !i.url().isBlank();
	}

	/** Routine fund NAV declaration (NSE's wording: "... Net Asset Value ... |SUBJECT: Declaration of NAV"). */
	static boolean isNavNotice(RawItem i) {
		String d = i.summaryHtml() == null ? "" : i.summaryHtml();
		String t = i.title() == null ? "" : i.title();
		return t.endsWith(": Declaration of NAV") || d.contains("Net Asset Value");
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
