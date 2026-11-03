/* Paradox Music: the window. Library views, the player (two <audio> elements for near-gapless
   playback), queue, playlists, settings. Talks only to the app's own Rust side; no network. */
'use strict';
const T = window.__TAURI__;
const invoke = T.core.invoke;
const listen = T.event.listen;
const fileSrc = p => T.core.convertFileSrc(p);
const ICON = {"side": "<svg viewBox=\"0 0 24 24\"><rect x=\"3\" y=\"4\" width=\"18\" height=\"16\" rx=\"3\"/><path d=\"M9 4v16\"/></svg>", "songs": "<svg viewBox=\"0 0 24 24\"><path d=\"M9 18V5l11-2v13\"/><circle cx=\"6.5\" cy=\"18\" r=\"2.5\"/><circle cx=\"17.5\" cy=\"16\" r=\"2.5\"/></svg>", "albums": "<svg viewBox=\"0 0 24 24\"><circle cx=\"12\" cy=\"12\" r=\"9\"/><circle cx=\"12\" cy=\"12\" r=\"2.5\"/></svg>", "artists": "<svg viewBox=\"0 0 24 24\"><circle cx=\"12\" cy=\"8\" r=\"4\"/><path d=\"M4 20a8 8 0 0 1 16 0\"/></svg>", "settings": "<svg viewBox=\"0 0 24 24\"><path d=\"M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0\"/><circle cx=\"16\" cy=\"6\" r=\"2\"/><circle cx=\"10\" cy=\"12\" r=\"2\"/><circle cx=\"18\" cy=\"18\" r=\"2\"/></svg>", "plus": "<svg viewBox=\"0 0 24 24\"><path d=\"M12 5v14M5 12h14\"/></svg>", "list": "<svg viewBox=\"0 0 24 24\"><path d=\"M4 7h11M4 12h11M4 17h7\"/><path d=\"M17 14.5V20l4-2.5z\" fill=\"currentColor\"/></svg>", "search": "<svg viewBox=\"0 0 24 24\"><circle cx=\"11\" cy=\"11\" r=\"7\"/><path d=\"m20 20-3.5-3.5\"/></svg>", "sort": "<svg viewBox=\"0 0 24 24\"><path d=\"M7 4v16M3.5 16.5 7 20l3.5-3.5M17 20V4M13.5 7.5 17 4l3.5 3.5\"/></svg>", "queue": "<svg viewBox=\"0 0 24 24\"><path d=\"M4 6h16M4 11h16M4 16h9\"/><path d=\"M16 14v6l5-3z\" fill=\"currentColor\"/></svg>", "refresh": "<svg viewBox=\"0 0 24 24\"><path d=\"M20 11a8 8 0 1 0-2.3 5.7\"/><path d=\"M20 4v7h-7\"/></svg>", "back": "<svg viewBox=\"0 0 24 24\"><path d=\"M15 5l-7 7 7 7\"/></svg>", "play": "<svg viewBox=\"0 0 24 24\" class=\"fill\"><path d=\"M8 5.5v13a1 1 0 0 0 1.5.86l10.2-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z\"/></svg>", "pause": "<svg viewBox=\"0 0 24 24\" class=\"fill\"><rect x=\"6.5\" y=\"5\" width=\"4\" height=\"14\" rx=\"1.2\"/><rect x=\"13.5\" y=\"5\" width=\"4\" height=\"14\" rx=\"1.2\"/></svg>", "prev": "<svg viewBox=\"0 0 24 24\" class=\"fill\"><path d=\"M18 6.2v11.6a.9.9 0 0 1-1.4.75L8.6 12.75a.9.9 0 0 1 0-1.5l8-5.8A.9.9 0 0 1 18 6.2z\"/><rect x=\"5\" y=\"5.5\" width=\"2.4\" height=\"13\" rx=\"1\"/></svg>", "next": "<svg viewBox=\"0 0 24 24\" class=\"fill\"><path d=\"M6 6.2v11.6a.9.9 0 0 0 1.4.75l8-5.8a.9.9 0 0 0 0-1.5l-8-5.8A.9.9 0 0 0 6 6.2z\"/><rect x=\"16.6\" y=\"5.5\" width=\"2.4\" height=\"13\" rx=\"1\"/></svg>", "shuffle": "<svg viewBox=\"0 0 24 24\"><path d=\"M16 3h5v5\"/><path d=\"M4 20 21 3\"/><path d=\"M21 16v5h-5\"/><path d=\"M15 15l6 6\"/><path d=\"M4 4l5 5\"/></svg>", "repeat": "<svg viewBox=\"0 0 24 24\"><path d=\"M17 2l4 4-4 4\"/><path d=\"M3 11v-1a4 4 0 0 1 4-4h14\"/><path d=\"M7 22l-4-4 4-4\"/><path d=\"M21 13v1a4 4 0 0 1-4 4H3\"/></svg>", "vol": "<svg viewBox=\"0 0 24 24\"><path d=\"M11 5 6 9H3v6h3l5 4z\"/><path d=\"M15.5 8.5a5 5 0 0 1 0 7\"/><path d=\"M18.5 5.5a9 9 0 0 1 0 13\"/></svg>", "mute": "<svg viewBox=\"0 0 24 24\"><path d=\"M11 5 6 9H3v6h3l5 4z\"/><path d=\"m22 9-6 6M16 9l6 6\"/></svg>", "more": "<svg viewBox=\"0 0 24 24\" class=\"fill\"><circle cx=\"5\" cy=\"12\" r=\"1.7\"/><circle cx=\"12\" cy=\"12\" r=\"1.7\"/><circle cx=\"19\" cy=\"12\" r=\"1.7\"/></svg>", "folder": "<svg viewBox=\"0 0 24 24\"><path d=\"M3 7a2 2 0 0 1 2-2h4l2 2.5h8a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z\"/></svg>", "x": "<svg viewBox=\"0 0 24 24\"><path d=\"M6 6l12 12M18 6 6 18\"/></svg>", "note": "<svg viewBox=\"0 0 24 24\"><path d=\"M9 18V5l11-2v13\"/><circle cx=\"6.5\" cy=\"18\" r=\"2.5\"/><circle cx=\"17.5\" cy=\"16\" r=\"2.5\"/></svg>", "clock": "<svg viewBox=\"0 0 24 24\"><circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M12 7v5l3 2\"/></svg>", "check": "<svg viewBox=\"0 0 24 24\"><path d=\"M5 12.5l4.5 4.5L19 7.5\"/></svg>", "drop": "<svg viewBox=\"0 0 24 24\"><path d=\"M12 4v11\"/><path d=\"m7 10 5 5 5-5\"/><path d=\"M5 20h14\"/></svg>"};

Object.assign(ICON, {
  heart: '<svg viewBox="0 0 24 24" class="heart"><path d="M12 20.3s-7.4-4.5-9.2-9.1C1.6 8.1 3.7 4.6 7.3 4.6c2 0 3.6 1.1 4.7 2.7 1.1-1.6 2.7-2.7 4.7-2.7 3.6 0 5.7 3.5 4.5 6.6-1.8 4.6-9.2 9.1-9.2 9.1z"/></svg>',
  spark: '<svg viewBox="0 0 24 24"><path d="M11 3.5l1.7 4.6 4.6 1.7-4.6 1.7L11 16.1l-1.7-4.6-4.6-1.7 4.6-1.7z"/><path d="M18.5 14.5l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z"/></svg>',
  wave: '<svg viewBox="0 0 24 24"><path d="M3 12h2.5l2-5 3 10 3-14 3 12 2-3H21"/></svg>',
  edit: '<svg viewBox="0 0 24 24"><path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/></svg>',
  most: '<svg viewBox="0 0 24 24"><path d="M5 20V12M10 20V6M15 20v-9M20 20V4"/></svg>',
  recent: '<svg viewBox="0 0 24 24"><path d="M3.5 12a8.5 8.5 0 1 0 2.5-6"/><path d="M3 4v4.5h4.5"/><path d="M12 8v4.5l3 2"/></svg>',
  added: '<svg viewBox="0 0 24 24"><rect x="3.5" y="3.5" width="17" height="17" rx="4"/><path d="M12 8v8M8 12h8"/></svg>',
  never: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path d="M10 8.8v6.4l5-3.2z"/></svg>',
  trash: '<svg viewBox="0 0 24 24"><path d="M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1-13"/></svg>',
});
const AUTO_ICON = { favorites: 'heart', most: 'most', recent: 'recent', added: 'added', never: 'never', genre: 'spark', decade: 'clock' };
const Sm = window.PMSmart;

const $ = s => document.querySelector(s);
const app = $('#app');
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = s => { s = Math.max(0, Math.floor(s || 0)); const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = String(s % 60).padStart(2, '0'); return h ? `${h}:${String(m).padStart(2, '0')}:${x}` : `${m}:${x}`; };
const fmtLong = s => { const m = Math.round((s || 0) / 60); return m >= 60 ? `${Math.floor(m / 60)} hr ${m % 60} min` : `${m} min`; };
const plural = (n, w) => `${n.toLocaleString()} ${w}${n === 1 ? '' : 's'}`;
const coll = new Intl.Collator(undefined, { sensitivity: 'base', numeric: true });

/* ------------------------------------------------------------------ state */
let B = {};                 // boot payload
let settings = {};          // { folders:[], setupDone, theme, gapless, lastScan }
let tracks = [];            // library
let byId = new Map();       // id -> track (library + dropped files)
let albums = [], albumByKey = new Map(), artists = [], artistByName = new Map();
let playlists = [];         // [{ id, name, ids:[] }]
let Q = new PMQueue();
let view = { name: 'songs', arg: null };
const history = [];
let sortBy = { songs: 'title', albums: 'name', artists: 'name' };
let query = '';
let scanning = false;
let demo = null;
let stats = {};             // play counts, last played and likes: { id: { p, l, f, fa } }
let smartLists = [];        // saved smart playlists: [{ id, name, rule, created }]; rules only, never songs
let mixes = new Map();      // mixes made this session: id -> { name, ids, seed, kind, ref, rnd }
const sessionSeed = (Date.now() % 100000) + 1;  // keeps "Random" smart lists in one order per session

/* ------------------------------------------------------------------ helpers */
function cover(name, cls) {
  return `<div class="cv ${cls || 'r6'}">${name ? `<img src="${esc(fileSrc(B.coversDir + B.sep + name))}" loading="lazy" decoding="async" alt="">` : ICON.note}</div>`;
}
function albumKey(t) { return (t.album_artist || t.artist).toLowerCase() + '\u0001' + t.album.toLowerCase(); }
function save(name, value) { return invoke('save_json', { name, value }).catch(e => console.warn('save', name, e)); }
function saveSettings() { return save('settings', settings); }
function savePlaylists() { return save('playlists', playlists); }
function saveSmart() { return save('smart', smartLists); }
let statsTimer;
function saveStatsSoon() { if (demo) return; clearTimeout(statsTimer); statsTimer = setTimeout(saveStatsNow, 1200); }
function saveStatsNow() { if (demo) return; clearTimeout(statsTimer); save('stats', Sm.Stats.toJSON(stats)); }

