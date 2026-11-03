// node --test ui/test/
const test = require('node:test');
const assert = require('node:assert/strict');
const S = require('../smart.js');

const NOW = Date.UTC(2026, 10, 3, 12, 0, 0);
const DAY = S.DAY;
const T = (id, o) => Object.assign({ id, title: id, artist: 'X', album_artist: '', album: 'A', track: 0, disc: 0, year: 0, genre: '', duration: 200, added: Math.floor((NOW - 400 * DAY) / 1000) }, o);
const lib = [
  T('a', { title: 'Alpha', artist: 'Aria Sen', album: 'Northbound', year: 2021, genre: 'Pop; Dance', duration: 180, added: Math.floor((NOW - 2 * DAY) / 1000) }),
  T('b', { title: 'Bravo', artist: 'Aria Sen', album: 'Northbound', year: 2019, genre: 'Hip-Hop', duration: 240 }),
  T('c', { title: 'Charlie', artist: 'Kavi', album: 'Lossless', year: 1998, genre: 'hip hop', duration: 400 }),
  T('d', { title: 'Delta', artist: 'Kavi feat. Aria Sen', album: 'Lossless', year: 1994, genre: 'Rock/Pop', duration: 60 }),
  T('e', { title: 'Echo', artist: 'Nila Rao', album: 'Window', year: 0, genre: '', duration: 0 }),
  T('f', { title: 'Foxtrot', artist: 'Ríos', album: 'Sol', year: 2023, genre: '(17)', duration: 300, added: Math.floor((NOW - 10 * DAY) / 1000) }),
];
const stats = {
  a: { p: 12, l: NOW - 1 * DAY, f: 1, fa: NOW - 5 * DAY },
  b: { p: 3, l: NOW - 40 * DAY, f: 0, fa: 0 },
  c: { p: 7, l: NOW - 2 * 3600e3, f: 1, fa: NOW - 1 * DAY },
};
const ids = l => l.map(t => t.id);
const rule = (c, o) => Object.assign({ m: 'all', c, s: 'title', l: 0 }, o);

test('genres: split "Pop; Dance" and "Rock/Pop", merge Hip-Hop / hip hop, read ID3v1 numbers', () => {
  assert.deepEqual(S.splitGenres('Pop; Dance'), ['Pop', 'Dance']);
  assert.deepEqual(S.splitGenres('Rock/Pop, Indie | Jazz'), ['Rock', 'Pop', 'Indie', 'Jazz']);
  assert.deepEqual(S.splitGenres('(17)'), ['Rock']);
  assert.deepEqual(S.splitGenres('(7)Hip-Hop'), ['Hip-Hop']);
  assert.deepEqual(S.splitGenres('Pop/Funk'), ['Pop/Funk']);
  assert.deepEqual(S.splitGenres('Pop;pop; POP'), ['Pop']);
  assert.deepEqual(S.splitGenres(''), []);
  assert.equal(S.genreKey('Hip-Hop'), S.genreKey('hip hop'));
  assert.equal(S.genreKey('HipHop'), S.genreKey('hip hop'));
  const g = S.genreLists(lib);
  const hh = g.find(x => x.key === 'genre:hiphop');
  assert.equal(hh.count, 2, 'Hip-Hop and hip hop are one list');
  assert.equal(g[0].key, 'genre:hiphop', 'biggest first');
  assert.deepEqual(ids(S.evaluate(hh.rule, lib, stats, NOW)).sort(), ['b', 'c']);
  const pop = g.find(x => x.key === 'genre:pop');
  assert.deepEqual(ids(S.evaluate(pop.rule, lib, stats, NOW)).sort(), ['a', 'd'], '"Pop; Dance" and "Rock/Pop" are both Pop');
  assert.ok(g.find(x => x.key === 'genre:rock'), '(17) is Rock');
});

test('decades are built from the year tag, newest first', () => {
  const d = S.decadeLists(lib);
  assert.deepEqual(d.map(x => x.name), ['2020s', '2010s', '90s']);
  assert.deepEqual(ids(S.evaluate(d[2].rule, lib, stats, NOW)), ['d', 'c'], 'oldest first');
  assert.equal(S.decadeName(1980), '80s');
  assert.equal(S.decadeName(2000), '2000s');
});

