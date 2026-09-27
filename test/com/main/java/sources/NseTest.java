package com.main.java.sources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.main.java.core.HttpFetcher;
import com.main.java.core.NewsException;
import com.main.java.core.NewsItem.SymbolTag;
import com.main.java.testutil.FixtureServer;
import com.main.java.testutil.FixtureServer.Reply;

class NseTest {

	static String equityList() {
		StringBuilder sb = new StringBuilder("SYMBOL,NAME OF COMPANY, SERIES, DATE OF LISTING, PAID UP VALUE, MARKET LOT, ISIN NUMBER, FACE VALUE\n");
		sb.append("MBAPL,Madhya Bharat Agro Products Limited,EQ,01-JAN-2020,10,1,INE900L01010,10\n");
		sb.append("KPIGREEN,KPI Green Energy Limited,EQ,01-JAN-2020,5,1,INE542W01025,5\n");
		sb.append("DUPA,\"Same Name, Limited\",EQ,01-JAN-2020,1,1,INE000A00001,1\n");
		sb.append("DUPB,\"Same Name, Limited\",BE,01-JAN-2020,1,1,INE000A00002,1\n");
		for (int i = 0; i < 600; i++) {
			sb.append("SYM").append(i).append(",Company ").append(i).append(" Limited,EQ,01-JAN-2020,1,1,INE").append(i).append(",1\n");
		}
		return sb.toString();
	}

	static SourceConfig config(String type, String url) {
		return new SourceConfig("nse-announcements", "NSE", type, url, null, "in", "filing", "en", List.of("filings"),
				List.of("filings"), 96, true, "browser", "Asia/Kolkata", "dd-MMM-yyyy HH:mm:ss", false, 5, null);
	}

	@Test
	void symbolMasterMapsExactUniqueNamesOnly() {
		NseSymbolMaster m = NseSymbolMaster.parse(equityList());
		assertEquals("KPIGREEN", m.symbolForExactName("KPI Green Energy Limited").orElseThrow());
		assertEquals("KPIGREEN", m.symbolForExactName("kpi green energy limited.").orElseThrow());
		assertTrue(m.symbolForExactName("KPI Green").isEmpty(), "partial names never match");
		assertTrue(m.symbolForExactName("Same Name, Limited").isEmpty(), "ambiguous names never match");
		assertEquals("INE900L01010", m.company("mbapl").orElseThrow().isin());
	}

	@Test
	void symbolMasterRejectsWrongHeaderOrTruncatedList() {
		assertThrows(NewsException.class, () -> NseSymbolMaster.parse("<html>blocked</html>"));
		assertThrows(NewsException.class, () -> NseSymbolMaster.parse(equityList().lines().limit(50)
				.reduce("", (a, b) -> a + b + "\n")));
	}

	@Test
	void announcementsFeedBuildsTitleDateAndSymbol() throws IOException {
		try (FixtureServer s = new FixtureServer()) {
			s.on("/rss", Reply.xml("""
					<rss version="2.0"><channel><title>NSE</title>
					<item><title>KPI Green Energy Limited</title>
					<link>https://nsearchives.nseindia.com/corporate/xbrl/a.xml</link>
					<description>KPI Green Energy Limited has informed the Exchange regarding Update-Acquisition |SUBJECT: Update-Acquisition (including agreement to acquire)</description>
					<pubDate>27-Sep-2026 12:39:15</pubDate></item>
					<item><title>Unknown Co Ltd</title><link>https://nsearchives.nseindia.com/corporate/b.pdf</link>
					<description>Unknown Co Ltd has informed the Exchange regarding Trading Window</description>
					<pubDate>27-Sep-2026 12:00:00</pubDate></item>
					</channel></rss>"""));
			NseSymbolMaster master = NseSymbolMaster.parse(equityList());
			NseAnnouncementsSource src = new NseAnnouncementsSource(config("nse-rss", s.url("/rss")),
					HttpFetcher.defaults(), () -> master);
			List<RawItem> items = src.fetch();
			assertEquals("KPI Green Energy Limited: Update-Acquisition (including agreement to acquire)", items.get(0).title());
			assertEquals(Instant.parse("2026-09-27T07:09:15Z"), items.get(0).published());
			assertEquals(List.of(new SymbolTag("KPIGREEN", "exact")), items.get(0).symbols());
			assertEquals("Unknown Co Ltd: Trading Window", items.get(1).title());
			assertTrue(items.get(1).symbols().isEmpty());
			assertTrue(s.lastRequest().headers().getFirst("User-Agent").startsWith("Mozilla/5.0"));
		}
	}

	@Test
	void missingSymbolListIsANoteNotAFailure() throws IOException {
		try (FixtureServer s = new FixtureServer()) {
			s.on("/rss", Reply.xml("<rss><channel></channel></rss>"));
			NseAnnouncementsSource src = new NseAnnouncementsSource(config("nse-rss", s.url("/rss")), HttpFetcher.defaults(), () -> null);
			src.fetch();
			assertTrue(src.note().contains("symbol list unavailable"));
		}
	}

	@Test
	void perSymbolApiRejectsForeignRecords() throws IOException {
		try (FixtureServer s = new FixtureServer()) {
			s.on("/api", Reply.json("""
					[{"symbol":"MBAPL","sm_name":"Madhya Bharat Agro Products Limited","desc":"Trading Window",
					  "an_dt":"24-Sep-2026 09:58:34","attchmntFile":"https://nsearchives.nseindia.com/corporate/x.pdf",
					  "attchmntText":"has informed the Exchange"}]"""));
			NseSymbolAnnouncementsSource ok = new NseSymbolAnnouncementsSource(config("nse-api", s.url("/api?symbol=")),
					HttpFetcher.defaults(), "mbapl");
			RawItem item = ok.fetch().get(0);
			assertEquals("Madhya Bharat Agro Products Limited: Trading Window", item.title());
			assertEquals(Instant.parse("2026-09-24T04:28:34Z"), item.published());
			assertEquals("MBAPL", s.lastRequest().query().replace("symbol=", ""));

			NseSymbolAnnouncementsSource other = new NseSymbolAnnouncementsSource(config("nse-api", s.url("/api?symbol=")),
					HttpFetcher.defaults(), "TCS");
			assertThrows(NewsException.class, other::fetch);
		}
	}
}