/* ------------------------------------------------------------------ likes, play stats, smart lists */
const isLiked = id => !!Sm.Stats.get(stats, id).f;
function likeBtn(id) { const on = isLiked(id); return `<button class="lk${on ? ' on' : ''}" aria-label="${on ? 'Unlike' : 'Like'}" title="${on ? 'Remove from Favorites' : 'Add to Favorites'}">${ICON.heart}</button>`; }
function setLiked(id, liked) {
  if (!id || isLiked(id) === !!liked) return;
  stats = Sm.Stats.setLiked(stats, id, liked, Date.now());
  saveStatsSoon();
  document.querySelectorAll(`.trow[data-id="${id}"] .lk`).forEach(b => { b.classList.toggle('on', !!liked); b.title = liked ? 'Remove from Favorites' : 'Add to Favorites'; });
  syncLike();
  toast(liked ? 'Added to Favorites' : 'Removed from Favorites');
  smartChanged();
}
const toggleLike = id => setLiked(id, !isLiked(id));
function syncLike() {
  const b = $('#likeBtn'); if (!b) return;
  const on = !!curId && isLiked(curId);
  b.classList.toggle('on', on); b.title = on ? 'Remove from Favorites' : 'Add to Favorites'; b.setAttribute('aria-label', on ? 'Unlike' : 'Like');
  const nb = document.querySelector('.now-acts [data-act=like]');
  if (nb) { nb.classList.toggle('on', on); nb.lastChild.textContent = on ? 'Liked' : 'Like'; }
}
function recordPlay(id) {
  if (!id || demo || !byId.has(id) || byId.get(id)._dropped) return;
  stats = Sm.Stats.recordPlay(stats, id, Date.now());
  saveStatsSoon();
  smartChanged();
}
/** Smart lists update themselves: after a play or a like, the sidebar and any smart page on screen catch up. */
function smartChanged() {
  renderSmNav();
  if (['smart', 'auto', 'smartpl'].includes(view.name) && $('#modal').hidden) softRender();
}
/** Re-renders the current view in place (no fade, same scroll position). */
function softRender() {
  const v = document.querySelector('#views .view'), top = v ? v.scrollTop : 0;
  render(true);
  const n = document.querySelector('#views .view'); if (n) n.scrollTop = top;
}
const smartNow = () => Date.now();
function evalRule(rule) { return Sm.evaluate(rule, tracks, stats, smartNow(), sessionSeed); }
function autoDef(key) { return Sm.autoByKey(key, tracks); }
let toastTimer;
function toast(msg) { const t = $('#toast'); t.textContent = msg; t.classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('show'), 2200); }
function matches(t, q) { return !q || t.title.toLowerCase().includes(q) || t.artist.toLowerCase().includes(q) || t.album.toLowerCase().includes(q) || (t.genre || '').toLowerCase().includes(q); }

/* ------------------------------------------------------------------ library index */
function indexLibrary(list) {
  tracks = list || [];
  for (const [id, t] of byId) if (!t._dropped) byId.delete(id);
  for (const t of tracks) byId.set(t.id, t);
  const am = new Map();
  for (const t of tracks) {
    const k = albumKey(t);
    let a = am.get(k);
    if (!a) am.set(k, a = { key: k, name: t.album, artist: t.album_artist || t.artist, year: 0, cover: null, tracks: [], added: 0 });
    a.tracks.push(t);
    if (!a.cover && t.cover) a.cover = t.cover;
    if (t.year && (!a.year || t.year < a.year)) a.year = t.year;
    a.added = Math.max(a.added, t.added || 0);
  }
  albums = [...am.values()];
  for (const a of albums) {
    a.tracks.sort((x, y) => (x.disc || 0) - (y.disc || 0) || (x.track || 0) - (y.track || 0) || coll.compare(x.title, y.title));
    a.duration = a.tracks.reduce((s, t) => s + (t.duration || 0), 0);
    // "Various artists" when an album's songs disagree and no album artist is set
    const arts = new Set(a.tracks.map(t => t.album_artist || t.artist));
    if (arts.size > 1) a.artist = 'Various artists';
  }
  albumByKey = new Map(albums.map(a => [a.key, a]));
  const rm = new Map();
  for (const t of tracks) {
    const n = t.artist;
    let r = rm.get(n.toLowerCase());
    if (!r) rm.set(n.toLowerCase(), r = { name: n, tracks: [], albums: new Set(), cover: null });
    r.tracks.push(t); r.albums.add(albumKey(t));
    if (!r.cover && t.cover) r.cover = t.cover;
  }
  artists = [...rm.values()];
  artistByName = new Map(artists.map(r => [r.name.toLowerCase(), r]));
  $('#cSongs').textContent = tracks.length ? tracks.length.toLocaleString() : '';
  $('#cAlbums').textContent = albums.length ? albums.length.toLocaleString() : '';
  $('#cArtists').textContent = artists.length ? artists.length.toLocaleString() : '';
}

const SORTS = {
  songs: { title: ['Title', (a, b) => coll.compare(a.title, b.title)], artist: ['Artist', (a, b) => coll.compare(a.artist, b.artist) || coll.compare(a.album, b.album) || (a.track - b.track)],
    album: ['Album', (a, b) => coll.compare(a.album, b.album) || (a.disc - b.disc) || (a.track - b.track)], added: ['Recently added', (a, b) => (b.added - a.added) || (b.mtime - a.mtime) || coll.compare(a.title, b.title)],
    duration: ['Duration', (a, b) => (b.duration - a.duration)],
    plays: ['Most played', (a, b) => (Sm.Stats.get(stats, b.id).p - Sm.Stats.get(stats, a.id).p) || coll.compare(a.title, b.title)] },
  albums: { name: ['Name', (a, b) => coll.compare(a.name, b.name)], artist: ['Artist', (a, b) => coll.compare(a.artist, b.artist) || coll.compare(a.name, b.name)],
    year: ['Year', (a, b) => (b.year - a.year) || coll.compare(a.name, b.name)], added: ['Recently added', (a, b) => (b.added - a.added)] },
  artists: { name: ['Name', (a, b) => coll.compare(a.name, b.name)], songs: ['Most songs', (a, b) => b.tracks.length - a.tracks.length || coll.compare(a.name, b.name)] },
};

/* ------------------------------------------------------------------ navigation */
function go(name, arg, opts) {
  opts = opts || {};
  if (!opts.back && (view.name !== name || view.arg !== arg)) history.push({ ...view });
  if (history.length > 50) history.shift();
  view = { name, arg };
  if (!['songs', 'albums', 'artists'].includes(name) && query && !opts.keepQuery) setQuery('', true);
  render();
  if (window.innerWidth < 860) app.dataset.sidebar = 'closed';
  persistSoon();
}
function back() { const v = history.pop(); if (v) go(v.name, v.arg, { back: true }); }

function render(soft) {
  const v = view.name;
  document.querySelectorAll('.nav-item').forEach(n => n.classList.toggle('active', !!(n.dataset.view === v || (n.dataset.pl && v === 'playlist' && n.dataset.pl === view.arg)
    || (n.dataset.sm && (v === 'auto' || v === 'smartpl') && n.dataset.sm === view.arg))));
  $('#backBtn').hidden = !['album', 'artist', 'now', 'auto', 'smartpl', 'mix'].includes(v);
  $('#sortBtn').hidden = !['songs', 'albums', 'artists'].includes(v);
  const host = $('#views');
  host.innerHTML = '';
  const el = document.createElement('section');
  el.className = 'view' + (soft ? ' still' : '');
  host.appendChild(el);
  if (!tracks.length && ['songs', 'albums', 'artists', 'smart'].includes(v)) return renderEmpty(el);
  ({ songs: renderSongs, albums: renderAlbums, artists: renderArtists, album: renderAlbum, artist: renderArtist, playlist: renderPlaylist, settings: renderSettings, now: renderNow,
    smart: renderSmart, auto: renderAuto, smartpl: renderSmartPl, mix: renderMix }[v] || renderSongs)(el);
}

function scanStrip() { return scanning ? `<div class="scanning"><span id="scanTxt">Updating library…</span><div class="bar"><div class="bar-fill" id="scanFill"></div></div></div>` : ''; }

function renderEmpty(el) {
  if (scanning) {
    el.innerHTML = `<div class="empty"><div class="big">${ICON.refresh}</div><h2>Reading your music…</h2><div class="muted" id="scanTxt">Looking for songs</div><div class="bar" style="width:240px;margin-top:14px"><div class="bar-fill" id="scanFill"></div></div></div>`;
    return;
  }
  el.innerHTML = `<div class="empty"><div class="big">${ICON.note}</div><h2>No music found</h2><div class="muted">Nothing playable in ${settings.folders && settings.folders.length > 1 ? 'your folders' : 'your music folder'} yet. Paradox Music plays MP3, M4A, AAC, FLAC, WAV, OGG and Opus.</div>
    <div class="actions" style="justify-content:center"><button class="btn primary" data-act="change">${ICON.folder}Choose another folder</button><button class="btn" data-act="rescan">Rescan</button></div></div>`;
  el.querySelector('[data-act=change]').onclick = () => changeFolder(true);
  el.querySelector('[data-act=rescan]').onclick = () => rescan(true);
}

/* ---------- song rows */
function rowHTML(t, i, o) {
  o = o || {};
  const cur = t.id === Q.current();
  const n = o.num != null ? o.num : i + 1;
  return `<div class="trow${cur ? ' cur' : ''}" data-i="${i}" data-id="${t.id}"${o.y != null ? ` style="top:${o.y}px"` : ''}>
    <span class="tn"><span class="n">${n || ''}</span><span class="pi">${ICON.play}</span><span class="eq"><i></i><i></i><i></i></span></span>
    <span class="tt">${o.noCover ? '' : cover(t.cover, 'r6')}<span class="tl"><span class="t1">${esc(t.title)}</span><span class="t2">${esc(t.artist)}</span></span></span>
    <span class="ta">${esc(t.album)}</span>
    <span class="td">${likeBtn(t.id)}<span class="dur">${fmt(t.duration)}</span></span>
    <button class="more" aria-label="More">${ICON.more}</button></div>`;
}
const theadHTML = `<div class="thead"><span style="text-align:center">#</span><span>Title</span><span class="ah">Album</span><span class="r">${ICON.clock}</span><span></span></div>`;

/** Wire clicks on a list of rows. list() returns the songs in display order. */
function wireRows(container, list, ctx) {
  let sel = null;
  container.addEventListener('click', e => {
    const row = e.target.closest('.trow'); if (!row) return;
    const i = +row.dataset.i;
    if (e.target.closest('.lk')) { e.stopPropagation(); return toggleLike(row.dataset.id); }
    if (e.target.closest('.more')) { e.stopPropagation(); return trackMenu(e, list(), i, ctx); }
    if (e.target.closest('.pi')) return playList(list().map(t => t.id), i);
    container.querySelectorAll('.trow.sel').forEach(r => r.classList.remove('sel'));
    row.classList.add('sel'); sel = i;
  });
  container.addEventListener('dblclick', e => {
    const row = e.target.closest('.trow'); if (!row || e.target.closest('.more') || e.target.closest('.lk')) return;
    playList(list().map(t => t.id), +row.dataset.i);
  });
  container.addEventListener('contextmenu', e => {
    const row = e.target.closest('.trow'); if (!row) return;
    e.preventDefault(); trackMenu(e, list(), +row.dataset.i, ctx);
  });
  container.addEventListener('keydown', e => { if (e.key === 'Enter' && sel != null) playList(list().map(t => t.id), sel); });
}

function plainList(list, opts) {
  const wrap = document.createElement('div');
  wrap.className = 'list' + (opts && opts.noCover ? ' no-cover' : '') + (opts && opts.noAlbum ? ' no-album' : '');
  wrap.innerHTML = list.map((t, i) => rowHTML(t, i, { num: opts && opts.trackNums ? (t.track || '') : i + 1, noCover: opts && opts.noCover })).join('');
  wireRows(wrap, () => list, opts && opts.ctx);
  return wrap;
}

