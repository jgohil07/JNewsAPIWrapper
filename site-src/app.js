/* jnews wire — static client. Renders data/latest.json; builds DOM with textContent only (no innerHTML). */
'use strict';
(() => {
  const $ = (id) => document.getElementById(id);
  const PAGE = 150;
  const NSE_FILINGS_PAGE = 'https://www.nseindia.com/companies-listing/corporate-filings-announcements';

  // ---------- small helpers ----------
  const store = {
    get(key, fallback) {
      try { const v = localStorage.getItem('jnews.' + key); return v === null ? fallback : JSON.parse(v); } catch { return fallback; }
    },
    set(key, value) {
      try { localStorage.setItem('jnews.' + key, JSON.stringify(value)); } catch { /* storage unavailable: keep in memory */ }
    }
  };

  function el(tag, props, ...children) {
    const node = document.createElement(tag);
    if (props) {
      for (const [k, v] of Object.entries(props)) {
        if (v === undefined || v === null || v === false) continue;
        if (k === 'class') node.className = v;
        else if (k === 'text') node.textContent = v;
        else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
        else node.setAttribute(k, v === true ? '' : String(v));
      }
    }
    for (const c of children.flat()) {
      if (c === null || c === undefined || c === false) continue;
      node.append(c instanceof Node ? c : document.createTextNode(String(c)));
    }
    return node;
  }

  /** Only absolute http(s) URLs ever become links. */
  function safeUrl(u) {
    if (typeof u !== 'string' || !/^https?:\/\//i.test(u)) return null;
    try { const p = new URL(u); return (p.protocol === 'http:' || p.protocol === 'https:') ? p.href : null; } catch { return null; }
  }

  /** NSE's machine-readable XBRL data files: never the headline's target, only a secondary "XBRL" link. */
  function isXbrl(u) { return /\/corporate\/xbrl\/|\.xml(\?|$)/i.test(u || ''); }

  function rel(ms) {
    if (!Number.isFinite(ms)) return '?';
    if (ms < 60e3) return 'now';
    const m = Math.floor(ms / 60e3);
    if (m < 60) return m + 'm';
    const h = Math.floor(m / 60);
    if (h < 48) return h + 'h';
    return Math.floor(h / 24) + 'd';
  }

  const fmtTime = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit', hour12: false });
  const fmtDay = new Intl.DateTimeFormat(undefined, { day: '2-digit', month: 'short' });
  const fmtDayLong = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
  const fmtFull = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });
  const dayKey = (d) => d.getFullYear() + '-' + d.getMonth() + '-' + d.getDate();

  function dayLabel(d, now) {
    const y = new Date(now); y.setDate(now.getDate() - 1);
    if (dayKey(d) === dayKey(now)) return 'Today';
    if (dayKey(d) === dayKey(y)) return 'Yesterday';
    return fmtDayLong.format(d);
  }

  // ---------- state ----------
  const previousVisit = store.get('lastVisit', null);
  const state = {
    data: null, meta: null, cats: new Map(), tops: [], cat: 'all', windowH: store.get('window', 0),
    sort: store.get('sort', 'time'), source: '', topic: '', q: '', sel: -1, view: 'list', limit: PAGE,
    compact: store.get('compact', false), read: new Set(store.get('read', [])), open: new Set(),
    expanded: new Set(), shown: []
  };

  // ---------- theme & density ----------
  function applyTheme(t) {
    if (t === 'light' || t === 'dark') document.documentElement.setAttribute('data-theme', t);
    else document.documentElement.removeAttribute('data-theme');
  }
  function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme')
      || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    const next = current === 'dark' ? 'light' : 'dark';
    applyTheme(next); store.set('theme', next);
  }
  function applyDensity() {
    document.body.classList.toggle('compact', state.compact);
    $('density').textContent = state.compact ? 'comfortable' : 'compact';
  }
  applyTheme(store.get('theme', null));

  // ---------- loading ----------
  async function getJson(path) {
    const r = await fetch(path, { cache: 'no-cache' });
    if (!r.ok) throw new Error(path + ': HTTP ' + r.status);
    return r.json();
  }

  async function load() {
    try {
      const [data, meta] = await Promise.all([getJson('data/latest.json'), getJson('data/site.json')]);
      if (data.schema_version !== 1 || !Array.isArray(data.items) || !Array.isArray(data.sources) || !Array.isArray(meta.categories)) {
        throw new Error('data/latest.json has an unexpected format (schema_version ' + data.schema_version + ')');
      }
      state.data = data;
      state.meta = meta;
      for (const c of meta.categories) state.cats.set(c.id, { ...c, children: Array.isArray(c.children) ? c.children : [] });
      state.tops = meta.categories.filter((c) => !c.parent).map((c) => c.id);
      for (const i of data.items) i._t = Date.parse(i.published_at);
      store.set('lastVisit', Date.now());
      fromHash();
      render();
      freshness();
      setInterval(freshness, 60e3);
    } catch (e) {
      showBanner('bad', 'Could not load the headlines: ' + e.message + '. Nothing is shown rather than old or partial data.');
      $('freshness').textContent = 'no data';
      $('freshness').className = 'pill bad';
      $('empty').hidden = false;
      $('empty').textContent = 'No data.';
    }
  }

  // ---------- freshness & banner ----------
  function showBanner(kind, text) {
    const b = $('banner');
    b.replaceChildren(el('div', { class: kind }, text));
    b.hidden = false;
  }

  function freshness() {
    const d = state.data, m = state.meta;
    if (!d || !m) return;
    const age = Date.now() - Date.parse(d.generated_at);
    const next = Date.parse(m.next_refresh_at) - Date.now();
    const pill = $('freshness');
    pill.textContent = 'updated ' + (age < 60e3 ? 'just now' : rel(age) + ' ago') + (next > 0 ? ' · next ~' + rel(next) : '');
    pill.title = 'Generated ' + fmtFull.format(new Date(d.generated_at)) + '. Refresh planned every ' + m.refresh_hours + ' h.';
    const h = age / 36e5;
    if (h > m.very_stale_after_hours) {
      pill.className = 'pill bad';
      showBanner('bad', 'These headlines are ' + rel(age) + ' old. The scheduled refresh is failing or disabled; do not rely on this page for current news.');
    } else if (h > m.stale_after_hours) {
      pill.className = 'pill warn';
      showBanner('warn', 'Headlines are ' + rel(age) + ' old — the last scheduled refresh did not publish.');
    } else {
      pill.className = 'pill';
      $('banner').hidden = true;
    }
  }

  // ---------- categories ----------
  const cat = (id) => state.cats.get(id);
  const parentOf = (id) => { const c = cat(id); return c && c.parent ? c.parent : null; };
  const topOf = (id) => parentOf(id) || id;
  function leavesOf(id) {
    const c = cat(id);
    return c && c.children.length ? c.children : [id];
  }

  // ---------- routing ----------
  function fromHash() {
    const h = decodeURIComponent(location.hash.slice(1));
    if (h === 'health') { state.view = 'health'; return; }
    state.view = 'list';
    state.cat = state.cats.has(h) ? h : 'all';
    const p = parentOf(state.cat);
    if (p) state.expanded.add(p); // show where a deep link points; otherwise sub-categories stay collapsed
  }
  function go(id) {
    state.cat = id; state.view = 'list'; state.topic = ''; state.source = ''; state.sel = -1; state.limit = PAGE;
    history.replaceState(null, '', id === 'all' ? location.pathname + location.search : '#' + encodeURIComponent(id));
    render();
    window.scrollTo({ top: 0 });
    $('list').focus({ preventScroll: true });
  }
  function showHealth(on) {
    state.view = on ? 'health' : 'list';
    history.replaceState(null, '', on ? '#health' : (state.cat === 'all' ? location.pathname + location.search : '#' + state.cat));
    render();
    window.scrollTo({ top: 0 });
  }
  function toggleExpanded(id) {
    if (state.expanded.has(id)) state.expanded.delete(id); else state.expanded.add(id);
    renderRail();
  }

  // ---------- filtering ----------
  function inWindow(i) { return !state.windowH || Date.now() - i._t <= state.windowH * 36e5; }
  function inCat(i, id) {
    if (id === 'all') return true;
    const leaves = leavesOf(id);
    return i.categories.some((c) => leaves.includes(c));
  }
  function matchesQuery(i) {
    if (!state.q) return true;
    const hay = (i.title + ' ' + (i.summary || '') + ' ' + i.source.name + ' ' + i.symbols.map((s) => s.symbol).join(' ')).toLowerCase();
    return state.q.split(/\s+/).every((w) => hay.includes(w));
  }
  const base = () => state.data.items.filter((i) => inCat(i, state.cat) && inWindow(i) && matchesQuery(i));
  function filtered() {
    let items = base();
    if (state.source) items = items.filter((i) => i.source.id === state.source);
    if (state.topic) items = items.filter((i) => i.topics.some((t) => t.id === state.topic));
    if (state.sort === 'coverage') {
      items = items.slice().sort((a, b) => (b.also_covered_by.length - a.also_covered_by.length) || (b._t - a._t));
    }
    return items;
  }

  // ---------- category health ----------
  function leafHealth(id) {
    const c = state.data.categories.find((x) => x.id === id);
    if (!c || !c.ok) return 'bad';
    const unhealthy = state.data.sources.some((s) => s.categories.includes(id) && s.status !== 'ok' && s.status !== 'skipped');
    return unhealthy ? 'warn' : '';
  }
  function catHealth(id) {
    const hs = leavesOf(id).map(leafHealth);
    return hs.includes('bad') ? 'bad' : hs.includes('warn') ? 'warn' : '';
  }

  // ---------- render ----------
  function render() {
    applyDensity();
    renderRail();
    const health = state.view === 'health';
    $('list-view').hidden = health;
    $('health-view').hidden = !health;
    document.querySelector('.toolbar').hidden = health;
    $('topics').hidden = health;
    if (health) renderHealth(); else renderList();
  }

  function renderRail() {
    const counts = new Map();
    const windowed = state.data.items.filter(inWindow);
    for (const i of windowed) for (const c of i.categories) counts.set(c, (counts.get(c) || 0) + 1);
    const countOf = (id) => {
      const leaves = leavesOf(id);
      return windowed.filter((i) => i.categories.some((c) => leaves.includes(c))).length;
    };
    // Every row reserves the same caret column on the right (a spacer when there is nothing to expand), so all
    // counts line up in one column.
    const spacer = () => el('span', { class: 'chev-space', 'aria-hidden': 'true' });
    const nodes = [el('li', null, el('div', { class: 'rail-row' }, railButton('all', 'All', windowed.length, '', '0', false), spacer()))];
    state.tops.forEach((id, idx) => {
      const c = cat(id);
      const kids = c.children;
      const open = state.expanded.has(id);
      const li = el('li', { class: kids.length ? 'has-kids' : null },
        el('div', { class: 'rail-row' },
          railButton(id, c.label, countOf(id), catHealth(id), idx < 9 ? String(idx + 1) : '', false),
          kids.length ? el('button', {
            class: 'chev', type: 'button', 'aria-expanded': String(open), 'aria-controls': 'sub-' + id,
            'aria-label': (open ? 'Collapse ' : 'Expand ') + c.label, title: (open ? 'Collapse' : 'Expand') + ' (e)',
            onclick: () => toggleExpanded(id)
          }, open ? '▾' : '▸') : spacer()));
      if (kids.length && open) {
        li.append(el('ul', { class: 'rail-sub', id: 'sub-' + id },
          kids.map((k) => el('li', null, railButton(k, cat(k).label, counts.get(k) || 0, catHealth(k), '', true)))));
      }
      nodes.push(li);
    });
    $('rail').replaceChildren(...nodes);

    // Narrow screens: the sub-categories of the active top-level category as a second strip.
    const top = state.cat === 'all' ? null : topOf(state.cat);
    const kids = top ? cat(top).children : [];
    $('subrail').replaceChildren(...kids.map((k) => railButton(k, cat(k).label, counts.get(k) || 0, catHealth(k), '', true)));
    $('subrail').hidden = kids.length === 0;
    // Keep the active chip visible in the scrolling strips on narrow screens.
    for (const b of document.querySelectorAll('.rail-list [aria-current="true"], .rail-list .ancestor, .subrail [aria-current="true"]')) {
      const strip = b.closest('.rail-list, .subrail');
      if (strip && strip.scrollWidth > strip.clientWidth) {
        strip.scrollLeft = Math.max(0, b.offsetLeft - strip.offsetLeft - 16);
      }
    }

    const bad = state.data.sources.filter((s) => s.status === 'failed' || s.status === 'stale').length;
    const catBad = state.data.categories.some((c) => !c.ok);
    $('health-dot').className = 'dot' + (catBad ? ' bad' : bad ? ' warn' : '');
    $('health-summary').textContent = bad ? 'Source health · ' + bad + ' issue' + (bad > 1 ? 's' : '') : 'Source health · all ok';
  }

  function railButton(id, label, n, health, key, sub) {
    const ancestor = !sub && state.view === 'list' && state.cat !== id && parentOf(state.cat) === id;
    return el('button', {
      class: 'rail-item' + (sub ? ' sub' : '') + (ancestor ? ' ancestor' : ''), type: 'button',
      'aria-current': state.view === 'list' && state.cat === id ? 'true' : 'false',
      onclick: () => go(id), title: key ? label + ' (shortcut ' + key + ')' : label
    },
    sub ? null : el('span', { class: 'key', 'aria-hidden': 'true', text: key }),
    id === 'all' ? null : el('span', { class: 'dot' + (health ? ' ' + health : ''), title: health === 'bad' ? 'below its minimum of healthy sources' : health === 'warn' ? 'some sources unhealthy' : 'healthy' }),
    el('span', { class: 'label', text: label }),
    el('span', { class: 'n', text: String(n) }));
  }

  function renderList() {
    for (const b of $('window').querySelectorAll('button')) b.setAttribute('aria-pressed', String(Number(b.dataset.h) === state.windowH));
    for (const b of $('sort').querySelectorAll('button')) b.setAttribute('aria-pressed', String(b.dataset.sort === state.sort));

    const baseItems = base();
    const topicCounts = new Map();
    for (const i of baseItems) for (const t of i.topics) topicCounts.set(t.id, (topicCounts.get(t.id) || 0) + 1);
    const topics = [...topicCounts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 16);
    $('topics').replaceChildren(...topics.map(([id, n]) => el('button', {
      class: 'topic', type: 'button', 'aria-pressed': String(state.topic === id),
      onclick: () => { state.topic = state.topic === id ? '' : id; state.sel = -1; state.limit = PAGE; renderList(); }
    }, (state.meta.topics && state.meta.topics[id]) || id, el('span', { class: 'n', text: String(n) }))));

    const srcs = new Map();
    for (const i of baseItems) srcs.set(i.source.id, i.source.name);
    const sel = $('source-filter');
    if (state.source && !srcs.has(state.source)) state.source = '';
    sel.replaceChildren(el('option', { value: '', text: 'all sources' }),
      ...[...srcs.entries()].sort((a, b) => a[1].localeCompare(b[1])).map(([id, name]) => el('option', { value: id, text: name })));
    sel.value = state.source;

    const items = filtered();
    state.shown = items.slice(0, state.limit);
    if (state.sel >= state.shown.length) state.sel = state.shown.length - 1;
    const now = new Date();
    const nodes = [];
    let lastDay = null;
    state.shown.forEach((i, idx) => {
      if (state.sort === 'time') {
        const d = new Date(i._t);
        const k = dayKey(d);
        if (k !== lastDay) { nodes.push(el('li', { class: 'day', 'aria-hidden': 'true', text: dayLabel(d, now) })); lastDay = k; }
      }
      nodes.push(row(i, idx, now));
    });
    $('list').replaceChildren(...nodes);

    const fresh = previousVisit ? items.filter((i) => i._t > previousVisit).length : 0;
    $('count').textContent = items.length + ' headline' + (items.length === 1 ? '' : 's') + (fresh ? ' · ' + fresh + ' new' : '');
    const more = $('more');
    more.hidden = items.length <= state.shown.length;
    more.textContent = 'Show more (' + (items.length - state.shown.length) + ' left)';

    const empty = $('empty');
    empty.hidden = items.length > 0;
    const label = state.cat === 'all' ? '' : cat(state.cat).label + ' ';
    empty.textContent = state.q ? 'No headlines match "' + state.q + '".'
      : state.windowH ? 'No ' + label + 'headlines in the last ' + state.windowH + ' h. Try 24h or all.'
        : 'No ' + label + 'headlines right now.';
  }

  function row(i, idx, now) {
    const raw = safeUrl(i.url);
    const xbrl = raw && i.kind === 'filing' && isXbrl(raw);
    const sym = i.symbols.length ? i.symbols[0].symbol : null;
    // An XBRL data file is not readable in a browser: the headline opens NSE's announcements page instead.
    const href = xbrl ? NSE_FILINGS_PAGE + (sym ? '?symbol=' + encodeURIComponent(sym) : '') : raw;
    const d = new Date(i._t);
    const time = el('time', {
      class: 'time', datetime: i.published_at,
      title: fmtFull.format(d) + ' (' + rel(Date.now() - i._t) + ' ago)',
      text: state.sort === 'time' || (d.getDate() === now.getDate() && Date.now() - i._t < 864e5) ? fmtTime.format(d) : fmtDay.format(d)
    });
    const isNew = previousVisit && i._t > previousVisit && !state.read.has(i.id);
    const title = href
      ? el('a', { class: 'title', href, target: '_blank', rel: 'noopener noreferrer', onclick: () => markRead(i.id, true) },
        isNew ? el('span', { class: 'new-dot', title: 'new since your last visit', 'aria-label': 'new' }) : null, i.title)
      : el('span', { class: 'title', text: i.title });

    const meta = el('div', { class: 'meta' }, el('span', { class: 'src', text: i.source.name }));
    if (i.kind !== 'news') meta.append(el('span', { class: 'tag kind', text: i.kind }));
    if (xbrl) meta.append(el('a', { class: 'tag doc', href: raw, target: '_blank', rel: 'noopener noreferrer', title: 'Machine-readable XBRL data file', text: 'XBRL ↗' }));
    else if (raw && /\.pdf(\?|$)/i.test(raw)) meta.append(el('span', { class: 'tag doc', title: 'The headline opens a PDF', text: 'PDF' }));
    for (const s of i.symbols) meta.append(el('span', { class: 'tag sym', title: s.match === 'exact' ? 'tagged by the exchange' : 'company named in the text', text: s.symbol }));
    for (const t of i.topics.slice(0, 4)) {
      meta.append(el('span', { class: 'tag' + (t.by === 'rule' ? ' rule' : ''), title: t.by === 'rule' ? 'keyword rule' : 'publisher section', text: (state.meta.topics && state.meta.topics[t.id]) || t.id }));
    }
    let cov = null;
    if (i.also_covered_by.length) {
      const expanded = state.open.has(i.id);
      meta.append(el('button', {
        class: 'cov-btn', type: 'button', 'aria-expanded': String(expanded),
        onclick: () => toggleCoverage(i.id)
      }, '+' + i.also_covered_by.length + ' more'));
      if (expanded) {
        cov = el('ul', { class: 'cov' }, i.also_covered_by.map((c) => {
          const cu = safeUrl(c.url);
          return el('li', null, cu ? el('a', { href: cu, target: '_blank', rel: 'noopener noreferrer', text: c.source_name }) : c.source_name,
            ' · ' + rel(Date.now() - Date.parse(c.published_at)) + ' ago');
        }));
      }
    }
    const body = el('div', { class: 'body' }, title, i.summary ? el('p', { class: 'summary', text: i.summary }) : null, meta, cov);
    return el('li', {
      class: 'row' + (state.read.has(i.id) ? ' read' : '') + (idx === state.sel ? ' sel' : ''),
      'data-idx': String(idx), 'data-id': i.id,
      onclick: (e) => { if (!e.target.closest('a, button')) select(idx); }
    }, time, body);
  }

  function renderHealth() {
    const d = state.data;
    $('health-cats').replaceChildren(...d.categories.map((c) => {
      const parent = parentOf(c.id);
      return el('div', { class: 'hcat' },
        el('b', null, el('span', { class: 'dot' + (c.ok ? (leafHealth(c.id) ? ' warn' : '') : ' bad'), 'aria-hidden': 'true' }), ' ',
          parent ? cat(parent).label + ' › ' : '', c.label),
        el('span', { text: c.healthy + ' healthy · needs ' + c.min_healthy + (c.required ? '' : ' (optional)') }));
    }));
    const order = { failed: 0, stale: 1, skipped: 2, ok: 3 };
    const rows = d.sources.slice().sort((a, b) => (order[a.status] - order[b.status]) || a.id.localeCompare(b.id));
    $('health-rows').replaceChildren(...rows.map((s) => {
      const home = safeUrl(s.homepage);
      return el('tr', null,
        el('td', null, el('span', { class: 'status ' + s.status, text: s.status })),
        el('td', null, home ? el('a', { href: home, target: '_blank', rel: 'noopener noreferrer', text: s.name }) : s.name),
        el('td', { class: 'mono', text: s.categories.map((c) => (cat(c) ? cat(c).label : c)).join(', ') }),
        el('td', { class: 'num', text: String(s.items) + (s.rejected ? ' (−' + s.rejected + ')' : '') }),
        el('td', { class: 'num', text: s.newest_at ? rel(Date.parse(d.generated_at) - Date.parse(s.newest_at)) : '—' }),
        el('td', { class: 'num', title: 'oldest item the source still lists', text: s.oldest_at ? rel(Date.parse(d.generated_at) - Date.parse(s.oldest_at)) : '—' }),
        el('td', { class: 'detail', text: s.error || s.note || '' }));
    }));
  }

  // ---------- interactions ----------
  function rowNodes() { return $('list').querySelectorAll('.row'); }
  function select(idx) {
    if (idx >= state.shown.length && state.shown.length < filtered().length) {
      state.limit += PAGE; renderList();
    }
    const rows = rowNodes();
    if (!rows.length) return;
    state.sel = Math.max(0, Math.min(idx, rows.length - 1));
    for (const r of rows) r.classList.toggle('sel', Number(r.dataset.idx) === state.sel);
    rows[state.sel].scrollIntoView({ block: 'nearest' });
  }
  function markRead(id, value) {
    if (value) state.read.add(id); else state.read.delete(id);
    store.set('read', [...state.read].slice(-3000));
    for (const r of rowNodes()) if (r.dataset.id === id) r.classList.toggle('read', value);
  }
  function toggleCoverage(id) {
    if (state.open.has(id)) state.open.delete(id); else state.open.add(id);
    const s = state.sel; renderList(); state.sel = s;
  }
  function current() { return state.sel >= 0 ? state.shown[state.sel] : null; }
  function stepSibling(dir) {
    const top = state.cat === 'all' ? null : topOf(state.cat);
    const kids = top ? cat(top).children : [];
    if (!kids.length) return;
    const pos = kids.indexOf(state.cat);
    const next = pos < 0 ? (dir > 0 ? 0 : kids.length - 1) : (pos + dir + kids.length) % kids.length;
    state.expanded.add(top);
    go(kids[next]);
  }

  $('q').addEventListener('input', (() => {
    let t;
    return (e) => { clearTimeout(t); t = setTimeout(() => { state.q = e.target.value.trim().toLowerCase(); state.sel = -1; state.limit = PAGE; renderList(); }, 80); };
  })());
  $('window').addEventListener('click', (e) => {
    const b = e.target.closest('button'); if (!b) return;
    state.windowH = Number(b.dataset.h); store.set('window', state.windowH); state.sel = -1; state.limit = PAGE; render();
  });
  $('sort').addEventListener('click', (e) => {
    const b = e.target.closest('button'); if (!b) return;
    state.sort = b.dataset.sort; store.set('sort', state.sort); state.sel = -1; renderList();
  });
  $('source-filter').addEventListener('change', (e) => { state.source = e.target.value; state.sel = -1; state.limit = PAGE; renderList(); });
  $('more').addEventListener('click', () => { state.limit += PAGE; renderList(); });
  $('density').addEventListener('click', () => { state.compact = !state.compact; store.set('compact', state.compact); applyDensity(); });
  $('theme').addEventListener('click', toggleTheme);
  $('help-btn').addEventListener('click', () => $('help').showModal());
  $('health-link').addEventListener('click', () => showHealth(true));
  $('health-close').addEventListener('click', () => showHealth(false));
  window.addEventListener('hashchange', () => { if (state.data) { fromHash(); render(); } });

  document.addEventListener('keydown', (e) => {
    if (e.metaKey || e.ctrlKey || e.altKey) return;
    const typing = /^(INPUT|SELECT|TEXTAREA)$/.test(document.activeElement && document.activeElement.tagName);
    if (e.key === 'Escape') {
      if ($('help').open) return;
      if (typing && $('q').value) { $('q').value = ''; state.q = ''; renderList(); return; }
      if (typing) { document.activeElement.blur(); return; }
      if (state.view === 'health') { showHealth(false); return; }
      return;
    }
    if (typing || !state.data) return;
    const k = e.key;
    if (k === '/') { e.preventDefault(); $('q').focus(); $('q').select(); }
    else if (k === '?') { $('help').showModal(); }
    else if (k === 't') { toggleTheme(); }
    else if (k === 'd') { state.compact = !state.compact; store.set('compact', state.compact); applyDensity(); }
    else if (k === 'h') { showHealth(state.view !== 'health'); }
    else if (k === 'e') { const top = state.cat === 'all' ? null : topOf(state.cat); if (top && cat(top).children.length) toggleExpanded(top); }
    else if (k === '[') { stepSibling(-1); }
    else if (k === ']') { stepSibling(1); }
    else if (/^[0-9]$/.test(k)) {
      if (k === '0') { go('all'); return; }
      const id = state.tops[Number(k) - 1];
      if (id) go(id);
    }
    else if (state.view !== 'list') { /* list keys only below */ }
    else if (k === 'j') { e.preventDefault(); select(state.sel + 1); }
    else if (k === 'k') { e.preventDefault(); select(state.sel - 1); }
    else if (k === 'o' || k === 'Enter') {
      const i = current(); if (!i) return;
      const raw = safeUrl(i.url);
      const sym = i.symbols.length ? i.symbols[0].symbol : null;
      const u = raw && i.kind === 'filing' && isXbrl(raw) ? NSE_FILINGS_PAGE + (sym ? '?symbol=' + encodeURIComponent(sym) : '') : raw;
      if (u) { window.open(u, '_blank', 'noopener,noreferrer'); markRead(i.id, true); }
    }
    else if (k === 'm') { const i = current(); if (i) markRead(i.id, !state.read.has(i.id)); }
    else if (k === 'c') { const i = current(); if (i && i.also_covered_by.length) toggleCoverage(i.id); }
  });

  load();
})();
