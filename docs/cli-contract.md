# `jnews` CLI contract (schema version 1)

This is the interface other programs (for example Thesis-Engine) can rely on. It changes only additively within
schema version 1; anything else gets a new `schema_version` and a new schema file.

## Build and run

```bash
./mvnw -B package            # builds target/jnews.jar (Java 21)
java -jar target/jnews.jar --help
```

Settings come from environment variables, then `.env` in the working directory (or `--env-file PATH`). Only the
optional keyed fallbacks need settings: `NEWSAPI_KEY`, `GNEWS_KEY`, `MARKETAUX_KEY` (see `.env.example`).

## Commands for ingestion

| Command | What it returns |
|---|---|
| `jnews india [--since 2d] [--topic ipo,rbi,...]` | India finance: categories `india-markets`, `india-business`, `economy`, `filings` |
| `jnews india --symbol MBAPL [--since 7d]` | One NSE symbol: NSE announcements (`match: exact`) + headlines naming the company (`match: name`) |
| `jnews fetch [--category a,b] [--since 24h] [--public-only]` | Any categories (`jnews sources` lists them) |

Common options: `--format json|jsonl|table` (default `json`), `--since` (`90m`, `24h`, `2d`, `1w`; 1 minute to 31 days;
default 2 days for `india`, everything for `fetch`), `--limit N`, `--strict`, `--allow-partial`, `--timeout 1m`,
`-q`. Topic ids: `jnews sources --topics`.

Recommended for Thesis-Engine (2-day news TTL):

```bash
java -jar jnews.jar india --symbol "$SYMBOL" --since 2d --format json > news.json
```

## Exit codes and streams

| Exit | Meaning | stdout |
|---|---|---|
| 0 | Every queried source healthy | the data |
| 3 | Partial: some sources failed or are stale, but every required category has enough healthy sources | the data (unhealthy sources listed in `sources`) |
| 1 | Failed: a required category is below its minimum, `--strict` with a partial result, or a fatal error | **empty** |
| 2 | Usage or configuration error (bad option, unknown category, invalid or unlisted symbol, missing `--env-file`) | **empty** |

`--allow-partial` turns 3 into 0; `--strict` turns 3 into 1. A one-line summary and every unhealthy source always go to
**stderr**. Consumers should check the exit code before reading stdout; a document on stdout is never a failed run.

## Output (`--format json`)

One JSON document matching [`schema/news-envelope.v1.json`](../schema/news-envelope.v1.json) (JSON Schema 2020-12):

```jsonc
{
  "schema_version": 1,
  "generator": "JNewsAPIWrapper 2.0",
  "generated_at": "2026-09-27T08:06:14Z",          // UTC, ISO-8601
  "query": { "command": "india", "symbol": "MBAPL", "company": "...", "isin": "...", "since_hours": 48.0, ... },
  "status": "ok",                                  // or "partial"
  "categories": [ { "id": "filings", "healthy": 1, "min_healthy": 1, "required": true, "ok": true, ... } ],
  "sources": [ { "id": "nse-symbol", "status": "ok|stale|failed|skipped", "items": 12, "rejected": 0,
                 "newest_at": "...", "error": null, "note": "...", ... } ],
  "items": [ {
      "id": "<sha256 of canonical URL>",
      "title": "Madhya Bharat Agro Products Limited: Trading Window",
      "url": "https://nsearchives.nseindia.com/corporate/....pdf",
      "source": { "id": "nse-symbol", "name": "NSE · Announcements for MBAPL" },
      "published_at": "2026-09-24T04:28:34Z",
      "fetched_at": "2026-09-27T08:06:13Z",
      "summary": "plain text, at most 500 characters, or null",
      "kind": "news | filing | policy",
      "region": "in | world",
      "language": "en",
      "categories": ["filings"],
      "topics": [ { "id": "filings", "by": "feed" }, { "id": "rbi", "by": "rule" } ],
      "symbols": [ { "symbol": "MBAPL", "match": "exact" } ],
      "also_covered_by": [ { "source_id": "...", "source_name": "...", "url": "...", "published_at": "..." } ]
  } ]
}
```

`--format jsonl` writes one item object per line and nothing else (the health summary stays on stderr).

## What the fields guarantee

- **Items are validated**: non-empty title, absolute http(s) URL, a publication time that was read unambiguously
  (dates without a zone are accepted only from sources whose zone is configured), not more than 15 minutes in the
  future. Invalid items are dropped and counted in `sources[].rejected`; a source with more than 20% invalid items, no
  items, or a newest item older than its `max_age_hours` contributes **no** items.
- **`topics[].by`**: `feed` = the publisher's own section (authoritative); `rule` = keyword rule
  (`resources/taxonomy.json`), a hint.
- **`symbols[].match`**: `exact` = the exchange attached the symbol (NSE announcements, or an exact, unambiguous
  company-name match in NSE's equity list); `name` = the headline or summary names the company (whole phrase, case as
  registered or all capitals, not inside a longer proper noun). Treat `name` as lower confidence.
- **Duplicates** are merged: same canonical URL, or the same headline from another source within 36 hours. The
  preferred source's copy is kept; the others are in `also_covered_by`.
- **Sorted** newest first.

## Known gaps (reported, never hidden)

- NSE lists some notices without a document (daily ETF NAV declarations; per-symbol "News Verification" entries
  without an attachment). They have no URL of their own, so they are skipped and counted in `sources[].note`.
- Headlines that name a company only by its ticker or a nickname (e.g. "TCS") are not matched by `--symbol`; NSE's
  own announcements for the symbol are always included.
- Keyed fallbacks run only when a category has too few healthy primary sources, and only when their key is set;
  otherwise they are listed as `skipped`. News API's free plan delays articles by 24 hours.
- Google News search results (used by `--symbol` and as a CLI fallback) are for local use only and never published
  on the site.