/* ---------- Songs (virtualised: only the rows on screen exist) */
function songList() {
  const q = query.toLowerCase();
  return tracks.filter(t => matches(t, q)).sort(SORTS.songs[sortBy.songs][1]);
}
function renderSongs(el) {
  const list = songList();
  el.innerHTML = `<div class="page fill">${scanStrip()}<div class="head"><h1>Songs</h1><span class="count">${query ? `${plural(list.length, 'result')} for “${esc(query)}”` : plural(list.length, 'song')}</span>
    <div class="actions"><button class="btn small primary" data-act="play">${ICON.play}Play</button><button class="btn small" data-act="shuffle">${ICON.shuffle}Shuffle</button></div></div>
    ${theadHTML}<div class="vlist" tabindex="0"><div class="vspace"></div></div></div>`;
  el.style.overflow = 'hidden';
  el.querySelector('[data-act=play]').onclick = () => playList(list.map(t => t.id), 0, { shuffle: false });
  el.querySelector('[data-act=shuffle]').onclick = () => playList(list.map(t => t.id), -1, { shuffle: true });
  const vl = el.querySelector('.vlist'), sp = el.querySelector('.vspace');
  const H = 56;
  sp.style.height = list.length * H + 'px';
  let lastA = -1, lastB = -1;
  const draw = force => {
    const a = Math.max(0, Math.floor(vl.scrollTop / H) - 8), b = Math.min(list.length, Math.ceil((vl.scrollTop + vl.clientHeight) / H) + 8);
    if (!force && a === lastA && b === lastB) return;
    lastA = a; lastB = b;
    let h = '';
    for (let i = a; i < b; i++) h += rowHTML(list[i], i, { y: i * H });
    sp.innerHTML = h;
  };
  vl.addEventListener('scroll', () => draw(false), { passive: true });
  new ResizeObserver(() => draw(true)).observe(vl);
  wireRows(sp, () => list);
  el._redraw = () => draw(true);
  if (view.scrollTo) { vl.scrollTop = view.scrollTo; view.scrollTo = 0; }
  draw(true);
}

/* ---------- Albums */
function albumList() {
  const q = query.toLowerCase();
  return albums.filter(a => !q || a.name.toLowerCase().includes(q) || a.artist.toLowerCase().includes(q) || a.tracks.some(t => matches(t, q))).sort(SORTS.albums[sortBy.albums][1]);
}
function renderAlbums(el) {
  const list = albumList();
  el.innerHTML = `<div class="page">${scanStrip()}<div class="head"><h1>Albums</h1><span class="count">${query ? `${plural(list.length, 'result')} for “${esc(query)}”` : plural(list.length, 'album')}</span></div>
    <div class="grid">${list.map((a, i) => `<div class="card" data-i="${i}">${cover(a.cover, 'r12').replace('</div>', `<button class="card-play" aria-label="Play">${ICON.play}</button></div>`)}
    <div class="cn">${esc(a.name)}</div><div class="ca">${esc(a.artist)}${a.year ? ' · ' + a.year : ''}</div></div>`).join('')}</div></div>`;
  wireCards(el, i => list[i], a => go('album', a.key), a => a.tracks, (e, a) => albumMenu(e, a));
}
function wireCards(el, get, open, songsOf, menu) {
  el.addEventListener('click', e => {
    const c = e.target.closest('.card'); if (!c) return;
    const it = get(+c.dataset.i);
    if (e.target.closest('.card-play')) return playList(songsOf(it).map(t => t.id), 0);
    open(it);
  });
  el.addEventListener('contextmenu', e => { const c = e.target.closest('.card'); if (!c) return; e.preventDefault(); menu(e, get(+c.dataset.i)); });
}

function albumHead(kicker, title, subHTML, coverHTML, extraHTML, opts) {
  opts = opts || {};
  const dis = opts.empty ? ' disabled' : '';
  return `<div class="dhead">${coverHTML}<div class="dmeta"><div class="kicker">${kicker}</div><div class="dtitle">${esc(title)}</div><div class="dsub">${subHTML}</div>
    <div class="actions"><button class="btn primary" data-act="play"${dis}>${ICON.play}Play</button><button class="btn" data-act="shuffle"${dis}>${ICON.shuffle}Shuffle</button>${extraHTML || ''}<button class="icon-btn" data-act="more" aria-label="More">${ICON.more}</button></div></div></div>`;
}
function renderAlbum(el) {
  const a = albumByKey.get(view.arg);
  if (!a) return go('albums');
  el.innerHTML = `<div class="page">${albumHead('Album', a.name, `<b data-artist="${esc(a.artist)}">${esc(a.artist)}</b>${a.year ? ' · ' + a.year : ''} · ${plural(a.tracks.length, 'song')}, ${fmtLong(a.duration)}`, cover(a.cover, 'r12'))}</div>`;
  const page = el.querySelector('.page');
  page.appendChild(plainList(a.tracks, { trackNums: true, noCover: true, noAlbum: true }));
  wireHead(el, () => a.tracks, e => albumMenu(e, a));
}
function wireHead(el, songs, more) {
  el.querySelector('[data-act=play]').onclick = () => playList(songs().map(t => t.id), 0, { shuffle: false });
  el.querySelector('[data-act=shuffle]').onclick = () => playList(songs().map(t => t.id), -1, { shuffle: true });
  el.querySelector('[data-act=more]').onclick = e => { e.stopPropagation(); more(e); };
  const ar = el.querySelector('[data-artist]');
  if (ar) ar.onclick = () => { const r = artistByName.get(ar.dataset.artist.toLowerCase()); if (r) go('artist', r.name); };
}

/* ---------- Artists */
function artistList() {
  const q = query.toLowerCase();
  return artists.filter(r => !q || r.name.toLowerCase().includes(q)).sort(SORTS.artists[sortBy.artists][1]);
}
function renderArtists(el) {
  const list = artistList();
  el.innerHTML = `<div class="page">${scanStrip()}<div class="head"><h1>Artists</h1><span class="count">${query ? `${plural(list.length, 'result')} for “${esc(query)}”` : plural(list.length, 'artist')}</span></div>
    <div class="grid artists">${list.map((r, i) => `<div class="card" data-i="${i}">${cover(r.cover, 'round').replace('</div>', `<button class="card-play" aria-label="Play">${ICON.play}</button></div>`)}
    <div class="cn">${esc(r.name)}</div><div class="ca">${plural(r.albums.size, 'album')} · ${plural(r.tracks.length, 'song')}</div></div>`).join('')}</div></div>`;
  wireCards(el, i => list[i], r => go('artist', r.name), r => artistSongs(r), (e, r) => artistMenu(e, r));
}
function artistSongs(r) {
  return [...r.tracks].sort((a, b) => coll.compare(a.album, b.album) || (a.disc - b.disc) || (a.track - b.track));
}
function renderArtist(el) {
  const r = artistByName.get(String(view.arg).toLowerCase());
  if (!r) return go('artists');
  const songs = artistSongs(r);
  const als = [...r.albums].map(k => albumByKey.get(k)).filter(Boolean).sort((a, b) => (b.year - a.year) || coll.compare(a.name, b.name));
  const dur = songs.reduce((s, t) => s + t.duration, 0);
  el.innerHTML = `<div class="page">${albumHead('Artist', r.name, `${plural(als.length, 'album')} · ${plural(songs.length, 'song')}, ${fmtLong(dur)}`, cover(r.cover, 'round'), `<button class="btn" data-act="mix">${ICON.wave}Make a mix</button>`)}
    <div class="sec">Albums</div><div class="grid">${als.map((a, i) => `<div class="card" data-i="${i}">${cover(a.cover, 'r12').replace('</div>', `<button class="card-play" aria-label="Play">${ICON.play}</button></div>`)}<div class="cn">${esc(a.name)}</div><div class="ca">${a.year || plural(a.tracks.length, 'song')}</div></div>`).join('')}</div>
    <div class="sec">Songs</div></div>`;
  el.querySelector('.page').appendChild(plainList(songs));
  wireCards(el.querySelector('.grid'), i => als[i], a => go('album', a.key), a => a.tracks, (e, a) => albumMenu(e, a));
  wireHead(el, () => songs, e => artistMenu(e, r));
  el.querySelector('[data-act=mix]').onclick = () => makeMix('artist', r.name);
}

/* ---------- Playlists */
function plSongs(p) { return p.ids.map(id => byId.get(id)).filter(Boolean); }
function renderPlNav() {
  const nav = $('#plNav');
  nav.innerHTML = playlists.length ? playlists.map(p => `<button class="nav-item" data-pl="${p.id}">${ICON.list}<span>${esc(p.name)}</span></button>`).join('')
    : `<div class="pl-empty">No playlists yet</div>`;
  nav.querySelectorAll('[data-pl]').forEach(b => {
    b.onclick = () => go('playlist', b.dataset.pl);
    b.oncontextmenu = e => { e.preventDefault(); playlistMenu(e, playlists.find(p => p.id === b.dataset.pl)); };
  });
}
function mosaic(list) {
  const cs = [...new Set(list.map(t => t.cover).filter(Boolean))].slice(0, 4);
  if (cs.length < 4) return cover(cs[0], 'r12');
  return `<div class="cv r12 mosaic">${cs.map(c => cover(c, '')).join('')}</div>`;
}
function renderPlaylist(el) {
  const p = playlists.find(x => x.id === view.arg);
  if (!p) return go('songs');
  const songs = plSongs(p);
  const dur = songs.reduce((s, t) => s + t.duration, 0);
  el.innerHTML = `<div class="page">${albumHead('Playlist', p.name, songs.length ? `${plural(songs.length, 'song')}, ${fmtLong(dur)}` : 'Empty. Right-click any song → Add to playlist.', mosaic(songs))}</div>`;
  if (songs.length) el.querySelector('.page').appendChild(plainList(songs, { ctx: { playlist: p } }));
  wireHead(el, () => songs, e => playlistMenu(e, p));
}
function newPlaylist(thenAdd) {
  ask('New playlist', '', 'Create', name => {
    const p = { id: 'p' + Date.now().toString(36), name, ids: [] };
    playlists.push(p);
    if (thenAdd && thenAdd.length) { p.ids.push(...thenAdd); toast(`Added to ${name}`); }
    savePlaylists(); renderPlNav();
    if (!thenAdd) go('playlist', p.id);
  });
}
function addToPlaylist(p, ids) { p.ids.push(...ids); savePlaylists(); toast(`Added ${ids.length > 1 ? plural(ids.length, 'song') : ''} to ${p.name}`.replace('Added  to', 'Added to')); if (view.name === 'playlist' && view.arg === p.id) render(); }
function renamePlaylist(p) { ask('Rename playlist', p.name, 'Save', name => { p.name = name; savePlaylists(); renderPlNav(); render(); }); }
function deletePlaylist(p) {
  confirmBox(`Delete “${p.name}”?`, 'The songs stay in your library.', 'Delete', () => {
    playlists = playlists.filter(x => x !== p); savePlaylists(); renderPlNav();
    if (view.name === 'playlist' && view.arg === p.id) go('songs'); else render();
  });
}

