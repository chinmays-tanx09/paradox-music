package bot.paradox.music.play

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class QueueLogicTest {
    private val ids = (1..10).map { "t$it" }
    private fun logic() = QueueLogic(Random(42))
    private fun Queue.tracks() = entries.map { it.trackId }

    @Test fun playInOrder() {
        val q = logic().playList(ids, 3, shuffle = false)
        assertEquals(ids, q.tracks()); assertEquals(3, q.index); assertEquals("t4", q.current!!.trackId); assertFalse(q.shuffled)
        assertEquals(ids.drop(4), q.upNext.map { it.trackId })
    }

    @Test fun playShuffledStartsWithTheTappedSongAndKeepsEverySong() {
        val q = logic().playList(ids, 5, shuffle = true)
        assertEquals("t6", q.current!!.trackId); assertEquals(0, q.index)
        assertEquals(ids.toSet(), q.tracks().toSet()); assertEquals(10, q.entries.size)
        assertNotEquals(ids.drop(6) + ids.take(5), q.tracks().drop(1))
        assertEquals(ids, q.original.map { it.trackId })
    }

    @Test fun shuffleOnKeepsHistoryAndCurrentInPlace() {
        val l = logic()
        val q = l.setShuffle(l.playList(ids, 4, false), true)
        assertTrue(q.shuffled); assertEquals(4, q.index); assertEquals("t5", q.current!!.trackId)
        assertEquals(ids.take(5), q.tracks().take(5))
        assertEquals(ids.drop(5).toSet(), q.tracks().drop(5).toSet())
    }

    @Test fun shuffleOffRestoresOriginalOrderAtTheCurrentSong() {
        val l = logic()
        var q = l.playList(ids, 0, true)
        q = q.copy(index = 3)                       // three songs later
        val cur = q.current
        q = l.setShuffle(q, false)
        assertFalse(q.shuffled); assertEquals(ids, q.tracks()); assertEquals(cur, q.current)
    }

    @Test fun playNextIsReallyNextEvenWhenShuffledAndSurvivesUnshuffle() {
        val l = logic()
        var q = l.playList(ids, 2, true)
        q = l.playNext(q, listOf("X"))
        assertEquals("X", q.entries[q.index + 1].trackId)
        q = l.setShuffle(q, false)
        assertEquals("X", q.entries[q.index + 1].trackId)
        assertEquals(11, q.entries.size)
    }

    @Test fun addToQueueGoesToTheEnd() {
        val l = logic()
        val q = l.addToQueue(l.playList(ids, 0, false), listOf("A", "B"))
        assertEquals(listOf("A", "B"), q.tracks().takeLast(2))
        assertEquals(listOf("A", "B"), q.original.takeLast(2).map { it.trackId })
    }

    @Test fun queueOpsOnAnEmptyQueueStartIt() {
        val l = logic()
        assertEquals(listOf("A"), l.playNext(Queue(), listOf("A")).tracks())
        assertEquals(listOf("A", "B"), l.addToQueue(Queue(), listOf("A", "B")).tracks())
        assertEquals(Queue(), l.playList(emptyList(), 0, true))
    }

    @Test fun sameSongTwiceGetsTwoSlots() {
        val l = logic()
        val q = l.addToQueue(l.playList(listOf("A"), 0, false), listOf("A"))
        assertEquals(2, q.entries.size); assertNotEquals(q.entries[0].uid, q.entries[1].uid)
        val r = l.remove(q, q.entries[1].uid)
        assertEquals(1, r.entries.size); assertEquals(q.entries[0], r.current)
    }

    @Test fun removeKeepsTheCurrentSong() {
        val l = logic()
        val q = l.playList(ids, 5, false)
        val before = l.remove(q, q.entries[1].uid)
        assertEquals("t6", before.current!!.trackId); assertEquals(4, before.index)
        val after = l.remove(q, q.entries[8].uid)
        assertEquals("t6", after.current!!.trackId); assertEquals(5, after.index)
        val cur = l.remove(q, q.entries[5].uid)
        assertEquals("t7", cur.current!!.trackId)
        val last = l.playList(listOf("a", "b"), 1, false)
        assertEquals("a", l.remove(last, last.entries[1].uid).current!!.trackId)
    }

    @Test fun moveKeepsCurrent() {
        val l = logic()
        val q = l.move(l.playList(ids, 2, false), 0, 9)
        assertEquals("t3", q.current!!.trackId); assertEquals("t1", q.tracks().last()); assertEquals(1, q.index)
    }

    @Test fun pruneDropsSongsNoLongerInTheLibrary() {
        val l = logic()
        val q = l.prune(l.playList(ids, 0, false), setOf("t1", "t3"))
        assertEquals(listOf("t1", "t3"), q.tracks())
    }

    @Test fun mediaIdRoundTrip() {
        val e = QEntry(17, "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fa%7Cb.mp3")
        assertEquals(e, QEntry.parse(e.mediaId)); assertNull(QEntry.parse("nonsense")); assertNull(QEntry.parse("|x"))
    }

    @Test fun repeatCycleIsOffAllOne() {
        assertEquals(Repeat.ALL, Repeat.OFF.next()); assertEquals(Repeat.ONE, Repeat.ALL.next()); assertEquals(Repeat.OFF, Repeat.ONE.next())
    }

    @Test fun nextWithRepeat() {
        assertNull(QueueLogic.nextIndex(3, 2, Repeat.OFF, false))
        assertEquals(0, QueueLogic.nextIndex(3, 2, Repeat.ALL, false))
        assertEquals(2, QueueLogic.nextIndex(3, 2, Repeat.ONE, false))
        assertEquals(0, QueueLogic.nextIndex(3, 2, Repeat.ONE, true))     // a tap on next still moves on
        assertEquals(1, QueueLogic.nextIndex(3, 0, Repeat.OFF, true))
        assertNull(QueueLogic.nextIndex(0, 0, Repeat.ALL, false))
    }

    @Test fun previousRestartsAfterThreeSeconds() {
        assertEquals(2 to true, QueueLogic.previousIndex(5, 2, 4000, Repeat.OFF))
        assertEquals(1 to false, QueueLogic.previousIndex(5, 2, 1000, Repeat.OFF))
        assertEquals(0 to true, QueueLogic.previousIndex(5, 0, 1000, Repeat.OFF))
        assertEquals(4 to false, QueueLogic.previousIndex(5, 0, 1000, Repeat.ALL))
    }
}
