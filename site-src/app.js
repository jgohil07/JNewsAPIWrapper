/* jnews wire — static client. Renders data/latest.json; builds DOM with textContent only (no innerHTML). */
'use strict';
(() => {
  const $ = (id) => document.getElementById(id);
  const GROUPS = [['india-finance', 'India finance'], ['india', 'India'], ['world', 'World']];

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
  const fmtFull = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });
  const sameDay = (a, b) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

  // ---------- state ----------
  const state = {
    data: null, meta: null, cat: 'all', windowH: store.get('window', 0), sort: store.get('sort', 'time'),
    source: '', topic: '', q: '', sel: -1, view: 'list',
    compact: store.get('compact', false), read: new Set(store.get('read', [])), open: new Set(), shown: []
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
      if (data.schema_version !== 1 || !Array.isArray(data.items) || !Array.isArray(data.sources)) {
        throw new Error('data/latest.json has an unexpected format (schema_version ' + data.schema_version + ')');
      }
      state.data = data;
      state.meta = meta;
      for (const i of data.items) i._t = Date.parse(i.published_at);
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
  function showBanner(kind, text, action) {
    const b = $('banner');
    b.replaceChildren(el('div', { class: kind }, text, action ? ' ' : null, action || null));
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

  // ---------- routing ----------
  function fromHash() {
    const h = decodeURIComponent(location.hash.slice(1));
    if (h === 'health') { state.view = 'health'; return; }
    state.view = 'list';
    const ok = state.meta.categories.some((c) => c.id === h);
    state.cat = ok ? h : 'all';
  }
  function go(cat) {
    state.cat = cat; state.view = 'list'; state.topic = ''; state.source = ''; state.sel = -1;
    history.replaceState(null, '', cat === 'all' ? location.pathname + location.search : '#' + encodeURIComponent(cat));
    render();
    $('list').focus({ preventScroll: true });
  }
  function showHealth(on) {
    state.view = on ? 'health' : 'list';
    history.replaceState(null, '', on ? '#health' : (state.cat === 'all' ? location.pathname + location.search : '#' + state.cat));
    render();
  }

  // ---------- filtering ----------
  function inWindow(i) { return !state.windowH || Date.now() - i._t <= state.windowH * 36e5; }
  function inCat(i) { return state.cat === 'all' || i.categories.includes(state.cat); }
  function matchesQuery(i) {
    if (!state.q) return true;
    const hay = (i.title + ' ' + (i.summary || '') + ' ' + i.source.name + ' ' + i.symbols.map((s) => s.symbol).join(' ')).toLowerCase();
    return state.q.split(/\s+/).every((w) => hay.includes(w));
  }
  function filtered() {
    let items = state.data.items.filter((i) => inCat(i) && inWindow(i) && matchesQuery(i));
    if (state.source) items = items.filter((i) => i.source.id === state.source);
    if (state.topic) items = items.filter((i) => i.topics.some((t) => t.id === state.topic));
    if (state.sort === 'coverage') {
      items = items.slice().sort((a, b) => (b.also_covered_by.length - a.also_covered_by.length) || (b._t - a._t));
    }
    return items;
  }

  // ---------- category health ----------
  function catHealth(id) {
    const c = state.data.categories.find((x) => x.id === id);
    if (!c) return 'bad';
    if (!c.ok) return 'bad';
    const unhealthy = state.data.sources.some((s) => s.categories.includes(id) && s.status !== 'ok' && s.status !== 'skipped');
    return unhealthy ? 'warn' : '';
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
    const d = state.data, cats = state.meta.categories;
    const counts = {};
    for (const i of d.items) if (inWindow(i)) for (const c of i.categories) counts[c] = (counts[c] || 0) + 1;
    const nodes = [];
    const all = d.items.filter(inWindow).length;
    nodes.push(el('li', null, railButton('all', 'All', all, '', '0')));
    let key = 1;
    for (const [g, label] of GROUPS) {
      const members = cats.filter((c) => c.group === g);
      if (!members.length) continue;
      nodes.push(el('li', { class: 'rail-group', 'aria-hidden': 'true', text: label }));
      for (const c of members) {
        nodes.push(el('li', null, railButton(c.id, c.label, counts[c.id] || 0, catHealth(c.id), key <= 9 ? String(key) : '')));
        c._key = key++;
      }
    }
    $('rail').replaceChildren(...nodes);

    const bad = d.sources.filter((s) => s.status === 'failed' || s.status === 'stale').length;
    const catBad = d.categories.some((c) => !c.ok);
    $('health-dot').className = 'dot' + (catBad ? ' bad' : bad ? ' warn' : '');
    $('health-summary').textContent = bad ? 'Source health · ' + bad + ' issue' + (bad > 1 ? 's' : '') : 'Source health · all ok';
  }

  function railButton(id, label, n, health, key) {
    return el('button', {
      class: 'rail-item', type: 'button', 'aria-current': state.view === 'list' && state.cat === id ? 'true' : 'false',
      onclick: () => go(id), title: key ? 'Shortcut: ' + key : null
    },
    el('span', { class: 'key', 'aria-hidden': 'true', text: key }),
    id === 'all' ? null : el('span', { class: 'dot' + (health ? ' ' + health : ''), title: health === 'bad' ? 'below its minimum of healthy sources' : health === 'warn' ? 'some sources unhealthy' : 'healthy' }),
    el('span', { text: label }),
    el('span', { class: 'n', text: String(n) }));
  }

  function renderList() {
    // toolbar state
    for (const b of $('window').querySelectorAll('button')) b.setAttribute('aria-pressed', String(Number(b.dataset.h) === state.windowH));
    for (const b of $('sort').querySelectorAll('button')) b.setAttribute('aria-pressed', String(b.dataset.sort === state.sort));

    const base = state.data.items.filter((i) => inCat(i) && inWindow(i) && matchesQuery(i));
    // topic chips
    const topicCounts = new Map();
    for (const i of base) for (const t of i.topics) topicCounts.set(t.id, (topicCounts.get(t.id) || 0) + 1);
    const topics = [...topicCounts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 16);
    $('topics').replaceChildren(...topics.map(([id, n]) => el('button', {
      class: 'topic', type: 'button', 'aria-pressed': String(state.topic === id),
      onclick: () => { state.topic = state.topic === id ? '' : id; state.sel = -1; renderList(); }
    }, (state.meta.topics && state.meta.topics[id]) || id, el('span', { class: 'n', text: String(n) }))));

    // source filter
    const srcs = new Map();
    for (const i of base) srcs.set(i.source.id, i.source.name);
    const sel = $('source-filter');
    if (state.source && !srcs.has(state.source)) state.source = '';
    sel.replaceChildren(el('option', { value: '', text: 'all sources' }),
      ...[...srcs.entries()].sort((a, b) => a[1].localeCompare(b[1])).map(([id, name]) => el('option', { value: id, text: name })));
    sel.value = state.source;

    const items = filtered();
    state.shown = items;
    if (state.sel >= items.length) state.sel = items.length - 1;
    const now = new Date();
    $('list').replaceChildren(...items.map((i, idx) => row(i, idx, now)));
    $('count').textContent = items.length + ' headline' + (items.length === 1 ? '' : 's');
    const empty = $('empty');
    empty.hidden = items.length > 0;
    empty.textContent = state.q ? 'No headlines match "' + state.q + '".' : 'No headlines in this window.';
  }

  function row(i, idx, now) {
    const url = safeUrl(i.url);
    const d = new Date(i._t);
    const time = el('time', {
      class: 'time', datetime: i.published_at,
      title: fmtFull.format(d) + ' (' + rel(Date.now() - i._t) + ' ago)',
      text: sameDay(d, now) ? fmtTime.format(d) : fmtDay.format(d)
    });
    const title = url
      ? el('a', { class: 'title', href: url, target: '_blank', rel: 'noopener noreferrer', onclick: () => markRead(i.id, true) }, i.title)
      : el('span', { class: 'title', text: i.title });

    const meta = el('div', { class: 'meta' }, el('span', { class: 'src', text: i.source.name }));
    if (i.kind !== 'news') meta.append(el('span', { class: 'tag kind', text: i.kind }));
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
    const body = el('div', null, title, i.summary ? el('p', { class: 'summary', text: i.summary }) : null, meta, cov);
    return el('li', {
      class: 'row' + (state.read.has(i.id) ? ' read' : '') + (idx === state.sel ? ' sel' : ''),
      'data-idx': String(idx), onclick: (e) => { if (e.target.tagName !== 'A' && e.target.tagName !== 'BUTTON') select(idx); }
    }, time, body);
  }

  function renderHealth() {
    const d = state.data;
    $('health-cats').replaceChildren(...d.categories.map((c) => el('div', { class: 'hcat' },
      el('b', null, el('span', { class: 'dot' + (c.ok ? (catHealth(c.id) ? ' warn' : '') : ' bad'), 'aria-hidden': 'true' }), ' ', c.label),
      el('span', { text: c.healthy + ' healthy · needs ' + c.min_healthy + (c.required ? '' : ' (optional)') }))));
    const order = { failed: 0, stale: 1, skipped: 2, ok: 3 };
    const rows = d.sources.slice().sort((a, b) => (order[a.status] - order[b.status]) || a.id.localeCompare(b.id));
    $('health-rows').replaceChildren(...rows.map((s) => {
      const home = safeUrl(s.homepage);
      return el('tr', null,
        el('td', null, el('span', { class: 'status ' + s.status, text: s.status })),
        el('td', null, home ? el('a', { href: home, target: '_blank', rel: 'noopener noreferrer', text: s.name }) : s.name),
        el('td', { class: 'mono', text: s.categories.join(', ') }),
        el('td', { class: 'num', text: String(s.items) + (s.rejected ? ' (−' + s.rejected + ')' : '') }),
        el('td', { class: 'num', text: s.newest_at ? rel(Date.parse(d.generated_at) - Date.parse(s.newest_at)) : '—' }),
        el('td', { class: 'detail', text: s.error || s.note || '' }));
    }));
  }

  // ---------- interactions ----------
  function select(idx) {
    const rows = $('list').children;
    if (!rows.length) return;
    state.sel = Math.max(0, Math.min(idx, rows.length - 1));
    for (const r of rows) r.classList.toggle('sel', Number(r.dataset.idx) === state.sel);
    rows[state.sel].scrollIntoView({ block: 'nearest' });
  }
  function markRead(id, value) {
    if (value) state.read.add(id); else state.read.delete(id);
    store.set('read', [...state.read].slice(-3000));
    const idx = state.shown.findIndex((i) => i.id === id);
    const r = $('list').children[idx];
    if (r) r.classList.toggle('read', value);
  }
  function toggleCoverage(id) {
    if (state.open.has(id)) state.open.delete(id); else state.open.add(id);
    renderList();
  }
  function current() { return state.sel >= 0 ? state.shown[state.sel] : null; }

  $('q').addEventListener('input', (() => {
    let t;
    return (e) => { clearTimeout(t); t = setTimeout(() => { state.q = e.target.value.trim().toLowerCase(); state.sel = -1; renderList(); }, 80); };
  })());
  $('window').addEventListener('click', (e) => {
    const b = e.target.closest('button'); if (!b) return;
    state.windowH = Number(b.dataset.h); store.set('window', state.windowH); state.sel = -1; render();
  });
  $('sort').addEventListener('click', (e) => {
    const b = e.target.closest('button'); if (!b) return;
    state.sort = b.dataset.sort; store.set('sort', state.sort); state.sel = -1; renderList();
  });
  $('source-filter').addEventListener('change', (e) => { state.source = e.target.value; state.sel = -1; renderList(); });
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
    else if (state.view !== 'list') { /* list keys only below */ }
    else if (k === 'j') { e.preventDefault(); select(state.sel + 1); }
    else if (k === 'k') { e.preventDefault(); select(state.sel - 1); }
    else if (k === 'o' || k === 'Enter') {
      const i = current(); const u = i && safeUrl(i.url);
      if (u) { window.open(u, '_blank', 'noopener,noreferrer'); markRead(i.id, true); }
    }
    else if (k === 'm') { const i = current(); if (i) markRead(i.id, !state.read.has(i.id)); }
    else if (k === 'c') { const i = current(); if (i && i.also_covered_by.length) { const s = state.sel; toggleCoverage(i.id); select(s); } }
    else if (/^[0-9]$/.test(k)) {
      if (k === '0') { go('all'); return; }
      const c = state.meta.categories.find((x) => x._key === Number(k));
      if (c) go(c.id);
    }
  });

  load();
})();