/* ---------- Smart: lists that keep themselves up to date */
const AUTO_HINT = {
  favorites: 'Click the heart on any song to add it here.',
  most: 'Songs you play show up here. A play counts after half the song or 30 seconds.',
  recent: 'Songs you play show up here, newest first.',
  added: 'New music in your folders shows up here.',
  never: 'You have played everything. Nice.',
};
function songCount(n) { return plural(n, 'song'); }
function smartCard(i, name, songs, icon, sub) {
  const cv = songs.length ? mosaic(songs).replace(/<\/div>$/, `<button class="card-play" aria-label="Play">${ICON.play}</button></div>`) : `<div class="cv r12 sm-ph">${ICON[icon] || ICON.spark}</div>`;
  return `<div class="card" data-i="${i}">${cv}<div class="cn">${esc(name)}</div><div class="ca">${esc(sub != null ? sub : songCount(songs.length))}</div></div>`;
}
function renderSmart(el) {
  const fixed = Sm.FIXED.map(a => ({ a, songs: evalRule(a.rule) }));
  const mine = smartLists.map(p => ({ p, songs: evalRule(p.rule) }));
  const genres = Sm.genreLists(tracks), decades = Sm.decadeLists(tracks);
  const chips = list => list.map(a => `<button class="chip" data-k="${esc(a.key)}"><span>${esc(a.name)}</span><span class="chip-n">${a.count.toLocaleString()}</span></button>`).join('');
  el.innerHTML = `<div class="page">${scanStrip()}<div class="head"><h1>Smart</h1><span class="count">Updates by itself as you listen</span>
      <div class="actions"><button class="btn small primary" data-act="new">${ICON.plus}New smart playlist</button></div></div>
    <div class="grid sm-grid" data-g="auto">${fixed.map((x, i) => smartCard(i, x.a.name, x.songs, AUTO_ICON[x.a.kind], x.songs.length ? null : 'Empty for now')).join('')}</div>
    <div class="sec">Your smart playlists</div>
    <div class="grid sm-grid" data-g="mine">${mine.map((x, i) => smartCard(i, x.p.name, x.songs, 'spark', `${songCount(x.songs.length)} · ${Sm.ruleSummary(x.p.rule)}`)).join('')}
      <button class="sm-new" data-act="new"><span class="sm-new-in">${ICON.plus}<b>New smart playlist</b><span>Pick songs by genre, artist, year, plays, likes…</span></span></button></div>
    ${genres.length ? `<div class="sec">Genres</div><div class="chips">${chips(genres)}</div>` : ''}
    ${decades.length ? `<div class="sec">Decades</div><div class="chips">${chips(decades)}</div>` : ''}
    ${!genres.length && !decades.length ? `<div class="sec">Genres and decades</div><div class="muted small">Your songs have no genre or year tags yet. Lists appear here as soon as they do.</div>` : ''}
  </div>`;
  el.querySelectorAll('[data-act=new]').forEach(b => b.onclick = () => openBuilder(null));
  wireCards(el.querySelector('[data-g=auto]'), i => fixed[i], x => go('auto', x.a.key), x => x.songs, (e, x) => smartMenu(e, { auto: x.a }));
  wireCards(el.querySelector('[data-g=mine]'), i => mine[i], x => go('smartpl', x.p.id), x => x.songs, (e, x) => smartMenu(e, { mine: x.p }));
  el.querySelectorAll('.chip').forEach(c => {
    c.onclick = () => go('auto', c.dataset.k);
    c.oncontextmenu = e => { e.preventDefault(); const a = autoDef(c.dataset.k); if (a) smartMenu(e, { auto: a }); };
  });
}
function smartSongsHead(kicker, name, sub, songs, extra) {
  const dur = songs.reduce((s, t) => s + (t.duration || 0), 0);
  return albumHead(kicker, name, `${sub}<span class="dsub2">${songs.length ? `${songCount(songs.length)}, ${fmtLong(dur)}` : 'No songs right now'}</span>`, songs.length ? mosaic(songs) : `<div class="cv r12 sm-ph">${ICON.spark}</div>`, extra, { empty: !songs.length });
}
function ruleChips(rule) {
  const sortName = Sm.SORTS[rule.s];
  return `<span class="rchips">${rule.c.length > 1 ? `<span class="rchip k">${rule.m === 'all' ? 'All of' : 'Any of'}</span>` : ''}${rule.c.map(c => `<span class="rchip">${esc(Sm.condSummary(c))}</span>`).join('') || '<span class="rchip">All songs</span>'}<span class="rchip k">${esc(sortName)}${rule.l ? ` · top ${rule.l}` : ''}</span></span>`;
}
function renderAuto(el) {
  const a = autoDef(view.arg);
  if (!a) return go('smart');
  const songs = evalRule(a.rule);
  const kicker = a.kind === 'genre' ? 'Genre' : a.kind === 'decade' ? 'Decade' : 'Smart playlist';
  const sub = { favorites: 'Songs you liked, newest first', most: 'Your 50 most played songs', recent: 'The last 50 songs you played', added: 'The 50 newest songs in your folders', never: 'Songs you haven\u2019t played yet', genre: `Every song tagged ${esc(a.name)}`, decade: `Songs from ${a.rule.c[0].a} to ${a.rule.c[0].b}` }[a.kind];
  el.innerHTML = `<div class="page">${smartSongsHead(kicker, a.name, sub, songs)}</div>`;
  smartBody(el, songs, AUTO_HINT[a.kind] || 'Nothing matches right now.', a.kind === 'favorites' ? ICON.heart : ICON.spark);
  wireHead(el, () => songs, e => smartMenu(e, { auto: a }));
}
function renderSmartPl(el) {
  const p = smartLists.find(x => x.id === view.arg);
  if (!p) return go('smart');
  const songs = evalRule(p.rule);
  el.innerHTML = `<div class="page">${smartSongsHead('Smart playlist', p.name, ruleChips(p.rule), songs, `<button class="btn" data-act="edit">${ICON.edit}Edit rules</button>`)}</div>`;
  smartBody(el, songs, 'No songs match these rules right now. It fills up by itself when they do.', ICON.spark);
  wireHead(el, () => songs, e => smartMenu(e, { mine: p }));
  el.querySelector('[data-act=edit]').onclick = () => openBuilder(p);
}
function smartBody(el, songs, hint, icon) {
  const page = el.querySelector('.page');
  if (songs.length) page.appendChild(plainList(songs));
  else page.insertAdjacentHTML('beforeend', `<div class="sm-empty"><div class="big">${icon}</div><div class="muted">${esc(hint)}</div></div>`);
}
function smartMenu(e, o) {
  const name = o.auto ? o.auto.name : o.mine.name;
  const songs = () => evalRule(o.auto ? o.auto.rule : o.mine.rule);
  const items = [
    { label: 'Play', icon: ICON.play, run: () => playList(songs().map(t => t.id), 0, { shuffle: false }) },
    { label: 'Add to queue', icon: ICON.plus, run: () => addToQueue(songs().map(t => t.id)) },
    { label: 'Save as playlist\u2026', icon: ICON.list, run: () => saveAsPlaylist(name, songs().map(t => t.id)) },
  ];
  if (o.auto) items.push('-', { label: 'Make my own from this\u2026', icon: ICON.spark, run: () => openBuilder(null, { name: o.auto.name, rule: o.auto.rule }) });
  if (o.mine) items.push('-', { label: 'Edit rules\u2026', icon: ICON.edit, run: () => openBuilder(o.mine) }, { label: 'Delete', danger: true, run: () => deleteSmart(o.mine) });
  openMenu(e, items);
}
function saveAsPlaylist(name, ids) {
  if (!ids.length) return toast('Nothing to save yet');
  ask('Save as playlist', name, 'Save', n => {
    const p = { id: 'p' + Date.now().toString(36), name: n, ids: ids.slice() };
    playlists.push(p); savePlaylists(); renderPlNav();
    toast(`Saved “${n}” · ${songCount(ids.length)}`);
  });
}
function deleteSmart(p) {
  confirmBox(`Delete “${p.name}”?`, 'Only the rules are deleted. The songs stay in your library.', 'Delete', () => {
    smartLists = Sm.remove(smartLists, p.id); saveSmart(); renderSmNav();
    if (view.name === 'smartpl' && view.arg === p.id) go('smart'); else render();
  });
}
function renderSmNav() {
  const nav = $('#smNav'); if (!nav) return;
  const fav = tracks.filter(t => isLiked(t.id)).length;
  nav.innerHTML = `<button class="nav-item" data-sm="auto:favorites" data-kind="auto">${ICON.heart}<span>Favorites</span><span class="nav-count">${fav ? fav.toLocaleString() : ''}</span></button>`
    + smartLists.map(p => `<button class="nav-item" data-sm="${esc(p.id)}" data-kind="mine">${ICON.spark}<span>${esc(p.name)}</span></button>`).join('');
  nav.querySelectorAll('[data-sm]').forEach(b => {
    b.onclick = () => go(b.dataset.kind === 'auto' ? 'auto' : 'smartpl', b.dataset.sm);
    b.oncontextmenu = e => { e.preventDefault(); if (b.dataset.kind === 'auto') smartMenu(e, { auto: autoDef(b.dataset.sm) }); else smartMenu(e, { mine: smartLists.find(p => p.id === b.dataset.sm) }); };
  });
  const v = view.name;
  nav.querySelectorAll('[data-sm]').forEach(n => n.classList.toggle('active', (v === 'auto' || v === 'smartpl') && n.dataset.sm === view.arg));
}

