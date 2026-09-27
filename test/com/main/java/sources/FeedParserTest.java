package com.main.java.sources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.main.java.core.NewsException;

class FeedParserTest {

	@TempDir
	Path dir;

	private static byte[] b(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void rss2WithCdataAndNamespacedNoise() {
		List<FeedParser.Entry> e = FeedParser.parse(b("""
				<?xml version="1.0" encoding="UTF-8"?>
				<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
				<channel><title>Mint</title><atom:link href="https://x.example/self" rel="self"/>
				<item>
				  <title><![CDATA[SAIL dividend & record date]]></title>
				  <link><![CDATA[https://www.livemint.com/a.html]]></link>
				  <atom:link href="https://wrong.example/"/>
				  <description><![CDATA[<p>Sixth year</p>]]></description>
				  <pubDate><![CDATA[Sun, 27 Sep 2026 12:19:43 +0530]]></pubDate>
				  <media:content url="https://img.example/x.jpg"><media:title>t</media:title></media:content>
				  <source url="https://pub.example">Publisher</source>
				</item>
				</channel></rss>"""), "t");
		assertEquals(1, e.size());
		FeedParser.Entry x = e.get(0);
		assertEquals("SAIL dividend & record date", x.title());
		assertEquals("https://www.livemint.com/a.html", x.link());
		assertEquals("<p>Sixth year</p>", x.description());
		assertEquals("Sun, 27 Sep 2026 12:19:43 +0530", x.date());
		assertEquals("Publisher", x.sourceName());
	}

	@Test
	void atomPrefersAlternateLinkAndPublished() {
		List<FeedParser.Entry> e = FeedParser.parse(b("""
				<feed xmlns="http://www.w3.org/2005/Atom"><title>f</title>
				<entry><title type="html">A &amp;lt;b&amp;gt;</title>
				  <link rel="self" href="https://self.example/"/>
				  <link rel="alternate" type="text/html" href="https://site.example/post"/>
				  <id>tag:x,2026:1</id><updated>2026-09-27T08:00:00Z</updated><published>2026-09-27T07:00:00Z</published>
				  <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Body</p></div></content>
				</entry></feed>"""), "t");
		assertEquals("https://site.example/post", e.get(0).link());
		assertEquals("2026-09-27T07:00:00Z", e.get(0).date());
		assertEquals("A &lt;b&gt;", e.get(0).title());
		assertTrue(e.get(0).description().contains("Body"));
	}

	@Test
	void rdfUsesDcDate() {
		List<FeedParser.Entry> e = FeedParser.parse(b("""
				<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/"
				  xmlns:dc="http://purl.org/dc/elements/1.1/">
				<channel rdf:about="x"><title>DW</title></channel>
				<item rdf:about="https://dw.example/a"><title>T</title><link>https://dw.example/a</link>
				<dc:date>2026-09-27T06:00:00Z</dc:date></item></rdf:RDF>"""), "t");
		assertEquals("2026-09-27T06:00:00Z", e.get(0).date());
		assertEquals("https://dw.example/a", e.get(0).link());
	}

	@Test
	void htmlPageIsNotAFeed() {
		NewsException ex = assertThrows(NewsException.class,
				() -> FeedParser.parse(b("<html><head><title>Market</title></head><body/></html>"), "Feed fe"));
		assertTrue(ex.getMessage().contains("not an RSS/Atom feed"));
	}

	@Test
	void externalEntitiesAreNeverResolved() throws IOException {
		Path secret = dir.resolve("secret.txt");
		Files.writeString(secret, "TOPSECRET");
		String xxe = "<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY x SYSTEM \"" + secret.toUri() + "\">]>"
				+ "<rss><channel><item><title>&x;</title><link>https://a.example/</link></item></channel></rss>";
		NewsException ex = assertThrows(NewsException.class, () -> FeedParser.parse(b(xxe), "t"));
		assertFalse(ex.getMessage().contains("TOPSECRET"));
	}

	@Test
	void entityExpansionBombIsRejected() {
		String bomb = "<?xml version=\"1.0\"?><!DOCTYPE lolz [<!ENTITY lol \"lol\"><!ENTITY lol2 \"&lol;&lol;&lol;&lol;\">]>"
				+ "<rss><channel><item><title>&lol2;</title></item></channel></rss>";
		assertThrows(NewsException.class, () -> FeedParser.parse(b(bomb), "t"));
	}

	@Test
	void malformedXmlFails() {
		assertThrows(NewsException.class, () -> FeedParser.parse(b("<rss><channel><item>"), "t"));
		assertThrows(NewsException.class, () -> FeedParser.parse(b(""), "t"));
	}
}
