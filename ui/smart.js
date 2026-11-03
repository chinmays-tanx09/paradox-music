/* Paradox Music: smart playlists. The rule engine, built-in auto lists, play stats, "Make a mix"
   and the play counter. Pure logic, no DOM, unit-tested with node --test. Mirrors the Android app
   (data/Smart.kt, Stats.kt, Mixer.kt, play/PlayCounter.kt): same fields, rules, sorts and JSON, so both
   apps behave the same. Everything is local; nothing here touches the network. */
(function (root) {
  'use strict';
  const DAY = 86400000;

  /* ---------------------------------------------------------------- text helpers */
  const fold = s => String(s == null ? '' : s).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  const alnum = s => fold(s).replace(/[^\p{L}\p{N}]+/gu, '');
  const coll = new Intl.Collator(undefined, { sensitivity: 'base', numeric: true });

  // ID3v1 genre numbers, as some old MP3s store them: "(17)", "17" or "(17)Rock".
  const ID3 = ['Blues', 'Classic Rock', 'Country', 'Dance', 'Disco', 'Funk', 'Grunge', 'Hip-Hop', 'Jazz', 'Metal', 'New Age', 'Oldies', 'Other', 'Pop', 'R&B', 'Rap', 'Reggae', 'Rock', 'Techno', 'Industrial',
    'Alternative', 'Ska', 'Death Metal', 'Pranks', 'Soundtrack', 'Euro-Techno', 'Ambient', 'Trip-Hop', 'Vocal', 'Jazz+Funk', 'Fusion', 'Trance', 'Classical', 'Instrumental', 'Acid', 'House', 'Game', 'Sound Clip', 'Gospel', 'Noise',
    'Alternative Rock', 'Bass', 'Soul', 'Punk', 'Space', 'Meditative', 'Instrumental Pop', 'Instrumental Rock', 'Ethnic', 'Gothic', 'Darkwave', 'Techno-Industrial', 'Electronic', 'Pop-Folk', 'Eurodance', 'Dream', 'Southern Rock', 'Comedy', 'Cult', 'Gangsta',
    'Top 40', 'Christian Rap', 'Pop/Funk', 'Jungle', 'Native American', 'Cabaret', 'New Wave', 'Psychedelic', 'Rave', 'Showtunes', 'Trailer', 'Lo-Fi', 'Tribal', 'Acid Punk', 'Acid Jazz', 'Polka', 'Retro', 'Musical', 'Rock & Roll', 'Hard Rock'];
  // "/" splits too, except inside genre names that contain one
  const KEEP_SLASH = ['pop/funk', 'ac/dc'];

  /** Splits a genre tag like "Pop; Dance", "Rock/Pop" or "(17)" into its parts. */
  function splitGenres(g) {
    if (!g) return [];
    let s = String(g).replace(/\((\d{1,3})\)/g, (_, n) => (ID3[+n] ? ID3[+n] + ';' : ';'));
    const out = [];
    for (let part of s.split(/[;\u0000,|]/)) {
      part = part.trim();
      if (!part) continue;
      const pieces = KEEP_SLASH.includes(part.toLowerCase()) ? [part] : part.split('/');
      for (let p of pieces) {
        p = p.trim();
        if (/^\d{1,3}$/.test(p)) p = ID3[+p] || '';
        if (p && !out.some(o => alnum(o) === alnum(p))) out.push(p);
      }
    }
    return out;
  }
  /** Genre identity: "Hip-Hop", "hip hop" and "HipHop" are one genre. */
  const genreKey = g => alnum(g);
  const genresOf = t => splitGenres(t.genre);
  const artistParts = a => String(a || '').split(/,|&| feat\. | ft\. | x | \/ /i).map(s => s.trim()).filter(Boolean);

  /* ---------------------------------------------------------------- rules */
  const FIELDS = {
    genre: { label: 'Genre', kind: 'text' }, artist: { label: 'Artist', kind: 'text' }, album: { label: 'Album', kind: 'text' },
    year: { label: 'Year', kind: 'number' }, added: { label: 'Added', kind: 'date' }, plays: { label: 'Play count', kind: 'number' },
    lastPlayed: { label: 'Last played', kind: 'date' }, liked: { label: 'Liked', kind: 'bool' }, duration: { label: 'Length', kind: 'number' },
  };
  const FIELD_ORDER = ['genre', 'artist', 'album', 'year', 'added', 'plays', 'lastPlayed', 'liked', 'duration'];
  function opsFor(f) {
    switch ((FIELDS[f] || {}).kind) {
      case 'text': return ['is', 'isNot', 'contains', 'notContains'];
      case 'number': return f === 'year' ? ['between', 'atLeast', 'atMost'] : ['atLeast', 'atMost', 'between'];
      case 'date': return f === 'lastPlayed' ? ['inLast', 'notInLast', 'never'] : ['inLast', 'notInLast'];
      case 'bool': return ['yes', 'no'];
    }
    return [];
  }
  function opLabel(o, f) {
    switch (o) {
      case 'is': return 'is'; case 'isNot': return 'is not'; case 'contains': return 'contains'; case 'notContains': return 'doesn\u2019t contain';
      case 'atLeast': return f === 'year' ? 'from' : f === 'duration' ? 'longer than' : 'at least';
      case 'atMost': return f === 'year' ? 'up to' : f === 'duration' ? 'shorter than' : 'at most';
      case 'between': return 'between'; case 'inLast': return 'in the last'; case 'notInLast': return 'not in the last'; case 'never': return 'never';
      case 'yes': return 'yes'; case 'no': return 'no';
    }
    return o;
  }
  /** A fresh rule for a field, with sensible numbers filled in (same defaults as Android). */
  function newCond(f) {
    switch (f) {
      case 'year': return { f, o: 'between', t: '', a: 2010, b: 2019 };
      case 'plays': return { f, o: 'atLeast', t: '', a: 5, b: 0 };
      case 'duration': return { f, o: 'atMost', t: '', a: 240, b: 360 };
      case 'added': return { f, o: 'inLast', t: '', a: 30, b: 0 };
      case 'lastPlayed': return { f, o: 'inLast', t: '', a: 7, b: 0 };
      default: return { f, o: opsFor(f)[0], t: '', a: 0, b: 0 };
    }
  }
  function cleanCond(c) {
    if (!c || !FIELDS[c.f]) return null;
    const ops = opsFor(c.f);
    return { f: c.f, o: ops.includes(c.o) ? c.o : ops[0], t: String(c.t || ''), a: Number(c.a) || 0, b: Number(c.b) || 0 };
  }
  const mmss = s => { s = Math.max(0, Math.round(s)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };
  const days = n => (n === 1 ? '1 day' : `${n} days`);
  /** Short readable form: "Genre is Pop", "Year 2010–2019", "Played 5+ times". */
  function condSummary(c) {
    const lo = Math.min(c.a, c.b), hi = Math.max(c.a, c.b);
    switch (c.f) {
      case 'liked': return c.o === 'yes' ? 'Liked' : 'Not liked';
      case 'plays':
        if (c.o === 'atLeast') return c.a <= 0 ? 'Any play count' : `Played ${c.a}+ ${c.a === 1 ? 'time' : 'times'}`;
        if (c.o === 'atMost') return c.a <= 0 ? 'Never played' : `Played \u2264 ${c.a} ${c.a === 1 ? 'time' : 'times'}`;
        return `Played ${lo}\u2013${hi} times`;
      case 'year': return c.o === 'atLeast' ? `Year ${c.a}+` : c.o === 'atMost' ? `Year \u2264 ${c.a}` : `Year ${lo}\u2013${hi}`;
      case 'duration': return c.o === 'atLeast' ? `Longer than ${mmss(c.a)}` : c.o === 'atMost' ? `Shorter than ${mmss(c.a)}` : `Length ${mmss(lo)}\u2013${mmss(hi)}`;
      case 'added': case 'lastPlayed': {
        const what = c.f === 'added' ? 'Added' : 'Played';
        return c.o === 'never' ? 'Never played' : c.o === 'notInLast' ? `${what} over ${days(c.a)} ago` : `${what} in the last ${days(c.a)}`;
      }
    }
    return `${FIELDS[c.f].label} ${opLabel(c.o, c.f)} ${String(c.t).trim() || '\u2026'}`;
  }

  const SORTS = {
    title: 'Title', artist: 'Artist', album: 'Album', yearNew: 'Newest first', yearOld: 'Oldest first', added: 'Recently added',
    mostPlayed: 'Most played', leastPlayed: 'Least played', lastPlayed: 'Recently played', liked: 'Recently liked',
    longest: 'Longest', shortest: 'Shortest', random: 'Random',
  };
  function cleanRule(r) {
    r = r || {};
    return { m: r.m === 'any' ? 'any' : 'all', c: (Array.isArray(r.c) ? r.c : []).map(cleanCond).filter(Boolean), s: SORTS[r.s] ? r.s : 'artist', l: Math.max(0, Math.floor(Number(r.l) || 0)) };
  }
  function ruleSummary(r) {
    if (!r.c.length) return 'All songs';
    const parts = r.c.map(condSummary);
    return r.m === 'any' && parts.length > 1 ? 'Any of: ' + parts.join(' \u00b7 ') : parts.join(' \u00b7 ');
  }

  const NONE = Object.freeze({ p: 0, l: 0, f: 0, fa: 0 });
  const st = (stats, id) => (stats && stats[id]) || NONE;
  const addedMs = t => (t.added || 0) * 1000;  // the library stores seconds

  function textMatch(c, values) {
    const q = fold(c.t.trim());
    if (!q) return true;  // an unfinished rule filters nothing out
    const qk = alnum(q);
    const vs = values.map(v => fold(String(v).trim()));
    const same = v => v === q || (qk && alnum(v) === qk);
    switch (c.o) {
      case 'is': return vs.some(same);
      case 'isNot': return !vs.some(same);
      case 'contains': return vs.some(v => v.includes(q));
      default: return !vs.some(v => v.includes(q));
    }
  }
  function numMatch(c, v) {
    if (c.o === 'atLeast') return v >= c.a;
    if (c.o === 'atMost') return v <= c.a;
    return v >= Math.min(c.a, c.b) && v <= Math.max(c.a, c.b);
  }
  function dateMatch(c, at, now) {
    if (c.o === 'never') return !at;
    if (c.o === 'inLast') return !!at && at >= now - c.a * DAY;
    return !at || at < now - c.a * DAY;
  }
  function matches(c, t, s, now) {
    switch (c.f) {
      case 'genre': return textMatch(c, genresOf(t));
      case 'artist': return textMatch(c, [t.artist, t.album_artist, ...artistParts(t.artist)].filter(Boolean));
      case 'album': return textMatch(c, [t.album || '']);
      case 'year': return t.year > 0 && numMatch(c, t.year);
      case 'plays': return numMatch(c, s.p || 0);
      case 'duration': return t.duration > 0 && numMatch(c, Math.round(t.duration));
      case 'added': return dateMatch(c, addedMs(t), now);
      case 'lastPlayed': return dateMatch(c, s.l || 0, now);
      case 'liked': return c.o === 'no' ? !s.f : !!s.f;
    }
    return true;
  }

  /** Small seeded random (mulberry32), so a random order stays put until asked to change. */
  function rng(seed) {
    let a = (seed >>> 0) || 0x9e3779b9;
    return () => { a = (a + 0x6D2B79F5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  }
  function shuffled(list, r) {
    const a = list.slice().sort((x, y) => (x.id < y.id ? -1 : x.id > y.id ? 1 : 0));
    for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; }
    return a;
  }

  function sortTracks(list, sort, stats, seed) {
    const S = t => st(stats, t.id);
    const byTitle = (a, b) => coll.compare(a.title, b.title);
    const byArtist = (a, b) => coll.compare(a.artist, b.artist) || coll.compare(a.album, b.album) || ((a.disc || 1) - (b.disc || 1)) || ((a.track || 0) - (b.track || 0)) || byTitle(a, b);
    const cmp = {
      title: byTitle, artist: byArtist,
      album: (a, b) => coll.compare(a.album, b.album) || ((a.disc || 1) - (b.disc || 1)) || ((a.track || 0) - (b.track || 0)) || byTitle(a, b),
      yearNew: (a, b) => ((b.year || -1e9) - (a.year || -1e9)) || byArtist(a, b),
      yearOld: (a, b) => ((a.year || 1e9) - (b.year || 1e9)) || byArtist(a, b),
      added: (a, b) => (addedMs(b) - addedMs(a)) || byArtist(a, b),
      mostPlayed: (a, b) => ((S(b).p || 0) - (S(a).p || 0)) || ((S(b).l || 0) - (S(a).l || 0)) || byTitle(a, b),
      leastPlayed: (a, b) => ((S(a).p || 0) - (S(b).p || 0)) || ((S(a).l || 0) - (S(b).l || 0)) || byTitle(a, b),
      lastPlayed: (a, b) => ((S(b).l || 0) - (S(a).l || 0)) || byTitle(a, b),
      liked: (a, b) => ((S(b).fa || 0) - (S(a).fa || 0)) || byTitle(a, b),
      longest: (a, b) => ((b.duration || 0) - (a.duration || 0)) || byTitle(a, b),
      shortest: (a, b) => ((a.duration || 0) - (b.duration || 0)) || byTitle(a, b),
    }[sort];
    // Random is stable for a given seed, so the list doesn't reshuffle each time a play is counted.
    if (!cmp) return shuffled(list, rng(seed || 1));
    return list.slice().sort(cmp);
  }

  /** The songs a rule picks, in its order, cut to its limit. */
  function evaluate(rule, tracks, stats, now, seed) {
    rule = cleanRule(rule);
    const hit = !rule.c.length ? tracks : tracks.filter(t => {
      const s = st(stats, t.id);
      return rule.m === 'all' ? rule.c.every(c => matches(c, t, s, now)) : rule.c.some(c => matches(c, t, s, now));
    });
    const sorted = sortTracks(hit, rule.s, stats, seed);
    return rule.l > 0 ? sorted.slice(0, rule.l) : sorted;
  }
  /** How many songs a rule picks (the live count in the rule builder). */
  const count = (rule, tracks, stats, now) => evaluate(Object.assign({}, rule, { s: 'title' }), tracks, stats, now).length;

  /* ---------------------------------------------------------------- saved smart playlists */
  function uniqueName(all, name, except) {
    const taken = new Set(all.filter(p => p.id !== except).map(p => p.name.toLowerCase()));
    if (!taken.has(name.toLowerCase())) return name;
    let n = 2; while (taken.has(`${name} ${n}`.toLowerCase())) n++;
    return `${name} ${n}`;
  }
  function create(all, name, rule, now) {
    const p = { id: 's' + (Math.max(0, ...all.map(p => parseInt(String(p.id).replace(/^s/, ''), 10) || 0)) + 1), name: uniqueName(all, String(name || '').trim() || 'Smart playlist'), rule: cleanRule(rule), created: now };
    return { list: [...all, p], item: p };
  }
  const update = (all, id, name, rule) => all.map(p => (p.id === id ? Object.assign({}, p, { name: uniqueName(all, String(name || '').trim() || p.name, id), rule: cleanRule(rule) }) : p));
  const remove = (all, id) => all.filter(p => p.id !== id);
  const loadSmart = v => (Array.isArray(v) ? v : []).filter(p => p && p.id && p.name).map(p => ({ id: String(p.id), name: String(p.name), rule: cleanRule(p.rule), created: Number(p.created) || 0 }));

  /* ---------------------------------------------------------------- stats: plays, last played, likes */
  const Stats = {
    get: st,
    recordPlay(all, id, now) { const s = st(all, id); return Object.assign({}, all, { [id]: Object.assign({}, s, { p: (s.p || 0) + 1, l: now }) }); },
    setLiked(all, id, liked, now) {
      const s = st(all, id);
      if (!!s.f === !!liked) return all;
      const n = Object.assign({}, s, liked ? { f: 1, fa: now } : { f: 0, fa: 0 });
      const out = Object.assign({}, all);
      if (!n.p && !n.l && !n.f) delete out[id]; else out[id] = n;
      return out;
    },
    /** Same JSON as the Android app: { id: { p, l, f, fa } }, empty fields left out. */
    toJSON(all) {
      const o = {};
      for (const [id, s] of Object.entries(all || {})) {
        const e = {};
        if (s.p > 0) e.p = s.p; if (s.l > 0) e.l = s.l; if (s.f) { e.f = 1; e.fa = s.fa || 0; }
        if (Object.keys(e).length) o[id] = e;
      }
      return o;
    },
    load(v) {
      const o = {};
      if (v && typeof v === 'object' && !Array.isArray(v)) for (const [id, e] of Object.entries(v)) if (e && typeof e === 'object') o[id] = { p: +e.p || 0, l: +e.l || 0, f: e.f ? 1 : 0, fa: +e.fa || 0 };
      return o;
    },
  };

  /* ---------------------------------------------------------------- built-in lists that keep themselves up to date */
  const R = (c, s, l) => ({ m: 'all', c, s, l: l || 0 });
  const FIXED = [
    { key: 'auto:favorites', name: 'Favorites', kind: 'favorites', rule: R([{ f: 'liked', o: 'yes', t: '', a: 0, b: 0 }], 'liked') },
    { key: 'auto:most', name: 'Most played', kind: 'most', rule: R([{ f: 'plays', o: 'atLeast', t: '', a: 1, b: 0 }], 'mostPlayed', 50) },
    { key: 'auto:recent', name: 'Recently played', kind: 'recent', rule: R([{ f: 'plays', o: 'atLeast', t: '', a: 1, b: 0 }], 'lastPlayed', 50) },
    { key: 'auto:added', name: 'Recently added', kind: 'added', rule: R([], 'added', 50) },
    { key: 'auto:never', name: 'Never played', kind: 'never', rule: R([{ f: 'plays', o: 'atMost', t: '', a: 0, b: 0 }], 'random') },
  ];
  /** One list per genre in the tags, biggest first. Spelling variants are merged; the commonest spelling names it. */
  function genreLists(tracks) {
    const by = new Map();
    for (const t of tracks) for (const g of genresOf(t)) { const k = genreKey(g); if (!k) continue; let e = by.get(k); if (!e) by.set(k, e = { k, n: 0, names: new Map() }); e.n++; e.names.set(g, (e.names.get(g) || 0) + 1); }
    return [...by.values()].sort((a, b) => b.n - a.n || (a.k < b.k ? -1 : 1)).map(e => {
      // ties go to a capitalised spelling ("Hip-Hop" over "hip hop"), then to the first one seen
      const caps = s => (/\p{Lu}/u.test(s) ? 1 : 0);
      const name = [...e.names.entries()].map((x, i) => [x[0], x[1], i]).sort((a, b) => b[1] - a[1] || caps(b[0]) - caps(a[0]) || a[2] - b[2])[0][0];
      return { key: 'genre:' + e.k, name, kind: 'genre', count: e.n, rule: R([{ f: 'genre', o: 'is', t: name, a: 0, b: 0 }], 'artist') };
    });
  }
  const decadeName = d => (d >= 1900 && d <= 1999 ? `${String(d % 100).padStart(2, '0')}s` : `${d}s`);
  /** One list per decade in the tags, newest first. */
  function decadeLists(tracks) {
    const by = new Map();
    for (const t of tracks) if (t.year >= 1000 && t.year <= 2999) { const d = Math.floor(t.year / 10) * 10; by.set(d, (by.get(d) || 0) + 1); }
    return [...by.keys()].sort((a, b) => b - a).map(d => ({ key: 'decade:' + d, name: decadeName(d), kind: 'decade', count: by.get(d), rule: R([{ f: 'year', o: 'between', t: '', a: d, b: d + 9 }], 'yearOld') }));
  }
  function autoByKey(key, tracks) {
    return FIXED.find(a => a.key === key) || (key.startsWith('genre:') ? genreLists(tracks).find(a => a.key === key) : key.startsWith('decade:') ? decadeLists(tracks).find(a => a.key === key) : null) || null;
  }

  /* ---------------------------------------------------------------- Make a mix */
  const LIMIT = 40, ERA_YEARS = 5, SAME_ARTIST_MAX = 12;
  const artistKey = a => fold(String(a || '').trim());
  function sameArtist(t, artist) {
    const k = artistKey(artist);
    return artistKey(t.artist) === k || (t.album_artist && artistKey(t.album_artist) === k) || artistParts(t.artist).some(p => artistKey(p) === k);
  }
  const gkeys = t => new Set(genresOf(t).map(genreKey).filter(Boolean));
  // Shuffle, then let liked songs float up a little (a stable sort keeps the shuffle inside each group).
  const weighted = (list, r, stats) => shuffled(list, r).map((t, i) => [t, i]).sort((x, y) => ((st(stats, y[0].id).f ? 1 : 0) - (st(stats, x[0].id).f ? 1 : 0)) || (x[1] - y[1])).map(x => x[0]);
  function tiers(head, pool, genres, year, r, stats, limit) {
    const out = [];
    const seen = new Set();
    for (const t of head) if (out.length < limit && !seen.has(t.id)) { seen.add(t.id); out.push(t.id); }
    const era = t => year > 0 && t.year > 0 && Math.abs(t.year - year) <= ERA_YEARS;
    const genre = t => genres.size > 0 && [...gkeys(t)].some(g => genres.has(g));
    const left = pool.filter(t => !seen.has(t.id));
    for (const tier of [left.filter(t => genre(t) && era(t)), left.filter(t => genre(t) && !era(t)), left.filter(t => !genre(t) && era(t))]) {
      for (const t of weighted(tier, r, stats)) { if (out.length >= limit) break; out.push(t.id); }
    }
    return out;
  }
  /** A song's mix: the song, its artist, then the same genre and era, then the same genre, then the same era. */
  function mixFromSong(seed, all, stats, rnd, limit) {
    limit = limit || LIMIT; const r = rng(rnd || 1);
    const rest = all.filter(t => t.id !== seed.id);
    const artist = weighted(rest.filter(t => sameArtist(t, seed.artist)), r, stats).slice(0, SAME_ARTIST_MAX);
    return { name: `${seed.title} mix`, ids: tiers([seed, ...artist], rest, gkeys(seed), seed.year || 0, r, stats, limit), seed: seed.title, seedId: seed.id };
  }
  /** An artist's mix: their songs first, then their genres (two commonest) and era (median year). */
  function mixFromArtist(artist, all, stats, rnd, limit) {
    limit = limit || LIMIT; const r = rng(rnd || 1);
    const mine = all.filter(t => sameArtist(t, artist));
    const gc = new Map();
    for (const t of mine) for (const g of gkeys(t)) gc.set(g, (gc.get(g) || 0) + 1);
    const genres = new Set([...gc.entries()].sort((a, b) => b[1] - a[1]).slice(0, 2).map(e => e[0]));
    const years = mine.map(t => t.year).filter(y => y > 0).sort((a, b) => a - b);
    const era = years.length ? years[Math.floor(years.length / 2)] : 0;
    const head = weighted(mine, r, stats).slice(0, Math.max(SAME_ARTIST_MAX, Math.floor(limit / 2)));
    return { name: `${artist} mix`, ids: tiers(head, all, genres, era, r, stats, limit), seed: artist, seedId: null };
  }

  /* ---------------------------------------------------------------- play counting */
  /** A song counts as played once it has actually been heard (playing time, not the seek position) for
      half its length or 30 seconds, whichever comes first. Each start counts at most once; a repeat-one
      loop is a new start. The caller passes the clock, so it is unit-tested. */
  class PlayCounter {
    constructor(capMs, fraction) { this.capMs = capMs || 30000; this.fraction = fraction || 0.5; this.id = null; this.durMs = 0; this.heard = 0; this.since = null; this.counted = false; }
    threshold() { return this.durMs > 0 ? Math.max(1, Math.min(this.capMs, this.durMs * this.fraction)) : this.capMs; }
    heardMs(now) { return this.heard + (this.since != null ? Math.max(0, now - this.since) : 0); }
    /** A new song (or the same one again) started. Returns the previous song's id if it just crossed the line. */
    begin(id, durMs, playing, now) {
      const done = this.check(now);
      this.id = id || null; this.durMs = Math.max(0, durMs || 0); this.heard = 0; this.counted = false;
      this.since = playing && id ? now : null;
      return done;
    }
    setDuration(ms) { if (ms > 0) this.durMs = ms; }
    /** Playback started or paused. Returns the id to count, if the song just crossed the line. */
    setPlaying(playing, now) {
      const done = this.check(now);
      if (playing) { if (this.since == null && this.id) this.since = now; }
      else { this.heard = this.heardMs(now); this.since = null; }
      return done;
    }
    /** Call now and then while playing. Returns the id to count, exactly once per start. */
    check(now) {
      if (!this.id || this.counted || this.heardMs(now) < this.threshold()) return null;
      this.counted = true;
      return this.id;
    }
    msUntilCount(now) { return !this.id || this.counted ? null : Math.max(0, this.threshold() - this.heardMs(now)); }
  }

  const Smart = {
    DAY, fold, splitGenres, genreKey, genresOf, FIELDS, FIELD_ORDER, opsFor, opLabel, newCond, cleanCond, condSummary, SORTS, cleanRule, ruleSummary,
    matches, evaluate, count, sortTracks, create, update, remove, loadSmart, Stats, FIXED, genreLists, decadeLists, decadeName, autoByKey,
    mixFromSong, mixFromArtist, MIX_LIMIT: LIMIT, PlayCounter, rng,
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = Smart;
  else root.PMSmart = Smart;
})(this);