/* ---------- the rule builder: "New smart playlist" */
function suggestions(f) {
  const seen = new Map();
  const add = v => { v = String(v || '').trim(); if (v && !seen.has(v.toLowerCase())) seen.set(v.toLowerCase(), v); };
  if (f === 'genre') Sm.genreLists(tracks).forEach(g => add(g.name));
  else if (f === 'artist') artists.forEach(r => add(r.name));
  else if (f === 'album') albums.forEach(a => add(a.name));
  return [...seen.values()].sort((a, b) => coll.compare(a, b)).slice(0, 400);
}
function openBuilder(existing, preset) {
  const base = existing ? { name: existing.name, rule: existing.rule } : preset || { name: '', rule: { m: 'all', c: [Sm.newCond('genre')], s: 'artist', l: 0 } };
  const st = { name: base.name, rule: JSON.parse(JSON.stringify(Sm.cleanRule(base.rule))) };
  if (!existing && !st.rule.c.length && !preset) st.rule.c.push(Sm.newCond('genre'));
  const lists = { genre: suggestions('genre'), artist: suggestions('artist'), album: suggestions('album') };
  const datalists = Object.entries(lists).map(([k, v]) => `<datalist id="dl-${k}">${v.map(x => `<option value="${esc(x)}">`).join('')}</datalist>`).join('');
  const sel = (cls, opts, cur) => `<span class="dd"><select class="${cls}">${opts.map(([v, l]) => `<option value="${esc(v)}"${String(v) === String(cur) ? ' selected' : ''}>${esc(l)}</option>`).join('')}</select></span>`;
  const valueHTML = c => {
    const kind = Sm.FIELDS[c.f].kind;
    if (kind === 'text') return `<input class="rb-t" list="dl-${c.f}" value="${esc(c.t)}" placeholder="${c.f === 'genre' ? 'e.g. Hip-Hop' : c.f === 'artist' ? 'Artist name' : 'Album name'}" spellcheck="false">`;
    if (kind === 'bool' || c.o === 'never') return '';
    const num = (k, v, unit, step, w) => `<input class="rb-n" data-k="${k}" type="number" min="0" step="${step || 1}" value="${v}" style="width:${w || 74}px">${unit ? `<span class="rb-u">${unit}</span>` : ''}`;
    if (c.f === 'duration') {
      const m = v => Math.round(v / 6) / 10;
      return c.o === 'between' ? `${num('a', m(c.a), '', 0.5)}<span class="rb-u">to</span>${num('b', m(c.b), 'min', 0.5)}` : num('a', m(c.a), 'min', 0.5);
    }
    if (kind === 'date') return num('a', c.a, c.a === 1 ? 'day' : 'days');
    const unit = c.f === 'plays' ? 'plays' : '';
    if (c.o === 'between') return `${num('a', c.a, '', 1, c.f === 'year' ? 84 : 74)}<span class="rb-u">to</span>${num('b', c.b, unit, 1, c.f === 'year' ? 84 : 74)}`;
    return num('a', c.a, unit, 1, c.f === 'year' ? 84 : 74);
  };
  const rowHTMLb = (c, i) => `<div class="rb-row" data-i="${i}">${sel('rb-f', Sm.FIELD_ORDER.map(f => [f, Sm.FIELDS[f].label]), c.f)}${sel('rb-o', Sm.opsFor(c.f).map(o => [o, Sm.opLabel(o, c.f)]), c.o)}<span class="rb-v">${valueHTML(c)}</span>
    <button class="icon-btn xs rb-x" title="Remove rule" aria-label="Remove rule">${ICON.x}</button></div>`;
  modal(`<h3>${existing ? 'Edit smart playlist' : 'New smart playlist'}</h3>
    <input class="rb-name" maxlength="80" placeholder="Name (optional)" value="${esc(st.name)}">
    <div class="rb-match"><span>Songs that match</span><div class="seg" id="rbMatch"><button data-m="all">all</button><button data-m="any">any</button></div><span>of these rules</span></div>
    <div class="rb-rules" id="rbRules"></div>
    <button class="link rb-add" id="rbAdd">${ICON.plus}Add rule</button>
    <div class="rb-opts"><label>Sort by ${sel('rb-s', Object.entries(Sm.SORTS), st.rule.s)}</label>
      <label class="rb-lim"><span class="chk"><input type="checkbox" id="rbLimOn" ${st.rule.l ? 'checked' : ''}><span></span></span>Limit to <input class="rb-n" id="rbLim" type="number" min="1" step="1" value="${st.rule.l || 25}" style="width:70px"> songs</label></div>
    <div class="rb-prev" id="rbPrev"></div>
    <div class="actions">${existing ? `<button class="btn danger rb-del" data-del>${ICON.trash}Delete</button><span class="spacer"></span>` : ''}<button class="btn" data-x>Cancel</button><button class="btn primary" data-ok>${existing ? 'Save' : 'Create'}</button></div>${datalists}`, (card, close) => {
    const rulesEl = card.querySelector('#rbRules');
    const draw = () => { rulesEl.innerHTML = st.rule.c.map(rowHTMLb).join('') || '<div class="rb-none muted small">No rules: every song matches. Add one to narrow it down.</div>'; preview(); };
    const preview = () => {
      st.rule.l = card.querySelector('#rbLimOn').checked ? Math.max(1, Math.floor(+card.querySelector('#rbLim').value || 0)) : 0;
      card.querySelector('#rbLim').disabled = !card.querySelector('#rbLimOn').checked;
      card.querySelectorAll('#rbMatch button').forEach(b => b.classList.toggle('on', b.dataset.m === st.rule.m));
      card.querySelector('.rb-match').classList.toggle('one', st.rule.c.length < 2);
      const songs = evalRule(st.rule);
      const total = Sm.count(Object.assign({}, st.rule, { l: 0 }), tracks, stats, smartNow());
      card.querySelector('#rbPrev').innerHTML = `<b>${songCount(songs.length)}</b>${st.rule.l && total > songs.length ? ` <span class="muted">of ${total.toLocaleString()} that match</span>` : ''}${songs.length ? `<span class="rb-eg">${songs.slice(0, 3).map(t => esc(t.title)).join(', ')}${songs.length > 3 ? '\u2026' : ''}</span>` : '<span class="rb-eg">Nothing matches yet. It fills up by itself when songs do.</span>'}`;
    };
    rulesEl.addEventListener('change', e => {
      const row = e.target.closest('.rb-row'); if (!row) return;
      const i = +row.dataset.i, c = st.rule.c[i];
      if (e.target.classList.contains('rb-f')) { st.rule.c[i] = Sm.newCond(e.target.value); draw(); return; }
      if (e.target.classList.contains('rb-o')) {
        c.o = e.target.value;
        if (c.o === 'between' && c.f === 'plays' && !c.b) c.b = Math.max(c.a + 5, 10);
        if (c.o === 'between' && c.f === 'duration' && c.b <= c.a) c.b = c.a + 120;
        draw(); return;
      }
    });
    rulesEl.addEventListener('input', e => {
      const row = e.target.closest('.rb-row'); if (!row) return;
      const c = st.rule.c[+row.dataset.i];
      if (e.target.classList.contains('rb-t')) c.t = e.target.value;
      else if (e.target.classList.contains('rb-n')) { const v = Math.max(0, +e.target.value || 0); c[e.target.dataset.k] = c.f === 'duration' ? Math.round(v * 60) : Math.round(v); }
      preview();
    });
    rulesEl.addEventListener('click', e => { const x = e.target.closest('.rb-x'); if (!x) return; st.rule.c.splice(+x.closest('.rb-row').dataset.i, 1); draw(); });
    card.querySelector('#rbAdd').onclick = () => {
      const used = new Set(st.rule.c.map(c => c.f));
      const next = ['genre', 'year', 'plays', 'artist', 'liked', 'added', 'lastPlayed', 'duration', 'album'].find(f => !used.has(f)) || 'genre';
      st.rule.c.push(Sm.newCond(next)); draw();
      const ins = rulesEl.querySelectorAll('.rb-row'); const last = ins[ins.length - 1]; if (last) { const f = last.querySelector('input'); if (f) f.focus(); }
    };
    card.querySelectorAll('#rbMatch button').forEach(b => b.onclick = () => { st.rule.m = b.dataset.m; preview(); });
    card.querySelector('.rb-s').onchange = e => { st.rule.s = e.target.value; preview(); };
    card.querySelector('#rbLimOn').onchange = preview;
    card.querySelector('#rbLim').oninput = preview;
    const ok = () => {
      const name = card.querySelector('.rb-name').value.trim() || (st.rule.c.length ? Sm.condSummary(st.rule.c[0]) : 'All songs');
      const rule = Sm.cleanRule(st.rule);
      if (existing) { smartLists = Sm.update(smartLists, existing.id, name, rule); saveSmart(); close(); renderSmNav(); render(); toast('Smart playlist updated'); return; }
      const r = Sm.create(smartLists, name, rule, Date.now());
      smartLists = r.list; saveSmart(); close(); renderSmNav(); go('smartpl', r.item.id);
      toast(`Created “${r.item.name}”`);
    };
    card.querySelector('[data-ok]').onclick = ok;
    card.querySelector('[data-x]').onclick = close;
    const del = card.querySelector('[data-del]'); if (del) del.onclick = () => { close(); deleteSmart(existing); };
    card.addEventListener('keydown', e => { if (e.key === 'Escape') { e.stopPropagation(); close(); } if (e.key === 'Enter' && e.target.matches('.rb-name')) ok(); });
    draw();
    if (!existing) card.querySelector('.rb-name').focus();
  }, 'wide');
}

/* ---------- Make a mix: from a song or an artist; same artist, then the same genre and era */
let mixSeq = 0;
function makeMix(kind, ref, again) {
  const rnd = Math.floor(Math.random() * 1e9) + 1;
  let m;
  if (kind === 'song') { const t = byId.get(ref); if (!t) return; m = Sm.mixFromSong(t, tracks, stats, rnd); }
  else m = Sm.mixFromArtist(ref, tracks, stats, rnd);
  if (m.ids.length < 2) return toast('Not enough similar songs for a mix yet');
  const id = 'm' + (++mixSeq);
  mixes.set(id, Object.assign(m, { kind, ref, rnd }));
  if (again && view.name === 'mix') { view = { name: 'mix', arg: id }; render(); } else go('mix', id);
}
function renderMix(el) {
  const m = mixes.get(view.arg);
  if (!m) return go('smart');
  const songs = m.ids.map(id => byId.get(id)).filter(Boolean);
  const sub = m.kind === 'song' ? `From “${esc(m.seed)}”: the same artist, then the same genre and era` : `From ${esc(m.seed)}: their songs, then the same genre and era`;
  el.innerHTML = `<div class="page">${smartSongsHead('Mix', m.name, sub, songs, `<button class="btn" data-act="save">${ICON.list}Save as playlist</button><button class="btn" data-act="again">${ICON.refresh}New mix</button>`)}</div>`;
  el.querySelector('.page').appendChild(plainList(songs));
  wireHead(el, () => songs, e => songsMenu(e, songs));
  el.querySelector('[data-act=save]').onclick = () => saveAsPlaylist(m.name, m.ids);
  el.querySelector('[data-act=again]').onclick = () => makeMix(m.kind, m.ref, true);
}

/* ---------- Now playing */
function renderNow(el) {
  const t = byId.get(Q.current());
  if (!t) { el.innerHTML = `<div class="empty"><div class="big">${ICON.note}</div><h2>Nothing playing</h2><div class="muted">Pick a song from your library.</div></div>`; return; }
  const next = Q.upcoming().slice(0, 3).map(u => byId.get(u.id)).filter(Boolean);
  const lk = isLiked(t.id);
  el.innerHTML = `<div class="nowv"><div class="now">${cover(t.cover, 'r12')}<div class="now-t">${esc(t.title)}</div>
    <div class="now-a"><b data-artist="${esc(t.artist)}">${esc(t.artist)}</b> · <b data-album="${esc(albumKey(t))}">${esc(t.album)}</b></div>
    <div class="now-acts"><button class="btn small lkb${lk ? ' on' : ''}" data-act="like">${ICON.heart}<span>${lk ? 'Liked' : 'Like'}</span></button><button class="btn small" data-act="mix">${ICON.wave}Make a mix</button></div>
    ${next.length ? `<div class="now-next"><div class="sec">Up next</div>${next.map((n, i) => `<div class="trow" data-q="${Q.pos + 1 + i}"><span class="tt">${cover(n.cover, 'r6')}<span class="tl"><span class="t1">${esc(n.title)}</span><span class="t2">${esc(n.artist)}</span></span></span><span class="td">${fmt(n.duration)}</span></div>`).join('')}</div>` : ''}</div></div>`;
  const ar = el.querySelector('[data-artist]'); ar.onclick = () => { const r = artistByName.get(t.artist.toLowerCase()); if (r) go('artist', r.name); };
  const al = el.querySelector('[data-album]'); al.onclick = () => { if (albumByKey.has(albumKey(t))) go('album', albumKey(t)); };
  el.querySelectorAll('[data-q]').forEach(r => r.ondblclick = r.onclick = () => { Q.jump(+r.dataset.q); load(Q.current()); });
  el.querySelector('[data-act=like]').onclick = () => toggleLike(t.id);
  el.querySelector('[data-act=mix]').onclick = () => { if (byId.has(t.id) && !t._dropped) makeMix('song', t.id); else toast('Mixes are made from songs in your library'); };
}

/* ---------- Settings */
function renderSettings(el) {
  const f = settings.folders || [];
  const last = settings.lastScan ? new Date(settings.lastScan).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : 'never';
  el.innerHTML = `<div class="page narrow"><h1>Settings</h1>
    <h2 class="sec">Library</h2>
    <div class="flist">${f.map((p, i) => `<div class="frow"><span class="fic">${ICON.folder}</span><span class="fp" title="${esc(p)}">&lrm;${esc(p)}&lrm;</span>${f.length > 1 ? `<button class="icon-btn" data-rm="${i}" title="Remove folder" aria-label="Remove folder">${ICON.x}</button>` : ''}</div>`).join('')}</div>
    <div class="actions lib-actions"><button class="btn small" data-act="change">Change folder</button><button class="btn small" data-act="add">${ICON.plus}Add another folder</button><button class="btn small" data-act="rescan">${ICON.refresh}Rescan</button></div>
    <div class="small muted status-line" id="libStatus">${plural(tracks.length, 'song')} · ${plural(albums.length, 'album')} · last scanned ${esc(last)}. Folders are watched; new music shows up on its own.</div>
    <h2 class="sec">Appearance</h2>
    <div class="row"><div><div class="row-t">Theme</div><div class="row-s">Follows Windows unless you pick one</div></div>
      <div class="seg" id="themeSeg">${['system', 'light', 'dark'].map(m => `<button data-m="${m}" class="${(settings.theme || 'system') === m ? 'on' : ''}">${m[0].toUpperCase() + m.slice(1)}</button>`).join('')}</div></div>
    <h2 class="sec">Playback</h2>
    <div class="row"><div><div class="row-t">Gapless playback</div><div class="row-s">Loads the next song early so albums flow without a pause</div></div>
      <label class="switch"><input type="checkbox" id="gapless" ${settings.gapless !== false ? 'checked' : ''}><span></span></label></div>
    <h2 class="sec">Keyboard</h2>
    <div class="keys"><span><kbd>Space</kbd></span><span>Play / pause</span><span><kbd>Ctrl</kbd> <kbd>←</kbd> <kbd>→</kbd></span><span>Previous / next song</span>
      <span><kbd>←</kbd> <kbd>→</kbd></span><span>Back / forward 5 seconds</span><span><kbd>Ctrl</kbd> <kbd>F</kbd></span><span>Search</span><span><kbd>Ctrl</kbd> <kbd>B</kbd></span><span>Sidebar</span>
      <span>Media keys</span><span>Play, pause, next, previous</span></div>
    <h2 class="sec">About</h2>
    <div class="about"><img src="icon.png" alt=""><div><div class="row-t">Paradox Music ${esc(B.version)}</div><div class="row-s">Works fully offline. No account, no network, no telemetry.</div></div></div>
  </div>`;
  el.querySelector('[data-act=change]').onclick = () => changeFolder(true);
  el.querySelector('[data-act=add]').onclick = () => changeFolder(false);
  el.querySelector('[data-act=rescan]').onclick = () => rescan(true);
  el.querySelectorAll('[data-rm]').forEach(b => b.onclick = () => { settings.folders.splice(+b.dataset.rm, 1); saveSettings().then(() => rescan(true)); render(); });
  el.querySelectorAll('#themeSeg button').forEach(b => b.onclick = () => { settings.theme = b.dataset.m; applyTheme(); saveSettings(); render(); });
  el.querySelector('#gapless').onchange = e => { settings.gapless = e.target.checked; saveSettings(); if (!settings.gapless) dropPreload(); else preloadNext(); };
}
function applyTheme() { document.documentElement.dataset.theme = (demo && demo.theme) || settings.theme || 'system'; }

