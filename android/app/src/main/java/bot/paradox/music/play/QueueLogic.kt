package bot.paradox.music.play

import kotlin.random.Random

enum class Repeat { OFF, ALL, ONE;
    fun next(): Repeat = when (this) { OFF -> ALL; ALL -> ONE; ONE -> OFF }
}

/** One slot in the play queue. [uid] tells apart two copies of the same song. */
data class QEntry(val uid: Long, val trackId: String) {
    /** The media id the player carries: "uid|trackId". */
    val mediaId: String get() = "$uid|$trackId"
    companion object {
        fun parse(mediaId: String): QEntry? {
            val i = mediaId.indexOf('|'); if (i <= 0) return null
            return mediaId.substring(0, i).toLongOrNull()?.let { QEntry(it, mediaId.substring(i + 1)) }
        }
    }
}

/**
 * The queue, independent of the player: the order that plays ([entries]), the current slot,
 * and the order to return to when shuffle goes off ([original]).
 * Shuffle is done here, by reordering what comes after the current song, so "Up next" always shows
 * exactly what plays, and "Play next" really is next even while shuffled.
 */
data class Queue(val entries: List<QEntry> = emptyList(), val index: Int = 0, val shuffled: Boolean = false, val original: List<QEntry> = emptyList()) {
    val current: QEntry? get() = entries.getOrNull(index)
    val upNext: List<QEntry> get() = if (entries.isEmpty()) emptyList() else entries.drop(index + 1)
}

class QueueLogic(private val random: Random = Random.Default, private var nextUid: Long = 1) {
    fun uid(): Long = nextUid++
    fun seedUid(atLeast: Long) { if (nextUid <= atLeast) nextUid = atLeast + 1 }

    /** Play a list starting at [start]; with shuffle on, the started song comes first and the rest is shuffled. */
    fun playList(trackIds: List<String>, start: Int, shuffle: Boolean): Queue {
        if (trackIds.isEmpty()) return Queue()
        val es = trackIds.map { QEntry(uid(), it) }
        val s = start.coerceIn(0, es.lastIndex)
        return if (shuffle) Queue(listOf(es[s]) + (es.take(s) + es.drop(s + 1)).shuffled(random), 0, true, es)
        else Queue(es, s, false, es)
    }

    /** "Shuffle all": a random first song too. */
    fun shuffleAll(trackIds: List<String>): Queue = if (trackIds.isEmpty()) Queue() else playList(trackIds, random.nextInt(trackIds.size), true)

    fun setShuffle(q: Queue, on: Boolean): Queue {
        if (on == q.shuffled || q.entries.isEmpty()) return q.copy(shuffled = on)
        val cur = q.current ?: return q.copy(shuffled = on)
        return if (on) {
            // Played history stays where it is; only what is still ahead gets shuffled.
            val before = q.entries.take(q.index)
            val ahead = q.entries.drop(q.index + 1).shuffled(random)
            Queue(before + cur + ahead, q.index, true, q.entries)
        } else {
            val alive = q.entries.toSet()
            val back = q.original.filter { it in alive }.toMutableList()
            q.entries.filter { it !in back.toSet() }.forEach { back.add(it) }   // anything added while shuffled
            Queue(back, back.indexOf(cur).coerceAtLeast(0), false, back)
        }
    }

    /** Insert right after the current song (in both orders, so it stays "next" after unshuffle too). */
    fun playNext(q: Queue, trackIds: List<String>): Queue {
        if (trackIds.isEmpty()) return q
        val add = trackIds.map { QEntry(uid(), it) }
        if (q.entries.isEmpty()) return Queue(add, 0, q.shuffled, add)
        val e = q.entries.toMutableList().apply { addAll(q.index + 1, add) }
        val cur = q.current
        val oi = q.original.indexOf(cur)
        val o = q.original.toMutableList().apply { if (oi >= 0) addAll(oi + 1, add) else addAll(add) }
        return q.copy(entries = e, original = o)
    }

    fun addToQueue(q: Queue, trackIds: List<String>): Queue {
        if (trackIds.isEmpty()) return q
        val add = trackIds.map { QEntry(uid(), it) }
        if (q.entries.isEmpty()) return Queue(add, 0, q.shuffled, add)
        return q.copy(entries = q.entries + add, original = q.original + add)
    }

    fun remove(q: Queue, uid: Long): Queue {
        val i = q.entries.indexOfFirst { it.uid == uid }
        if (i < 0) return q
        val e = q.entries.toMutableList().apply { removeAt(i) }
        val idx = when { e.isEmpty() -> 0; i < q.index -> q.index - 1; i == q.index -> q.index.coerceAtMost(e.lastIndex); else -> q.index }
        return q.copy(entries = e, index = idx, original = q.original.filter { it.uid != uid })
    }

    fun move(q: Queue, from: Int, to: Int): Queue {
        if (from !in q.entries.indices || to !in q.entries.indices || from == to) return q
        val e = q.entries.toMutableList(); val x = e.removeAt(from); e.add(to, x)
        val cur = q.current
        return q.copy(entries = e, index = e.indexOf(cur), original = if (q.shuffled) q.original else e)
    }

    /** Drop songs that are no longer in the library (after a rescan or folder change). */
    fun prune(q: Queue, alive: Set<String>): Queue {
        var r = q
        q.entries.filter { it.trackId !in alive }.forEach { r = remove(r, it.uid) }
        return r
    }

    companion object {
        /** Where the player goes on "next" (null = stop), given repeat. Repeat ONE still skips on a user tap. */
        fun nextIndex(size: Int, index: Int, repeat: Repeat, userAction: Boolean): Int? = when {
            size == 0 -> null
            repeat == Repeat.ONE && !userAction -> index
            index + 1 < size -> index + 1
            repeat == Repeat.OFF -> null
            else -> 0
        }

        /** "Previous": restart the song if more than 3 s in, else the one before (wraps only with repeat all). */
        fun previousIndex(size: Int, index: Int, positionMs: Long, repeat: Repeat): Pair<Int, Boolean> = when {
            size == 0 -> 0 to true
            positionMs > 3000 -> index to true
            index > 0 -> index - 1 to false
            repeat == Repeat.ALL -> size - 1 to false
            else -> index to true
        }
    }
}
