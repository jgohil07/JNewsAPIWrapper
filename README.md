# JNewsAPIWrapper 
[![forthebadge](http://forthebadge.com/images/badges/check-it-out.svg)](http://forthebadge.com)
[![forthebadge](http://forthebadge.com/images/badges/built-with-love.svg)](http://forthebadge.com)
[![forthebadge](http://forthebadge.com/images/badges/kinda-sfw.svg)](http://forthebadge.com)

JNewsAPIWrapper started as a Java wrapper for [News API](https://newsapi.org/). Version 2 keeps that wrapper (fixed)
and adds:

- **80+ sources with health checks and fallbacks** in eight top-level categories:

  | Category | Sources |
  |---|---|
  | India | Economic Times, Mint, The Hindu, Indian Express, NDTV Profit |
  | Finance | India Markets, India Companies & Banking, India Economy & Policy (RBI included), NSE Filings, Global Markets & Business |
  | Global | BBC, The Guardian, Al Jazeera, NPR, DW |
  | Tech | Ars Technica, The Verge, Hacker News, Lobsters, and others |
  | Science | BBC, The Guardian, NPR, NASA, ScienceDaily, The Hindu |
  | Health | BBC, The Guardian, NPR, The Hindu, ScienceDaily, ETHealthworld |
  | Entertainment | BBC, The Guardian, Variety, The Hollywood Reporter, The Hindu, ET Panache, NPR |
  | Sports | BBC Sport, The Guardian, ESPNcricinfo, The Hindu, Mint |

  Google News, News API v2, GNews and MarketAux serve as command-line fallbacks.
- **A command-line India finance feed** for other programs, such as Thesis-Engine, with a versioned JSON schema:
  markets, companies & banking, economy & policy, and NSE filings, optionally for a single NSE symbol.
- **An optional static website**: a minimal "wire" of headlines with a source-health view. It is built by the same
  jar and deployed to GitHub Pages every 6 hours.

**Fail loud, never wrong.** Every source is validated on every run. A source that cannot be fetched, is not a feed,
has more than 20% invalid items, or whose newest item is older than its limit contributes nothing, and the output
says so. When a required category has too few healthy sources, the CLI writes no data and exits non-zero, and the
site keeps its previous deployment and shows how old it is.

## Getting Started

Requirements: Java 21. Maven is fetched by the wrapper (checksum-pinned).

```bash
git clone https://github.com/jgohil07/JNewsAPIWrapper.git
cd JNewsAPIWrapper
./mvnw -B package              # builds and tests; produces target/jnews.jar
java -jar target/jnews.jar --help
```

Eclipse and IntelliJ can import the project as a Maven project (`pom.xml`). Sources stay in `src/`, tests in `test/`.

### Settings and API keys

Keys are **never** stored in code. Copy `.env.example` to `.env` (git-ignored) and fill in what you use. Real
environment variables take precedence.

| Setting | Used by |
|---|---|
| `NEWSAPI_KEY` | The original v1 wrapper classes, and the News API v2 CLI fallback |
| `GNEWS_KEY`, `MARKETAUX_KEY` | Optional CLI fallbacks |

Everything else, including the whole website, runs without keys. The free plans of News API and GNews allow only
development and non-published use, so keyed sources are never used for the public site.

## Command line

```bash
java -jar target/jnews.jar sources                  # categories and sources
java -jar target/jnews.jar sources --check          # fetch and validate every source now
java -jar target/jnews.jar fetch --category tech --since 12h --format table
java -jar target/jnews.jar india --since 2d         # India finance, JSON envelope
java -jar target/jnews.jar india --symbol MBAPL --since 7d
java -jar target/jnews.jar india --topic ipo,rbi --format jsonl
java -jar target/jnews.jar site build --out _site && java -jar target/jnews.jar site serve --dir _site
```

Exit codes: `0` ok, `3` partial (data written, some sources unhealthy), `1` failed (no data written), `2` usage or
configuration error. The full contract for programs, including fields, guarantees and known gaps, is in
[docs/cli-contract.md](docs/cli-contract.md) and [schema/news-envelope.v1.json](schema/news-envelope.v1.json).

### Thesis-Engine

```bash
tmp=$(mktemp) && java -jar /path/to/jnews.jar india --symbol "$SYMBOL" --since 2d --format json > "$tmp"
case $? in 0|3) mv "$tmp" news.json ;; *) rm -f "$tmp"; echo "news unavailable" >&2 ;; esac
```

A failed run (exit 1 or 2) never overwrites the last good `news.json`.

NSE's own announcements carry `symbols[].match = "exact"`. Headlines that name the company carry `"name"`, which is
lower confidence.

## Website (GitHub Pages)

`.github/workflows/site.yml` runs every 6 hours (at minute 23), on manual dispatch, and on pushes to `master`. It
builds and tests the jar, runs `jnews site build` on public sources only, and deploys the result to GitHub Pages. If
the build fails, nothing is deployed and the previous site stays live. The page shows when it was generated and
turns amber after 8 hours and red after 24 hours.

One-time setup: *Settings → Pages → Source: GitHub Actions*.

GitHub disables scheduled workflows in public repositories after 60 days without activity.
`.github/workflows/keepalive.yml` therefore commits a one-line heartbeat (`.github/heartbeat`) on the 1st of each
month. This relies on a bot commit counting as activity, which third-party guides report but GitHub's docs do not
state. If the refresh ever stops, re-enable the workflow under *Actions*.

The page's keyboard shortcuts:

| Key | Action |
|---|---|
| `j` / `k` | Next / previous headline |
| `o` | Open the selected headline (an NSE XBRL-only filing opens NSE's announcements page; its "XBRL" tag opens the data file) |
| `c` | Show other outlets covering it |
| `m` | Mark read or unread |
| `/` | Search |
| `0`–`8` | Switch top-level category (Finance's sub-categories stay collapsed until you expand them) |
| `[` / `]` | Previous / next sub-category |
| `e` | Expand or collapse sub-categories |
| `h` | Source health |
| `t` | Theme |
| `d` | Density |
| `?` | Help |

## Original News API v1 wrapper

The classes in `com.main.java.aggreators`, `models`, `base`, `utils` and `startup` keep their public signatures; a
test locks them. Behaviour changes in 2.0:

- The API key comes from `NEWSAPI_KEY` (environment or `.env`), is sent in the `X-Api-Key` header, and a missing key
  raises `ConfigException`.
- Methods throw instead of returning `null`:

  | Exception | When |
  |---|---|
  | `FetchException` | Network or HTTP errors. HTTP 403 is no longer read as success. |
  | `NewsApiException` | An API error, or a response that does not match the request |
  | `IllegalArgumentException` | Unsupported filter values |

- The JSON fields `source`, `sortBy`, `category`, `language` and `country` are now filled; they were always `null`.
- Defaults and filters are sent as real, URL-encoded parameters; the defaults used to be ignored.
- Supported values match what v1 served on 2026-09-27: categories `business, entertainment, general, science, sports,
  technology`, languages `en, de`, countries `au, de, gb, in, it, us`, and sortBy `top`. v1 silently returns an empty
  list for unknown categories, and `top` data when asked for `latest`, so those requests are now rejected.
- Requests have timeouts (10 s to connect, 20 s for headers and again for the body) and a 5 MB response cap. GET
  requests are retried up to twice on HTTP 429/5xx or network errors, with a backoff of up to 30 s; POST/PUT/DELETE
  are never retried. Redirects are followed for GET only, never from https to http, and never to private or loopback
  addresses.
- `JsonFactory` is deprecated; use `HttpFetcher` and Jackson. Its header name and value arrays must now have the
  same length (otherwise `IllegalArgumentException`), and `parameters` is now applied to POST and PUT too.

Sample run: `java -cp target/jnews.jar com.main.java.startup.InitializeNewsWrapper` (needs `NEWSAPI_KEY`).

## Support

Please feel free to open a issue if your are having trouble, I am willing to help anyone with any issue that relates to JNewsAPIWrapper.