/* ------------------------------------------------------------------ menus & dialogs */
function openMenu(e, items) {
  const m = $('#menu');
  const build = list => {
    m.innerHTML = list.map((it, i) => it === '-' ? '<div class="msep"></div>' : it.head ? `<div class="mhead">${esc(it.head)}</div>` : it.scroll ? `<div class="mscroll">${it.scroll.map((s, j) => `<button class="mi" data-s="${i}:${j}">${s.icon || ''}<span>${esc(s.label)}</span></button>`).join('')}</div>`
      : `<button class="mi${it.danger ? ' danger' : ''}" data-i="${i}">${it.icon || ''}<span>${esc(it.label)}</span>${it.sub ? '<span class="ck">›</span>' : it.checked ? `<span class="ck">${ICON.check}</span>` : ''}</button>`).join('');
    m.querySelectorAll('[data-i]').forEach(b => b.onclick = ev => { ev.stopPropagation(); const it = list[+b.dataset.i]; if (it.sub) return build(it.sub()); closeMenu(); it.run && it.run(); });
    m.querySelectorAll('[data-s]').forEach(b => b.onclick = ev => { ev.stopPropagation(); const [i, j] = b.dataset.s.split(':').map(Number); closeMenu(); list[i].scroll[j].run(); });
    place();
  };
  const place = () => {
    m.hidden = false;
    const r = m.getBoundingClientRect();
    let x = e.clientX, y = e.clientY;
    if (x + r.width > innerWidth - 8) x = innerWidth - r.width - 8;
    if (y + r.height > innerHeight - 8) y = Math.max(8, innerHeight - r.height - 8);
    m.style.left = x + 'px'; m.style.top = y + 'px';
  };
  build(items);
}
function closeMenu() { $('#menu').hidden = true; }
function playlistSub(ids) {
  return () => [{ head: 'Add to playlist' }, { label: 'New playlist…', icon: ICON.plus, run: () => newPlaylist(ids) }, ...(playlists.length ? ['-', { scroll: playlists.map(p => ({ label: p.name, icon: ICON.list, run: () => addToPlaylist(p, ids) })) }] : [])];
}
function songsMenu(e, list, extra) {
  const ids = list.map(t => t.id);
  openMenu(e, [
    { label: 'Play', icon: ICON.play, run: () => playList(ids, 0, { shuffle: false }) },
    { label: 'Play next', icon: ICON.queue, run: () => { playNext(ids); } },
    { label: 'Add to queue', icon: ICON.plus, run: () => { addToQueue(ids); } },
    '-', { label: 'Add to playlist', icon: ICON.list, sub: playlistSub(ids) }, ...(extra || []),
  ]);
}
function albumMenu(e, a) { songsMenu(e, a.tracks); }
function artistMenu(e, r) { songsMenu(e, artistSongs(r), [{ label: 'Make a mix', icon: ICON.wave, run: () => makeMix('artist', r.name) }]); }
function trackMenu(e, list, i, ctx) {
  const t = list[i];
  const items = [
    { label: 'Play', icon: ICON.play, run: () => playList(list.map(x => x.id), i) },
    { label: 'Play next', icon: ICON.queue, run: () => playNext([t.id]) },
    { label: 'Add to queue', icon: ICON.plus, run: () => addToQueue([t.id]) },
    '-', { label: isLiked(t.id) ? 'Unlike' : 'Like', icon: ICON.heart, run: () => toggleLike(t.id) },
    { label: 'Add to playlist', icon: ICON.list, sub: playlistSub([t.id]) },
    { label: 'Make a mix', icon: ICON.wave, run: () => makeMix('song', t.id) },
  ];
  if (ctx && ctx.playlist) items.push({ label: 'Remove from this playlist', icon: ICON.x, run: () => { const p = ctx.playlist; const k = p.ids.indexOf(t.id); if (k >= 0) p.ids.splice(k, 1); savePlaylists(); render(); } });
  items.push('-');
  if (albumByKey.has(albumKey(t)) && !(view.name === 'album')) items.push({ label: 'Go to album', icon: ICON.albums, run: () => go('album', albumKey(t)) });
  if (artistByName.has(t.artist.toLowerCase()) && !(view.name === 'artist')) items.push({ label: 'Go to artist', icon: ICON.artists, run: () => go('artist', t.artist) });
  if (items[items.length - 1] === '-') items.pop();
  openMenu(e, items);
}
function playlistMenu(e, p) {
  openMenu(e, [
    { label: 'Play', icon: ICON.play, run: () => playList(plSongs(p).map(t => t.id), 0, { shuffle: false }) },
    { label: 'Add to queue', icon: ICON.plus, run: () => addToQueue(plSongs(p).map(t => t.id)) },
    '-', { label: 'Rename…', run: () => renamePlaylist(p) }, { label: 'Delete', danger: true, run: () => deletePlaylist(p) },
  ]);
}
function sortMenu(e) {
  const k = view.name; const opts = SORTS[k]; if (!opts) return;
  const r = $('#sortBtn').getBoundingClientRect();
  openMenu({ clientX: r.right - 210, clientY: r.bottom + 6 }, [{ head: 'Sort by' }, ...Object.entries(opts).map(([id, [label]]) => ({ label, checked: sortBy[k] === id, run: () => { sortBy[k] = id; render(); persistSoon(); } }))]);
}
function modal(html, onOpen, cls) {
  const m = $('#modal'), c = $('#modalCard');
  c.className = 'modal-card' + (cls ? ' ' + cls : '');
  c.innerHTML = html; m.hidden = false;
  const close = () => { m.hidden = true; };
  m.onclick = ev => { if (ev.target === m) close(); };
  onOpen(c, close);
}
function ask(title, value, okLabel, done) {
  modal(`<h3>${esc(title)}</h3><input id="askIn" maxlength="80" value="${esc(value)}" placeholder="Playlist name"><div class="actions"><button class="btn" data-x>Cancel</button><button class="btn primary" data-ok>${esc(okLabel)}</button></div>`, (c, close) => {
    const inp = c.querySelector('input'); inp.focus(); inp.select();
    const ok = () => { const v = inp.value.trim(); if (!v) return inp.focus(); close(); done(v); };
    c.querySelector('[data-ok]').onclick = ok; c.querySelector('[data-x]').onclick = close;
    inp.onkeydown = ev => { if (ev.key === 'Enter') ok(); if (ev.key === 'Escape') close(); };
  });
}
function confirmBox(title, text, okLabel, done) {
  modal(`<h3>${esc(title)}</h3><p>${esc(text)}</p><div class="actions"><button class="btn" data-x>Cancel</button><button class="btn primary" data-ok>${esc(okLabel)}</button></div>`, (c, close) => {
    c.querySelector('[data-ok]').onclick = () => { close(); done(); }; c.querySelector('[data-x]').onclick = close; c.querySelector('[data-ok]').focus();
  });
}

/* ------------------------------------------------------------------ player */
const audios = [$('#a0'), $('#a1')];
let ai = 0;                       // index of the element playing now
const A = () => audios[ai], N = () => audios[1 - ai];
let curId = null, preId = null, errStreak = 0, volume = 0.8, muted = false, seeking = false, pendingSeek = 0;
// Play counting: a song counts after half of it, or 30 s, of real listening (paused time and seeking don't count).
const counter = new Sm.PlayCounter(30000, 0.5);
const clock = () => performance.now();
function countBegin(id, playing) { const t = byId.get(id); recordPlay(counter.begin(id, t ? (t.duration || 0) * 1000 : 0, !!playing, clock())); }
function countCheck() { recordPlay(counter.check(clock())); }
const player = $('#player');

function curve(v) { return v * v; }  // perceived loudness
function applyVolume() { for (const a of audios) { a.volume = muted ? 0 : curve(volume); } setRange($('#vol'), muted ? 0 : volume * 100, 100); $('#volBtn').classList.toggle('muted', muted || volume === 0); }
function setRange(r, v, max) { r.value = v; r.style.setProperty('--p', (max ? (v / max) * 100 : 0) + '%'); }

function playList(ids, start, opts) {
  ids = ids.filter(id => byId.has(id));
  if (!ids.length) return;
  Q.set(ids, start, opts);
  syncToggles();
  load(Q.current());
}
function playNext(ids) { const was = Q.current(); Q.playNext(ids); if (!was) load(Q.current()); else { preloadNext(); refreshQueue(); } toast(ids.length > 1 ? `${plural(ids.length, 'song')} will play next` : 'Plays next'); }
function addToQueue(ids) { const was = Q.current(); Q.add(ids); if (!was) load(Q.current()); else { preloadNext(); refreshQueue(); } toast(ids.length > 1 ? `Added ${plural(ids.length, 'song')} to the queue` : 'Added to the queue'); }

function load(id, opts) {
  opts = opts || {};
  const t = byId.get(id);
  if (!t) { curId = null; updateNow(); return; }
  curId = id;
  const autoplay = opts.autoplay !== false;
  countBegin(id, false);  // the clock starts on the 'play' event
  if (preId === id && N().getAttribute('src')) {
    const old = A(); ai = 1 - ai; old.pause(); old.removeAttribute('src'); old.load();
  } else {
    N().pause(); N().removeAttribute('src');
    if (!(demo && demo.noaudio)) A().src = fileSrc(t.path);
  }
  preId = null;
  const a = A();
  pendingSeek = opts.at || 0;
  if (pendingSeek && a.readyState >= 1) { a.currentTime = pendingSeek; pendingSeek = 0; }
  applyVolume();
  if (autoplay) a.play().catch(err => console.warn('play', err));
  updateNow(); mediaMeta(t); persistSoon();
}

