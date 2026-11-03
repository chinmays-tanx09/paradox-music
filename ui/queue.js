/* Paradox Music: play queue with shuffle and repeat. Pure logic, no DOM, so it is unit-tested
   with node --test. Each entry has its own key so the same song can be queued twice. */
(function (root) {
  'use strict';
  const REPEAT = ['off', 'all', 'one'];

  class Queue {
    constructor(rng) {
      this.rng = rng || Math.random;
      this.items = []; // play order
      this.base = []; // the order as given (what "shuffle off" goes back to)
      this.pos = -1;
      this.shuffle = false;
      this.repeat = 'off';
      this.seq = 0;
    }
    _e(id) { return { k: ++this.seq, id }; }
    get length() { return this.items.length; }
    current() { return this.pos >= 0 && this.pos < this.items.length ? this.items[this.pos].id : null; }
    ids() { return this.items.map(e => e.id); }

    /** Replace the queue with ids, starting at index start. */
    set(ids, start, opts) {
      opts = opts || {};
      if (typeof opts.shuffle === 'boolean') this.shuffle = opts.shuffle;
      this.base = ids.map(id => this._e(id));
      this.items = this.base.slice();
      if (!ids.length) { this.pos = -1; return null; }
      const s = start == null || start < 0 ? (this.shuffle ? Math.floor(this.rng() * ids.length) : 0) : Math.min(start, ids.length - 1);
      this.pos = s;
      if (this.shuffle) this._shuffle();
      return this.current();
    }

    /** Current song first, everything else in random order (Fisher–Yates). */
    _shuffle() {
      if (this.pos < 0) { this.items = this.base.slice(); return; }
      const cur = this.items[this.pos];
      const rest = this.base.filter(e => e !== cur);
      for (let i = rest.length - 1; i > 0; i--) {
        const j = Math.floor(this.rng() * (i + 1));
        [rest[i], rest[j]] = [rest[j], rest[i]];
      }
      this.items = [cur].concat(rest);
      this.pos = 0;
    }

    setShuffle(on) {
      on = !!on;
      if (on === this.shuffle) return;
      this.shuffle = on;
      if (on) this._shuffle();
      else {
        const cur = this.items[this.pos];
        this.items = this.base.slice();
        this.pos = cur ? this.items.indexOf(cur) : -1;
      }
    }

    setRepeat(mode) { if (REPEAT.includes(mode)) this.repeat = mode; }
    cycleRepeat() { this.repeat = REPEAT[(REPEAT.indexOf(this.repeat) + 1) % REPEAT.length]; return this.repeat; }

    /** What plays after the current song ends (for preloading); does not move. */
    peekNext() {
      if (!this.items.length) return null;
      if (this.repeat === 'one') return this.current();
      if (this.pos + 1 < this.items.length) return this.items[this.pos + 1].id;
      return this.repeat === 'all' ? this.items[0].id : null;
    }

    /** Move forward. auto = the song ended on its own (repeat-one replays it then). Null = stop. */
    next(auto) {
      if (!this.items.length) return null;
      if (auto && this.repeat === 'one') return this.current();
      if (this.pos + 1 < this.items.length) { this.pos++; return this.current(); }
      if (this.repeat !== 'off') { this.pos = 0; return this.current(); }
      return null;
    }

    prev() {
      if (!this.items.length) return null;
      if (this.pos > 0) this.pos--;
      else if (this.repeat !== 'off') this.pos = this.items.length - 1;
      return this.current();
    }

    jump(i) { if (i >= 0 && i < this.items.length) this.pos = i; return this.current(); }

    /** Insert right after the current song ("Play next"). */
    playNext(ids) {
      if (!ids.length) return;
      if (this.pos < 0) { this.set(ids, 0); return; }
      const es = ids.map(id => this._e(id));
      const cur = this.items[this.pos];
      this.items.splice(this.pos + 1, 0, ...es);
      const bi = this.base.indexOf(cur);
      this.base.splice(bi + 1, 0, ...es);
    }

    /** Append to the end ("Add to queue"). */
    add(ids) {
      if (!ids.length) return;
      if (this.pos < 0) { this.set(ids, 0); return; }
      const es = ids.map(id => this._e(id));
      this.items.push(...es);
      this.base.push(...es);
    }

    /** Remove the entry at play position i. Removing the current song keeps pos on the song after it. */
    remove(i) {
      if (i < 0 || i >= this.items.length) return;
      const [e] = this.items.splice(i, 1);
      this.base.splice(this.base.indexOf(e), 1);
      if (i < this.pos) this.pos--;
      if (this.pos >= this.items.length) this.pos = this.items.length - 1;
    }

    /** Drop everything after the current song. */
    clearUpcoming() {
      const keep = new Set(this.items.slice(0, this.pos + 1));
      this.items = this.items.slice(0, this.pos + 1);
      this.base = this.base.filter(e => keep.has(e));
    }

    upcoming() { return this.items.slice(this.pos + 1).map((e, n) => ({ index: this.pos + 1 + n, id: e.id })); }

    /** Drop entries whose song is gone (e.g. deleted from disk). */
    prune(exists) {
      const cur = this.items[this.pos];
      this.items = this.items.filter(e => exists(e.id));
      this.base = this.base.filter(e => exists(e.id));
      const p = this.items.indexOf(cur);
      this.pos = p >= 0 ? p : Math.min(this.pos, this.items.length - 1);
    }

    toJSON() {
      const ix = new Map(this.items.map((e, i) => [e, i]));
      return { items: this.ids(), base: this.base.map(e => ix.get(e)), pos: this.pos, shuffle: this.shuffle, repeat: this.repeat };
    }

    static fromJSON(o, exists, rng) {
      const q = new Queue(rng);
      if (!o || !Array.isArray(o.items)) return q;
      q.shuffle = !!o.shuffle;
      q.setRepeat(o.repeat || 'off');
      const all = o.items.map(id => q._e(id));
      const base = Array.isArray(o.base) && o.base.length === all.length ? o.base.map(i => all[i]).filter(Boolean) : all.slice();
      q.items = all; q.base = base.length === all.length ? base : all.slice();
      q.pos = typeof o.pos === 'number' ? o.pos : -1;
      if (exists) q.prune(exists);
      if (q.pos >= q.items.length) q.pos = q.items.length - 1;
      return q;
    }
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = Queue;
  else root.PMQueue = Queue;
})(this);
