package com.main.java.core;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts feed HTML fragments to plain text. Output is plain text only; rendering code must still escape it.
 *
 * @author jgohil
 */
public final class Text {

	private static final Pattern DROP_BLOCKS = Pattern.compile("(?is)<(script|style|noscript|iframe)\\b.*?</\\1\\s*>");
	private static final Pattern COMMENTS = Pattern.compile("(?s)<!--.*?-->");
	private static final Pattern BREAKS = Pattern.compile("(?i)<\\s*(br|/p|/div|/li|/h[1-6])\\b[^>]*>");
	/** Only real tags: a name right after {@code <} or {@code </}, so text such as "a < b > c" survives. */
	private static final Pattern TAGS = Pattern.compile("(?s)<\\s*/?\\s*[a-zA-Z][a-zA-Z0-9:-]*(\\s[^<>]*)?/?\\s*>|<!\\[CDATA\\[|\\]\\]>");
	private static final Pattern ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,10});");
	private static final Pattern SPACE = Pattern.compile("[\\s\\u00A0\\u200B]+");
	private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

	private static final Map<String, String> NAMED = Map.ofEntries(Map.entry("amp", "&"), Map.entry("lt", "<"),
			Map.entry("gt", ">"), Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("nbsp", " "),
			Map.entry("ndash", "–"), Map.entry("mdash", "—"), Map.entry("lsquo", "‘"),
			Map.entry("rsquo", "’"), Map.entry("ldquo", "“"), Map.entry("rdquo", "”"),
			Map.entry("hellip", "…"), Map.entry("rupee", "₹"), Map.entry("euro", "€"),
			Map.entry("pound", "£"), Map.entry("copy", "©"), Map.entry("reg", "®"),
			Map.entry("trade", "™"), Map.entry("middot", "·"), Map.entry("bull", "•"),
			Map.entry("deg", "°"), Map.entry("times", "×"), Map.entry("laquo", "«"),
			Map.entry("raquo", "»"));

	private Text() {}

	/** Strips tags and comments, decodes entities (twice, for double-escaped feeds) and collapses whitespace. */
	public static String plain(String html) {
		if (html == null) {
			return null;
		}
		String s = COMMENTS.matcher(html).replaceAll(" ");
		s = DROP_BLOCKS.matcher(s).replaceAll(" ");
		s = BREAKS.matcher(s).replaceAll(" ");
		s = TAGS.matcher(s).replaceAll(" ");
		s = decode(decode(s));
		// Decoding can reveal escaped markup such as &lt;p&gt;; strip it too.
		s = TAGS.matcher(s).replaceAll(" ");
		s = CONTROL.matcher(s).replaceAll(" ");
		return SPACE.matcher(s).replaceAll(" ").strip();
	}

	/** {@link #plain(String)} cut to at most {@code max} characters at a word boundary, with an ellipsis. */
	public static String plain(String html, int max) {
		String s = plain(html);
		if (s == null || s.length() <= max) {
			return s;
		}
		int cut = s.lastIndexOf(' ', max - 1);
		if (cut < max / 2) {
			cut = max - 1;
		}
		return s.substring(0, cut).stripTrailing() + "…";
	}

	static String decode(String s) {
		if (s.indexOf('&') < 0) {
			return s;
		}
		Matcher m = ENTITY.matcher(s);
		StringBuilder out = new StringBuilder(s.length());
		while (m.find()) {
			m.appendReplacement(out, Matcher.quoteReplacement(entity(m.group(1), m.group())));
		}
		m.appendTail(out);
		return out.toString();
	}

	private static String entity(String body, String whole) {
		try {
			if (body.startsWith("#x") || body.startsWith("#X")) {
				return codePoint(Integer.parseInt(body.substring(2), 16), whole);
			}
			if (body.startsWith("#")) {
				return codePoint(Integer.parseInt(body.substring(1)), whole);
			}
		} catch (NumberFormatException e) {
			return whole;
		}
		String named = NAMED.get(body.toLowerCase(Locale.ROOT));
		return named == null ? whole : named;
	}

	private static String codePoint(int cp, String whole) {
		if (cp <= 0 || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
			return whole;
		}
		return new String(Character.toChars(cp));
	}
}