function toggle() {
  if (!curId) { const ids = (view.name === 'songs' ? songList() : tracks).map(t => t.id); if (Q.length) return load(Q.current()); return playList(ids, 0); }
  const a = A(); if (a.paused) a.play().catch(() => {}); else a.pause();
}
function next() { const id = Q.next(false); if (id) load(id); }
function prev() { if (A().currentTime > 3 || Q.pos <= 0 && Q.repeat === 'off') { A().currentTime = 0; return; } const id = Q.prev(); if (id) load(id); }
function ended() {
  errStreak = 0;
  countCheck();
  const id = Q.next(true);
  if (!id) { A().currentTime = 0; A().pause(); updateState(); return; }
  if (id === curId && Q.repeat === 'one') { countBegin(id, false); A().currentTime = 0; A().play(); return; }
  load(id);
}
function dropPreload() { N().pause(); N().removeAttribute('src'); preId = null; }
function preloadNext() {
  if (settings.gapless === false || !curId) return;
  const id = Q.peekNext();
  if (!id || id === curId) { dropPreload(); return; }
  if (id === preId) return;
  const t = byId.get(id); if (!t) return;
  const n = N(); n.src = fileSrc(t.path); n.preload = 'auto'; n.load(); n.volume = muted ? 0 : curve(volume);
  preId = id;
}

for (const [i, a] of audios.entries()) {
  const mine = () => audios[ai] === a;
  a.addEventListener('ended', () => { if (mine()) ended(); });
  a.addEventListener('loadedmetadata', () => { if (mine() && pendingSeek) { a.currentTime = Math.min(pendingSeek, a.duration || pendingSeek); pendingSeek = 0; } if (mine()) { if (counter.id === curId && a.duration > 0 && isFinite(a.duration)) counter.setDuration(a.duration * 1000); tick(); } });
  a.addEventListener('timeupdate', () => { if (mine()) { tick(); if (!a.paused) countCheck(); } });
  a.addEventListener('playing', () => { if (mine()) recordPlay(counter.setPlaying(true, clock())); });
  a.addEventListener('waiting', () => { if (mine()) recordPlay(counter.setPlaying(false, clock())); });
  a.addEventListener('play', () => { if (mine()) { errStreak = 0; updateState(); setTimeout(preloadNext, 1500); } });
  a.addEventListener('pause', () => { if (mine()) { recordPlay(counter.setPlaying(false, clock())); updateState(); persistSoon(); } });
  a.addEventListener('seeked', () => { if (mine()) mediaState(); });
  a.addEventListener('error', () => {
    if (!mine()) { if (preId) preId = null; return; }
    const t = byId.get(curId);
    toast(`Can't play “${t ? t.title : 'this file'}”`);
    if (++errStreak < 5 && Q.length > 1) { const id = Q.next(false); if (id) load(id); } else { updateState(); }
  });
  void i;
}

let lastPersist = 0;
function tick() {
  const a = A(), d = a.duration || (byId.get(curId) || {}).duration || 0;
  if (!seeking) { setRange($('#seek'), d ? (a.currentTime / d) * 1000 : 0, 1000); $('#curTime').textContent = fmt(a.currentTime); }
  $('#durTime').textContent = fmt(d);
  if (!a.paused && Date.now() - lastPersist > 5000) persistNow();
  if (!a.paused && !preId && d && d - a.currentTime < 8) preloadNext();  // short songs: load the next one in time
}
function updateState() {
  const playing = curId && !A().paused;
  player.dataset.state = !curId ? 'empty' : playing ? 'playing' : 'paused';
  app.dataset.playing = playing ? '1' : '0';
  $('#playBtn').title = playing ? 'Pause (Space)' : 'Play (Space)';
  mediaState();
}
function updateNow() {
  const t = byId.get(curId);
  $('#npCover').innerHTML = t ? cover(t.cover, 'r8') : cover(null, 'r8');
  $('#npTitle').textContent = t ? t.title : 'Nothing playing';
  $('#npArtist').textContent = t ? t.artist : 'Pick a song to start';
  document.title = t ? `${t.title} · ${t.artist}` : 'Paradox Music';
  updateState(); tick(); syncLike();
  document.querySelectorAll('.trow[data-id]').forEach(r => r.classList.toggle('cur', r.dataset.id === curId));
  if (view.name === 'now') render();
  refreshQueue();
}
function syncToggles() {
  $('#shufBtn').classList.toggle('on', Q.shuffle);
  const r = $('#repBtn'); r.classList.toggle('on', Q.repeat !== 'off'); r.dataset.mode = Q.repeat;
  r.title = 'Repeat: ' + { off: 'off', all: 'all', one: 'one song' }[Q.repeat];
}

/* Windows media overlay (SMTC) + media keys, through the app's Rust side */
function mediaMeta(t) {
  invoke('media_update', { title: t.title, artist: t.artist, album: t.album, cover: t.cover || null, duration: t.duration || 0 }).catch(() => {});
  if ('mediaSession' in navigator && B.os !== 'windows') {
    try { navigator.mediaSession.metadata = new MediaMetadata({ title: t.title, artist: t.artist, album: t.album, artwork: t.cover ? [{ src: fileSrc(B.coversDir + B.sep + t.cover), sizes: '600x600', type: 'image/jpeg' }] : [] }); } catch (_) {}
  }
}
function mediaState() { if (curId) invoke('media_playback', { playing: !A().paused, position: A().currentTime || 0 }).catch(() => {}); }
function mediaKey(action, value) {
  switch (action) {
    case 'toggle': return toggle();
    case 'play': return curId ? A().play().catch(() => {}) : toggle();
    case 'pause': return A().pause();
    case 'next': return next();
    case 'prev': return prev();
    case 'seek': A().currentTime = value; return;
    case 'seekby': A().currentTime = Math.max(0, A().currentTime + value); return;
  }
}
if ('mediaSession' in navigator) {
  try { for (const [k, f] of [['play', () => mediaKey('play')], ['pause', () => mediaKey('pause')], ['nexttrack', next], ['previoustrack', prev]]) navigator.mediaSession.setActionHandler(k, f); } catch (_) {}
}

/* ------------------------------------------------------------------ queue panel */
function refreshQueue() {
  if (app.dataset.queue !== 'open') return;
  const body = $('#qBody');
  const t = byId.get(Q.current());
  const up = Q.upcoming().slice(0, 300);
  const row = (x, i, cur) => `<div class="trow${cur ? ' cur' : ''}" data-q="${i}"><span class="tt">${cover(x.cover, 'r6')}<span class="tl"><span class="t1">${esc(x.title)}</span><span class="t2">${esc(x.artist)}</span></span></span>${cur ? '' : `<button class="more" data-rm="${i}" aria-label="Remove">${ICON.x}</button>`}</div>`;
  body.innerHTML = `<div class="sec">Now playing</div>${t ? row(t, Q.pos, true) : '<div class="qp-empty">Nothing yet</div>'}
    <div class="sec">Next up</div>${up.length ? up.map(u => { const x = byId.get(u.id); return x ? row(x, u.index, false) : ''; }).join('') : `<div class="qp-empty">${Q.repeat === 'all' && Q.length ? 'Starts over from the top' : 'Nothing queued'}</div>`}`;
  body.querySelectorAll('[data-rm]').forEach(b => b.onclick = e => { e.stopPropagation(); Q.remove(+b.dataset.rm); preloadNext(); refreshQueue(); persistSoon(); });
  body.querySelectorAll('.trow[data-q]').forEach(r => r.ondblclick = () => { Q.jump(+r.dataset.q); load(Q.current()); });
}
function toggleQueue(open) {
  app.dataset.queue = open == null ? (app.dataset.queue === 'open' ? '' : 'open') : open ? 'open' : '';
  $('#queueBtn').classList.toggle('on', app.dataset.queue === 'open');
  refreshQueue();
}

/* ------------------------------------------------------------------ persistence */
let persistTimer;
function persistSoon() { clearTimeout(persistTimer); persistTimer = setTimeout(persistNow, 600); }
function persistNow() {
  if (demo) return;
  lastPersist = Date.now();
  const a = A();
  const st = { cur: curId, position: a.currentTime || 0, volume, muted, queue: Q.toJSON(), view: ['songs', 'albums', 'artists', 'smart'].includes(view.name) ? view.name : 'songs', sortBy };
  // songs from dropped files are not in the library; keep only library songs in the saved queue
  const lib = new Set(tracks.map(t => t.id));
  if (st.queue.items.some(id => !lib.has(id))) { st.queue = { items: [], base: [], pos: -1, shuffle: Q.shuffle, repeat: Q.repeat }; if (!lib.has(curId)) st.cur = null; }
  save('state', st);
}
window.addEventListener('beforeunload', () => { persistNow(); saveStatsNow(); });

/* ------------------------------------------------------------------ scanning */
async function rescan(loud) {
  if (scanning) return;
  scanning = true; $('#scanBtn').classList.add('spin');
  if (loud || !tracks.length) render();
  try {
    const list = await invoke('scan');
    const before = tracks.length;
    indexLibrary(list);
    renderSmNav();
    Q.prune(id => byId.has(id));
    settings.lastScan = Date.now(); saveSettings();
    scanning = false;
    const keep = view.name === 'songs' ? (document.querySelector('.vlist') || {}).scrollTop : 0;
    if (keep) view.scrollTo = keep;
    render();
    if (loud) toast(tracks.length === before ? `Library is up to date · ${plural(tracks.length, 'song')}` : `${plural(tracks.length, 'song')} in your library`);
  } catch (e) {
    if (String(e) !== 'busy') toast('Couldn\u2019t read the music folder');
  } finally { scanning = false; $('#scanBtn').classList.remove('spin'); }
}
async function changeFolder(replace) {
  const p = await invoke('pick_folder');
  if (!p) return;
  const f = settings.folders || [];
  if (replace) settings.folders = [p];
  else if (!f.includes(p)) settings.folders = [...f, p];
  else return toast('That folder is already in your library');
  await saveSettings();
  rescan(true);
}

/* ------------------------------------------------------------------ setup (first launch) */
let setupPath = null;
function showSetup() {
  app.dataset.mode = 'setup'; $('#setup').hidden = false;
  $('#pickBtn').onclick = async () => {
    const p = await invoke('pick_folder');
    if (!p) return;
    setupPath = p;
    $('#pickBtn').dataset.has = '1';
    $('#pickTitle').textContent = p.split(/[\\/]/).filter(Boolean).pop() || p;
    $('#pickPath').textContent = p;
    $('#startBtn').disabled = false; $('#setupErr').textContent = '';
  };
  $('#startBtn').onclick = startSetup;
}
async function startSetup() {
  if (!setupPath) return;
  $('#startBtn').disabled = true; $('#pickBtn').disabled = true; $('#setupScan').hidden = false; $('#setupErr').textContent = '';
  settings.folders = [setupPath];
  await saveSettings();
  let list = [];
  scanning = true;
  try { list = await invoke('scan'); } catch (e) { list = []; }
  scanning = false;
  if (!list.length) {
    $('#setupScan').hidden = true; $('#startBtn').disabled = false; $('#pickBtn').disabled = false;
    $('#setupErr').textContent = 'No songs found in that folder. Choose the folder that has your music.';
    return;
  }
  settings.setupDone = true; settings.lastScan = Date.now();
  await saveSettings();
  indexLibrary(list);
  $('#setup').hidden = true;
  enterLibrary();
  toast(`${plural(list.length, 'song')} ready`);
}
function enterLibrary() {
  app.dataset.mode = 'lib';
  renderPlNav(); renderSmNav();
  render();
}

/* ------------------------------------------------------------------ search */
function setQuery(q, silent) {
  query = q; $('#search').value = q; $('#searchBox').classList.toggle('has', !!q);
  if (!silent) { if (!['songs', 'albums', 'artists'].includes(view.name)) { history.push({ ...view }); view = { name: 'songs', arg: null }; } render(); }
}
let searchTimer;
$('#search').addEventListener('input', e => { clearTimeout(searchTimer); searchTimer = setTimeout(() => setQuery(e.target.value.trim()), 120); });
$('#searchX').onclick = () => { setQuery(''); $('#search').focus(); };