test('text rules: is / is not / contains, case- and accent-blind; artist matches featured artists', () => {
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'artist', o: 'is', t: 'aria sen' }]), lib, stats, NOW)), ['a', 'b', 'd']);
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'artist', o: 'is', t: 'rios' }]), lib, stats, NOW)), ['f']);
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'album', o: 'contains', t: 'north' }]), lib, stats, NOW)), ['a', 'b']);
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'genre', o: 'isNot', t: 'Hip Hop' }]), lib, stats, NOW)), ['a', 'd', 'e', 'f']);
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'album', o: 'notContains', t: 'loss' }]), lib, stats, NOW)), ['a', 'b', 'e', 'f']);
  assert.equal(S.evaluate(rule([{ f: 'genre', o: 'is', t: '  ' }]), lib, stats, NOW).length, 6, 'an unfinished rule filters nothing');
});

test('number rules: year range, play count, duration; untagged values never match', () => {
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'year', o: 'between', a: 2019, b: 2010 }]), lib, stats, NOW)), ['b'], 'range works either way round');
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'year', o: 'atLeast', a: 2020 }]), lib, stats, NOW)), ['a', 'f']);
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'year', o: 'atMost', a: 2000 }]), lib, stats, NOW)), ['c', 'd'], 'no year tag is not "before 2000"');
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'plays', o: 'atLeast', a: 5 }]), lib, stats, NOW)), ['a', 'c']);
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'plays', o: 'atMost', a: 0 }]), lib, stats, NOW)), ['d', 'e', 'f']);
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'duration', o: 'atMost', a: 200 }]), lib, stats, NOW)), ['a', 'd'], 'unknown length is skipped');
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'duration', o: 'atLeast', a: 300 }]), lib, stats, NOW)), ['c', 'f']);
});

test('date rules: added and last played, in / not in the last N days, never', () => {
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'added', o: 'inLast', a: 30 }]), lib, stats, NOW)), ['a', 'f']);
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'added', o: 'notInLast', a: 5 }]), lib, stats, NOW)), ['b', 'c', 'd', 'e', 'f']);
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'lastPlayed', o: 'inLast', a: 7 }]), lib, stats, NOW)), ['a', 'c']);
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'lastPlayed', o: 'notInLast', a: 30 }]), lib, stats, NOW)), ['b', 'd', 'e', 'f'], 'never played counts as not recently');
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'lastPlayed', o: 'never' }]), lib, stats, NOW)), ['d', 'e', 'f']);
});

test('liked, match all vs any, sort and limit', () => {
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'liked', o: 'yes' }]), lib, stats, NOW)), ['a', 'c']);
  assert.deepEqual(ids(S.evaluate(rule([{ f: 'liked', o: 'no' }]), lib, stats, NOW)), ['b', 'd', 'e', 'f']);
  const two = [{ f: 'artist', o: 'is', t: 'Kavi' }, { f: 'year', o: 'atLeast', a: 2020 }];
  assert.deepEqual(ids(S.evaluate(rule(two), lib, stats, NOW)), []);
  assert.deepEqual(ids(S.evaluate(rule(two, { m: 'any' }), lib, stats, NOW)), ['a', 'c', 'd', 'f']);
  assert.deepEqual(ids(S.evaluate(rule([], { s: 'mostPlayed', l: 2 }), lib, stats, NOW)), ['a', 'c']);
  assert.deepEqual(ids(S.evaluate(rule([], { s: 'lastPlayed', l: 3 }), lib, stats, NOW)), ['c', 'a', 'b']);
  assert.deepEqual(ids(S.evaluate(rule([], { s: 'yearNew' }), lib, stats, NOW)), ['f', 'a', 'b', 'c', 'd', 'e']);
  assert.deepEqual(ids(S.evaluate(rule([], { s: 'longest', l: 1 }), lib, stats, NOW)), ['c']);
  assert.deepEqual(ids(S.evaluate(rule([], { s: 'liked' }), lib, stats, NOW)).slice(0, 2), ['c', 'a'], 'recently liked first');
  const r1 = ids(S.evaluate(rule([], { s: 'random' }), lib, stats, NOW, 42)), r2 = ids(S.evaluate(rule([], { s: 'random' }), lib, stats, NOW, 42));
  assert.deepEqual(r1, r2, 'random is stable for a seed');
  assert.deepEqual([...r1].sort(), ['a', 'b', 'c', 'd', 'e', 'f']);
  assert.equal(S.count(rule([{ f: 'plays', o: 'atLeast', a: 1 }], { l: 1 }), lib, stats, NOW), 1, 'the preview count respects the limit');
});

