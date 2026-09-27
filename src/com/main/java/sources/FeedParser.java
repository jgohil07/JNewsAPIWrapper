package com.main.java.sources;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.main.java.core.NewsException;

/**
 * Minimal RSS 2.0 / RSS 1.0 (RDF) / Atom reader on StAX. DTDs and external entities are disabled, so XXE and entity
 * expansion attacks fail. A document whose root is not a feed (for example an HTML error page served with HTTP 200)
 * is an error, not an empty feed.
 *
 * @author jgohil
 */
public final class FeedParser {

	/** One entry with raw strings; dates are parsed later with the source's rules. */
	public record Entry(String title, String link, String description, String date, String guid, String sourceName) {}

	private static final XMLInputFactory FACTORY = createFactory();

	private FeedParser() {}

	private static XMLInputFactory createFactory() {
		XMLInputFactory f = XMLInputFactory.newFactory();
		f.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
		f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
		f.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
		f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
		f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
		try {
			f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			f.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		} catch (IllegalArgumentException ignored) {
			// property not supported by this StAX implementation; DTD support is already off
		}
		return f;
	}

	/**
	 * @throws NewsException when the bytes are not well-formed XML or not a feed
	 */
	public static List<Entry> parse(byte[] xml, String what) {
		XMLStreamReader r = null;
		try {
			r = FACTORY.createXMLStreamReader(new ByteArrayInputStream(xml));
			while (r.hasNext() && r.next() != XMLStreamConstants.START_ELEMENT) {
				if (r.getEventType() == XMLStreamConstants.DTD) {
					throw new NewsException(what + " contains a DOCTYPE; refusing to parse");
				}
			}
			if (!r.isStartElement()) {
				throw new NewsException(what + " is empty");
			}
			String root = r.getLocalName().toLowerCase(Locale.ROOT);
			boolean atom = root.equals("feed");
			if (!atom && !root.equals("rss") && !root.equals("rdf")) {
				throw new NewsException(what + " is not an RSS/Atom feed (root element <" + r.getLocalName() + ">)");
			}
			return readEntries(r, atom);
		} catch (XMLStreamException e) {
			throw new NewsException(what + " is not well-formed XML: " + e.getMessage(), e);
		} finally {
			if (r != null) {
				try {
					r.close();
				} catch (XMLStreamException ignored) {
					// nothing to release
				}
			}
		}
	}

	private static List<Entry> readEntries(XMLStreamReader r, boolean atom) throws XMLStreamException {
		List<Entry> entries = new ArrayList<>();
		String entryName = atom ? "entry" : "item";
		while (r.hasNext()) {
			int ev = r.next();
			if (ev == XMLStreamConstants.DTD) {
				throw new XMLStreamException("DOCTYPE not allowed");
			}
			if (ev == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals(entryName)) {
				entries.add(atom ? readAtomEntry(r) : readRssItem(r));
			}
		}
		return entries;
	}

	private static Entry readRssItem(XMLStreamReader r) throws XMLStreamException {
		String title = null, link = null, description = null, content = null, pubDate = null, dcDate = null;
		String guid = null, source = null;
		while (r.hasNext()) {
			int ev = r.next();
			if (ev == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("item")) {
				break;
			}
			if (ev != XMLStreamConstants.START_ELEMENT) {
				continue;
			}
			String ns = r.getNamespaceURI() == null ? "" : r.getNamespaceURI();
			String name = r.getLocalName();
			boolean plainRss = ns.isEmpty() || ns.equals("http://purl.org/rss/1.0/");
			if (plainRss && name.equals("title")) {
				title = text(r);
			} else if (plainRss && name.equals("link")) {
				link = text(r);
			} else if (plainRss && name.equals("description")) {
				description = text(r);
			} else if (plainRss && name.equals("pubDate")) {
				pubDate = text(r);
			} else if (plainRss && name.equals("guid")) {
				guid = text(r);
			} else if (plainRss && name.equals("source")) {
				source = text(r);
			} else if (name.equals("encoded") && ns.startsWith("http://purl.org/rss/1.0/modules/content")) {
				content = text(r);
			} else if (name.equals("date") && ns.startsWith("http://purl.org/dc/elements/1.1")) {
				dcDate = text(r);
			} else {
				skip(r);
			}
		}
		return new Entry(title, link, description != null ? description : content, pubDate != null ? pubDate : dcDate,
				guid, source);
	}

	private static Entry readAtomEntry(XMLStreamReader r) throws XMLStreamException {
		String title = null, link = null, altLink = null, summary = null, content = null, published = null;
		String updated = null, id = null;
		while (r.hasNext()) {
			int ev = r.next();
			if (ev == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("entry")) {
				break;
			}
			if (ev != XMLStreamConstants.START_ELEMENT) {
				continue;
			}
			switch (r.getLocalName()) {
			case "title" -> title = text(r);
			case "link" -> {
				String rel = r.getAttributeValue(null, "rel");
				String href = r.getAttributeValue(null, "href");
				if (rel == null || rel.equals("alternate")) {
					if (altLink == null) {
						altLink = href;
					}
				} else if (link == null && !rel.equals("self") && !rel.equals("enclosure")) {
					link = href;
				}
				skip(r);
			}
			case "summary" -> summary = text(r);
			case "content" -> content = text(r);
			case "published" -> published = text(r);
			case "updated" -> updated = text(r);
			case "id" -> id = text(r);
			default -> skip(r);
			}
		}
		return new Entry(title, altLink != null ? altLink : link, summary != null ? summary : content,
				published != null ? published : updated, id, null);
	}

	/** All character data inside the current element, including nested elements (e.g. Atom xhtml content). */
	private static String text(XMLStreamReader r) throws XMLStreamException {
		StringBuilder sb = new StringBuilder();
		int depth = 1;
		while (r.hasNext() && depth > 0) {
			int ev = r.next();
			switch (ev) {
			case XMLStreamConstants.START_ELEMENT -> {
				depth++;
				sb.append(' ');
			}
			case XMLStreamConstants.END_ELEMENT -> depth--;
			case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> sb.append(r.getText());
			case XMLStreamConstants.ENTITY_REFERENCE -> throw new XMLStreamException(
					"Undeclared entity &" + r.getLocalName() + "; not allowed");
			default -> {
				// comments and processing instructions are ignored
			}
			}
		}
		return sb.toString().strip();
	}

	private static void skip(XMLStreamReader r) throws XMLStreamException {
		int depth = 1;
		while (r.hasNext() && depth > 0) {
			int ev = r.next();
			if (ev == XMLStreamConstants.START_ELEMENT) {
				depth++;
			} else if (ev == XMLStreamConstants.END_ELEMENT) {
				depth--;
			}
		}
	}
}
