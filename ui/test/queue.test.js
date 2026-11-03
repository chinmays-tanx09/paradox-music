// node --test ui/test/
const test = require('node:test');
const assert = require('node:assert/strict');
const Queue = require('../queue.js');

function seeded(seed) { // deterministic rng (mulberry32)
  return function () { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const ids = ['a', 'b', 'c', 'd', 'e'];

test('plays in order and stops at the end with repeat off', () => {
  const q = new Queue();
  assert.equal(q.set(ids, 0), 'a');
  assert.deepEqual([q.next(true), q.next(true), q.next(true), q.next(true)], ['b', 'c', 'd', 'e']);
  assert.equal(q.peekNext(), null);
  assert.equal(q.next(true), null);
  assert.equal(q.current(), 'e');
});

test('repeat all wraps both ways', () => {
  const q = new Queue(); q.set(ids, 4); q.setRepeat('all');
  assert.equal(q.peekNext(), 'a');
  assert.equal(q.next(true), 'a');
  assert.equal(q.prev(), 'e');
});

test('repeat one replays on auto-advance but manual next moves on', () => {
  const q = new Queue(); q.set(ids, 1); q.setRepeat('one');
  assert.equal(q.peekNext(), 'b');
  assert.equal(q.next(true), 'b');
  assert.equal(q.next(false), 'c');
  q.jump(4);
  assert.equal(q.next(false), 'a', 'manual next at the end wraps when repeat is on');
});

test('cycleRepeat goes off → all → one → off', () => {
  const q = new Queue();
  assert.deepEqual([q.cycleRepeat(), q.cycleRepeat(), q.cycleRepeat()], ['all', 'one', 'off']);
});

test('prev at the start stays on the first song with repeat off', () => {
  const q = new Queue(); q.set(ids, 0);
  assert.equal(q.prev(), 'a');
});

test('shuffle keeps the current song first and plays every song exactly once', () => {
  const q = new Queue(seeded(7)); q.set(ids, 2, { shuffle: true });
  assert.equal(q.current(), 'c');
  assert.equal(q.pos, 0);
  const order = [q.current()];
  let n; while ((n = q.next(true)) !== null) order.push(n);
  assert.deepEqual([...order].sort(), [...ids].sort());
  assert.notDeepEqual(order, ['c', 'a', 'b', 'd', 'e'], 'seeded shuffle should change the order');
});

test('turning shuffle off goes back to the original order at the current song', () => {
  const q = new Queue(seeded(3)); q.set(ids, 0);
  q.next(true); // b
  q.setShuffle(true);
  assert.equal(q.current(), 'b');
  q.next(true);
  const cur = q.current();
  q.setShuffle(false);
  assert.equal(q.current(), cur);
  assert.deepEqual(q.ids(), ids);
  assert.equal(q.pos, ids.indexOf(cur));
});

test('shuffle is deterministic for a given rng', () => {
  const a = new Queue(seeded(42)); a.set(ids, 0, { shuffle: true });
  const b = new Queue(seeded(42)); b.set(ids, 0, { shuffle: true });
  assert.deepEqual(a.ids(), b.ids());
});

test('play next inserts after the current song, add to queue appends', () => {
  const q = new Queue(); q.set(ids, 1);
  q.playNext(['x', 'y']);
  assert.deepEqual(q.ids(), ['a', 'b', 'x', 'y', 'c', 'd', 'e']);
  q.add(['z']);
  assert.equal(q.ids().at(-1), 'z');
  assert.equal(q.next(true), 'x');
});

test('play next while shuffled survives turning shuffle off', () => {
  const q = new Queue(seeded(9)); q.set(ids, 0, { shuffle: true });
  const cur = q.current();
  q.playNext(['x']);
  assert.equal(q.peekNext(), 'x');
  q.setShuffle(false);
  assert.equal(q.ids()[q.ids().indexOf(cur) + 1], 'x');
});

test('the same song can be queued twice', () => {
  const q = new Queue(); q.set(['a'], 0); q.add(['a']);
  assert.deepEqual(q.ids(), ['a', 'a']);
  assert.equal(q.next(true), 'a');
  assert.equal(q.pos, 1);
});

test('remove before, at and after the current position', () => {
  const q = new Queue(); q.set(ids, 2); // c
  q.remove(0); assert.equal(q.current(), 'c'); assert.equal(q.pos, 1);
  q.remove(3); assert.deepEqual(q.ids(), ['b', 'c', 'd']);
  q.remove(1); assert.equal(q.current(), 'd', 'removing the current song lands on the next one');
});

test('clearUpcoming keeps history and the current song', () => {
  const q = new Queue(); q.set(ids, 1); q.clearUpcoming();
  assert.deepEqual(q.ids(), ['a', 'b']);
  assert.equal(q.peekNext(), null);
  assert.deepEqual(q.upcoming(), []);
});

test('empty queue is safe', () => {
  const q = new Queue();
  assert.equal(q.current(), null); assert.equal(q.next(true), null); assert.equal(q.prev(), null); assert.equal(q.peekNext(), null);
  q.add(['a']); assert.equal(q.current(), 'a');
});

test('save and restore, dropping songs that no longer exist', () => {
  const q = new Queue(seeded(5)); q.set(ids, 3, { shuffle: true }); q.setRepeat('all');
  const saved = JSON.parse(JSON.stringify(q));
  const r = Queue.fromJSON(saved, id => id !== 'a');
  assert.equal(r.current(), 'd');
  assert.equal(r.repeat, 'all'); assert.equal(r.shuffle, true);
  assert.ok(!r.ids().includes('a'));
  r.setShuffle(false);
  assert.deepEqual(r.ids(), ['b', 'c', 'd', 'e']);
});