test('built-in lists: Favorites, Most played, Recently played, Recently added, Never played', () => {
  const run = k => ids(S.evaluate(S.autoByKey(k, lib).rule, lib, stats, NOW, 1));
  assert.deepEqual(run('auto:favorites'), ['c', 'a']);
  assert.deepEqual(run('auto:most'), ['a', 'c', 'b']);
  assert.deepEqual(run('auto:recent'), ['c', 'a', 'b']);
  assert.deepEqual(run('auto:added').slice(0, 2), ['a', 'f']);
  assert.deepEqual(run('auto:never').sort(), ['d', 'e', 'f']);
  assert.equal(S.autoByKey('genre:hiphop', lib).name, 'Hip-Hop');
  assert.equal(S.autoByKey('decade:1990', lib).name, '90s');
  assert.equal(S.autoByKey('genre:polka', lib), null);
  // they update themselves: one more play moves a song in
  const more = S.Stats.recordPlay(stats, 'f', NOW);
  assert.ok(ids(S.evaluate(S.autoByKey('auto:most', lib).rule, lib, more, NOW)).includes('f'));
  assert.ok(!ids(S.evaluate(S.autoByKey('auto:never', lib).rule, lib, more, NOW)).includes('f'));
});

test('saved smart playlists keep only the rules: create, edit, delete, survive bad JSON', () => {
  let all = [];
  let r = S.create(all, '  ', { m: 'any', c: [{ f: 'genre', o: 'is', t: 'Pop' }, { f: 'nope', o: 'x' }], s: 'bogus', l: -3 }, NOW);
  all = r.list;
  assert.equal(r.item.name, 'Smart playlist');
  assert.deepEqual(r.item.rule, { m: 'any', c: [{ f: 'genre', o: 'is', t: 'Pop', a: 0, b: 0 }], s: 'artist', l: 0 }, 'unknown fields and sorts are dropped');
  assert.ok(!('ids' in r.item), 'no song list is stored');
  r = S.create(all, 'Smart playlist', S.cleanRule({}), NOW); all = r.list;
  assert.equal(r.item.name, 'Smart playlist 2');
  assert.notEqual(all[0].id, all[1].id);
  all = S.update(all, all[1].id, 'Gym', { c: [{ f: 'plays', o: 'atLeast', a: 5 }], s: 'mostPlayed', l: 25 });
  assert.equal(all[1].name, 'Gym'); assert.equal(all[1].rule.l, 25);
  all = S.update(all, all[1].id, 'smart playlist', all[1].rule);
  assert.equal(all[1].name, 'smart playlist 2', 'renaming to a taken name gets a number');
  all = S.remove(all, all[0].id);
  assert.equal(all.length, 1);
  const back = S.loadSmart(JSON.parse(JSON.stringify(all)));
  assert.deepEqual(back, all, 'round-trips through JSON');
  assert.deepEqual(S.loadSmart(null), []); assert.deepEqual(S.loadSmart([{ id: 1 }, 'x']), []);
  assert.equal(S.ruleSummary(S.cleanRule({ m: 'any', c: [{ f: 'genre', o: 'is', t: 'Pop' }, { f: 'year', o: 'between', a: 2010, b: 2019 }] })), 'Any of: Genre is Pop \u00b7 Year 2010\u20132019');
  assert.equal(S.condSummary(S.newCond('plays')), 'Played 5+ times');
  assert.equal(S.condSummary({ f: 'duration', o: 'atMost', a: 240 }), 'Shorter than 4:00');
  assert.deepEqual(S.opsFor('lastPlayed'), ['inLast', 'notInLast', 'never']);
});

test('stats: plays, last played, likes; JSON matches the Android app', () => {
  let s = {};
  s = S.Stats.recordPlay(s, 'x', 1000); s = S.Stats.recordPlay(s, 'x', 2000);
  assert.deepEqual(S.Stats.get(s, 'x'), { p: 2, l: 2000, f: 0, fa: 0 });
  s = S.Stats.setLiked(s, 'y', true, 3000);
  assert.deepEqual(S.Stats.toJSON(s), { x: { p: 2, l: 2000 }, y: { f: 1, fa: 3000 } });
  assert.equal(S.Stats.setLiked(s, 'y', true, 9), s, 'liking twice changes nothing');
  s = S.Stats.setLiked(s, 'y', false, 4000);
  assert.ok(!('y' in s), 'an unliked, never-played song leaves no entry');
  assert.deepEqual(S.Stats.load({ x: { p: 2, l: 2000 }, bad: 5 }), { x: { p: 2, l: 2000, f: 0, fa: 0 } });
  assert.deepEqual(S.Stats.load('nope'), {});
});