/* ------------------------------------------------------------------ controls wiring */
$('#playBtn').onclick = toggle;
$('#nextBtn').onclick = next;
$('#prevBtn').onclick = prev;
$('#shufBtn').onclick = () => { Q.setShuffle(!Q.shuffle); syncToggles(); dropPreload(); preloadNext(); refreshQueue(); persistSoon(); toast(Q.shuffle ? 'Shuffle on' : 'Shuffle off'); };
$('#repBtn').onclick = () => { const m = Q.cycleRepeat(); syncToggles(); preloadNext(); refreshQueue(); persistSoon(); toast({ off: 'Repeat off', all: 'Repeat all', one: 'Repeat one song' }[m]); };
const seek = $('#seek');
seek.addEventListener('input', () => { seeking = true; const d = A().duration || 0; setRange(seek, +seek.value, 1000); $('#curTime').textContent = fmt(d * seek.value / 1000); });
seek.addEventListener('change', () => { const d = A().duration || 0; if (d) A().currentTime = d * seek.value / 1000; seeking = false; persistSoon(); });
$('#vol').addEventListener('input', e => { volume = e.target.value / 100; muted = false; applyVolume(); persistSoon(); });
$('#volBtn').onclick = () => { muted = !muted; if (!muted && volume === 0) volume = 0.5; applyVolume(); persistSoon(); };
$('#npCover').onclick = () => curId && go('now');
$('#npTitle').onclick = () => curId && go('now');
$('#queueBtn').onclick = () => toggleQueue();
$('#qClose').onclick = () => toggleQueue(false);
$('#qClear').onclick = () => { Q.clearUpcoming(); dropPreload(); refreshQueue(); persistSoon(); };
$('#sortBtn').onclick = e => { e.stopPropagation(); sortMenu(e); };
$('#scanBtn').onclick = () => rescan(true);
$('#backBtn').onclick = back;
$('#newPl').onclick = () => newPlaylist();
$('#newSm').onclick = () => openBuilder(null);
$('#likeBtn').onclick = () => { if (curId) toggleLike(curId); };
document.querySelectorAll('.nav-item[data-view]').forEach(n => n.onclick = () => go(n.dataset.view));
$('#sbToggle').onclick = () => { app.dataset.sidebar = 'open'; };
$('#sbClose').onclick = () => { app.dataset.sidebar = 'closed'; };
$('#scrim').onclick = () => { app.dataset.sidebar = 'closed'; };
document.addEventListener('click', e => { if (!e.target.closest('#menu')) closeMenu(); });
window.addEventListener('blur', closeMenu);
window.addEventListener('resize', closeMenu);
document.addEventListener('contextmenu', e => { if (!e.target.closest('input')) e.preventDefault(); });

document.addEventListener('keydown', e => {
  const inField = e.target.closest && e.target.closest('input,textarea');
  const ctrl = e.ctrlKey || e.metaKey;
  if (app.dataset.mode !== 'lib') return;
  if (ctrl && (e.key === 'f' || e.key === 'F')) { e.preventDefault(); $('#search').focus(); $('#search').select(); return; }
  if (ctrl && (e.key === 'b' || e.key === 'B')) { e.preventDefault(); app.dataset.sidebar = app.dataset.sidebar === 'open' ? 'closed' : 'open'; return; }
  if (e.key === 'Escape') {
    if (!$('#menu').hidden) return closeMenu();
    if (!$('#modal').hidden) return;
    if (inField) { setQuery(''); e.target.blur(); return; }
    if (app.dataset.queue === 'open') return toggleQueue(false);
    if (['album', 'artist', 'now', 'auto', 'smartpl', 'mix'].includes(view.name)) return back();
    return;
  }
  if (!$('#modal').hidden) return;
  if (ctrl && e.key === 'ArrowRight') { e.preventDefault(); return next(); }
  if (ctrl && e.key === 'ArrowLeft') { e.preventDefault(); return prev(); }
  if (inField) return;
  if (e.key === ' ' || e.code === 'Space') { e.preventDefault(); return toggle(); }
  if (e.key === 'ArrowRight' && curId) { A().currentTime = Math.min((A().duration || 0), A().currentTime + 5); return; }
  if (e.key === 'ArrowLeft' && curId) { A().currentTime = Math.max(0, A().currentTime - 5); return; }
  if (e.altKey && e.key === 'ArrowLeft') return back();
});
window.addEventListener('mouseup', e => { if (e.button === 3) back(); });

/* ------------------------------------------------------------------ drag & drop, Open with */
async function openPaths(paths) {
  if (!paths || !paths.length) return;
  const list = await invoke('open_paths', { paths });
  if (!list.length) return toast('No playable songs in what you dropped');
  for (const t of list) if (!byId.has(t.id)) { t._dropped = true; byId.set(t.id, t); }
  playList(list.map(t => t.id), 0, { shuffle: false });
  toast(list.length > 1 ? `Playing ${plural(list.length, 'song')}` : `Playing “${list[0].title}”`);
}
try {
  T.webview.getCurrentWebview().onDragDropEvent(ev => {
    const p = ev.payload;
    if (app.dataset.mode !== 'lib') return;
    if (p.type === 'enter' || p.type === 'over') app.dataset.drop = '1';
    else if (p.type === 'leave') app.dataset.drop = '';
    else if (p.type === 'drop') { app.dataset.drop = ''; openPaths(p.paths); }
  });
} catch (e) { console.warn('drag-drop', e); }

/* sidebar: docked on wide windows, tucked away on narrow ones (like College WiFi) */
let wasWide = null;
function fitSidebar() {
  const wide = window.innerWidth >= 860;
  if (wide !== wasWide) app.dataset.sidebar = (demo && demo.sidebar) || (wide ? 'open' : 'closed');
  wasWide = wide;
}
window.addEventListener('resize', () => { if (wasWide !== null) fitSidebar(); });

/* ------------------------------------------------------------------ boot */
async function start() {
  B = await invoke('boot');
  demo = B.demo || null;
  settings = Object.assign({ folders: [], theme: 'system', gapless: true }, B.settings || {});
  playlists = Array.isArray(B.playlists) ? B.playlists : [];
  stats = Sm.Stats.load(B.stats);
  smartLists = Sm.loadSmart(B.smart);
  applyTheme();
  indexLibrary(B.library || []);
  const st = B.state || {};
  if (typeof st.volume === 'number') volume = st.volume;
  muted = !!st.muted;
  if (st.sortBy) sortBy = Object.assign(sortBy, st.sortBy);
  applyVolume();

  listen('scan-progress', e => {
    const { done, total } = e.payload;
    const pct = total ? Math.round(done / total * 100) : 0;
    const txt = total ? `${done.toLocaleString()} of ${plural(total, 'song')}` : 'Looking for songs…';
    const f1 = $('#setupBar'); if (f1) f1.style.width = pct + '%';
    const t1 = $('#setupScanText'); if (t1) t1.textContent = total ? `Reading ${txt}` : 'Looking for songs…';
    const f2 = $('#scanFill'); if (f2) f2.style.width = pct + '%';
    const t2 = $('#scanTxt'); if (t2) t2.textContent = total ? `Updating library · ${txt}` : 'Looking for songs…';
  });
  listen('library-dirty', () => { if (app.dataset.mode === 'lib') rescan(false); });
  listen('media-key', e => mediaKey(e.payload.action, e.payload.value));
  listen('open-paths', e => openPaths(e.payload));

  const needSetup = !settings.setupDone || !(settings.folders || []).length;
  if (demo && demo.setup) { showSetup(); if (demo.pick) { setupPath = demo.pick; $('#pickBtn').dataset.has = '1'; $('#pickTitle').textContent = demo.pick.split(/[\\/]/).pop(); $('#pickPath').textContent = demo.pick; $('#startBtn').disabled = false; } }
  else if (needSetup) showSetup();
  else {
    if (st.view && !demo) view = { name: st.view, arg: null };
    Q = PMQueue.fromJSON(st.queue, id => byId.has(id));
    syncToggles();
    enterLibrary();
    if (st.cur && byId.has(st.cur) && Q.current() === st.cur) load(st.cur, { autoplay: false, at: st.position || 0 });
    else updateNow();
    if (!demo) rescan(false);  // pick up changes made while the app was closed
  }
  const pending = await invoke('take_pending_open');
  if (pending.length && app.dataset.mode === 'lib') openPaths(pending);
  setTimeout(() => invoke('ready').then(() => setTimeout(fitSidebar, 120)), 30);
  if (demo) await runDemo(demo);
}

/* Screenshot mode (PARADOX_MUSIC_DEMO): drives the real UI into a given state. Never set by users. */
async function runDemo(d) {
  if (app.dataset.mode !== 'lib') return;
  if (d.sidebar) app.dataset.sidebar = d.sidebar;
  if (d.sort) Object.assign(sortBy, d.sort);
  if (d.play != null) {
    const ids = albums.sort(SORTS.albums.name[1]).flatMap(a => a.tracks.map(t => t.id));
    if (d.playTitle) d.play = Math.max(0, ids.findIndex(id => byId.get(id).title === d.playTitle));
    Q.set(ids, d.play, { shuffle: !!d.shuffle }); if (d.repeat) Q.setRepeat(d.repeat); syncToggles();
    load(Q.current(), { autoplay: false });
    const a = A();
    await new Promise(r => { if (a.readyState >= 1) r(); else { a.addEventListener('loadedmetadata', r, { once: true }); setTimeout(r, 2500); } });
    if (d.at) { try { a.currentTime = d.at; } catch (_) {} }
    if (d.playing) { player.dataset.state = 'playing'; app.dataset.playing = '1'; }
    const t = byId.get(curId), dur = (a.duration || t.duration || 0);
    setRange($('#seek'), dur ? (d.at || 0) / dur * 1000 : 0, 1000); $('#curTime').textContent = fmt(d.at || 0); $('#durTime').textContent = fmt(dur);
  }
  if (d.view === 'album') { const a = albums.sort(SORTS.albums.name[1])[d.album || 0]; view = { name: 'album', arg: a.key }; }
  else if (d.view === 'artist') { view = { name: 'artist', arg: artists.sort(SORTS.artists.name[1])[d.artist || 0].name }; }
  else if (d.view === 'playlist') { view = { name: 'playlist', arg: (playlists[0] || {}).id }; }
  else if (d.view === 'auto') { view = { name: 'auto', arg: d.key }; }
  else if (d.view === 'smartpl') { view = { name: 'smartpl', arg: (smartLists[d.idx || 0] || {}).id }; }
  else if (d.view === 'mix') {
    const seed = d.mixArtist ? null : tracks.find(t => t.title === d.mixSong) || tracks[0];
    const m = d.mixArtist ? Sm.mixFromArtist(d.mixArtist, tracks, stats, 7) : Sm.mixFromSong(seed, tracks, stats, 7);
    mixes.set('m0', Object.assign(m, { kind: d.mixArtist ? 'artist' : 'song', ref: d.mixArtist || seed.id, rnd: 7 })); view = { name: 'mix', arg: 'm0' };
  }
  else if (d.view) view = { name: d.view, arg: null };
  if (d.query) setQuery(d.query, true);
  render();
  if (d.queue) toggleQueue(true);
  renderSmNav();
  if (d.builder) openBuilder(d.builder === 'edit' ? smartLists[d.idx || 0] : null, d.builder === 'edit' ? null : d.preset);
  if (d.menu) {
    await new Promise(r => setTimeout(r, 1800));  // after the window is shown (showing it fires resize, which closes menus)
    const row = document.querySelectorAll('.trow')[d.menu.row || 0];
    if (row) { row.classList.add('sel'); const r = row.getBoundingClientRect(); trackMenu({ clientX: r.left + r.width * 0.55, clientY: r.top + 30 }, songList(), +row.dataset.i); }
    if (d.menu.sub) { const b = [...document.querySelectorAll('#menu .mi')].find(x => x.textContent.includes('Add to playlist')); b && b.click(); }
  }
}

start().catch(e => { console.error(e); invoke('ready'); });