test('play counter: 50% of a short song, 30 s of a long one, real listening only, once per start', () => {
  const c = new S.PlayCounter();
  // a 40 s song counts after 20 s of playing
  c.begin('s', 40000, true, 0);
  assert.equal(c.check(19999), null);
  assert.equal(c.check(20000), 's');
  assert.equal(c.check(39000), null, 'once per start');
  // a 5 min song counts after 30 s, and paused time does not count
  c.begin('l', 300000, true, 100000);
  assert.equal(c.setPlaying(false, 110000), null);     // 10 s heard
  assert.equal(c.check(500000), null);                  // paused for minutes
  c.setPlaying(true, 500000);
  assert.equal(c.msUntilCount(500000), 20000);
  assert.equal(c.check(519999), null);
  assert.equal(c.check(520000), 'l');
  // seeking ahead adds nothing: only the clock while playing does
  c.begin('k', 200000, true, 0);
  assert.equal(c.check(5000), null, 'a 5 s listen is not a play however far the seek bar moved');
  // skipping a song early never counts it; begin() reports a song that crossed the line on the way out
  assert.equal(c.begin('m', 60000, true, 10000), null);
  assert.equal(c.begin('n', 60000, true, 40000), 'm', 'm had 30 s, counted as it ended');
  // repeat-one: the same song again is a new start
  c.begin('r', 10000, true, 0); assert.equal(c.check(5000), 'r');
  c.begin('r', 10000, true, 10000); assert.equal(c.check(15000), 'r');
  // unknown length waits for 30 s, and learns the length later
  c.begin('u', 0, true, 0); assert.equal(c.check(20000), null); c.setDuration(30000); assert.equal(c.check(20000), 'u');
  // nothing loaded
  c.begin(null, 0, true, 0); assert.equal(c.check(99999), null); assert.equal(c.msUntilCount(0), null);
});

test('make a mix from a song: the song, its artist, then same genre and era, then the rest of the genre', () => {
  const big = [];
  for (let i = 0; i < 6; i++) big.push(T('sen' + i, { artist: 'Aria Sen', genre: 'Pop', year: 2020 }));
  for (let i = 0; i < 10; i++) big.push(T('pe' + i, { artist: 'Other' + i, genre: 'pop', year: 2018 + (i % 4) }));   // same genre, same era
  for (let i = 0; i < 10; i++) big.push(T('pold' + i, { artist: 'Old' + i, genre: 'Pop', year: 1980 }));             // same genre, other era
  for (let i = 0; i < 10; i++) big.push(T('re' + i, { artist: 'Rock' + i, genre: 'Rock', year: 2021 }));             // other genre, same era
  for (let i = 0; i < 10; i++) big.push(T('x' + i, { artist: 'Jazz' + i, genre: 'Jazz', year: 1960 }));              // nothing in common
  const seed = big[0];
  const m = S.mixFromSong(seed, big, {}, 7);
  assert.equal(m.name, `${seed.title} mix`);
  assert.equal(m.ids[0], seed.id, 'starts with the song');
  assert.equal(new Set(m.ids).size, m.ids.length, 'no repeats');
  assert.equal(m.ids.length, 36, 'everything related, nothing unrelated (under the 40 cap)');
  const kind = id => id.replace(/\d+$/, '');
  assert.deepEqual([...new Set(m.ids.map(kind))], ['sen', 'pe', 'pold', 're'], 'tiers in order');
  assert.ok(!m.ids.some(id => id.startsWith('x')));
  assert.deepEqual(S.mixFromSong(seed, big, {}, 7).ids, m.ids, 'same seed, same mix');
  assert.notDeepEqual(S.mixFromSong(seed, big, {}, 8).ids, m.ids, 'a new mix is shuffled differently');
  // liked songs float up inside a tier
  const liked = { pe9: { p: 0, l: 0, f: 1, fa: 1 } };
  assert.equal(S.mixFromSong(seed, big, liked, 7).ids[6], 'pe9');
  // the 40 cap
  const huge = big.concat(Array.from({ length: 60 }, (_, i) => T('more' + i, { artist: 'M' + i, genre: 'Pop', year: 2020 })));
  assert.equal(S.mixFromSong(seed, huge, {}, 1).ids.length, 40);
});

test('make a mix from an artist: their songs first (with features), then their genre and era', () => {
  const m = S.mixFromArtist('Aria Sen', lib, stats, 3);
  assert.equal(m.name, 'Aria Sen mix');
  assert.deepEqual(m.ids.slice(0, 3).sort(), ['a', 'b', 'd'], 'own songs and a featured one first');
  assert.ok(m.ids.includes('f'), 'a song from her era (2019 ± 5) follows');
  assert.ok(!m.ids.includes('e'), 'no genre, no year: not related');
});
