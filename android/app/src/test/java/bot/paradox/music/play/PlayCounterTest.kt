package bot.paradox.music.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayCounterTest {
    @Test fun countsAfterThirtySecondsOfALongSong() {
        val c = PlayCounter()
        assertNull(c.begin("a", 300_000, true, 0))
        assertNull(c.check(29_999))
        assertEquals("a", c.check(30_000))
        assertNull(c.check(60_000))  // only once per start
    }

    @Test fun countsAfterHalfOfAShortSong() {
        val c = PlayCounter()
        c.begin("short", 40_000, true, 0)
        assertNull(c.check(19_999))
        assertEquals("short", c.check(20_000))
    }

    @Test fun pausedTimeDoesNotCount() {
        val c = PlayCounter()
        c.begin("a", 300_000, true, 0)
        assertNull(c.setPlaying(false, 20_000))
        assertNull(c.check(500_000))           // paused for a long time
        assertNull(c.setPlaying(true, 500_000))
        assertNull(c.check(509_999))
        assertEquals("a", c.check(510_000))     // 20 s + 10 s heard
    }

    @Test fun skippingEarlyDoesNotCountButTheTransitionFlushesAHeardSong() {
        val c = PlayCounter()
        c.begin("a", 300_000, true, 0)
        assertNull(c.begin("b", 300_000, true, 10_000))   // skipped "a" after 10 s
        assertEquals("b", c.begin("c", 300_000, true, 45_000))  // "b" heard 35 s, counted at the switch
        assertEquals("c", c.setPlaying(false, 76_000))     // counted on pause too
    }

    @Test fun repeatOneCountsEachLoop() {
        val c = PlayCounter()
        c.begin("a", 20_000, true, 0)
        assertEquals("a", c.check(10_000))
        assertNull(c.begin("a", 20_000, true, 20_000))  // already counted this loop
        assertEquals("a", c.check(30_000))               // next loop counts again
    }

    @Test fun unknownLengthUsesThirtySecondsAndLearnsTheRealLength() {
        val c = PlayCounter()
        c.begin("a", 0, true, 0)
        assertEquals(30_000, c.thresholdMs())
        c.setDuration(10_000)
        assertEquals(5_000, c.thresholdMs())
        assertEquals("a", c.check(5_000))
    }

    @Test fun startedPausedAndNothingLoaded() {
        val c = PlayCounter()
        assertNull(c.check(100_000))
        assertNull(c.msUntilCount(0))
        c.begin("a", 300_000, false, 0)
        assertNull(c.check(100_000))
        assertEquals(30_000L, c.msUntilCount(100_000))
        c.setPlaying(true, 100_000)
        assertEquals(5_000L, c.msUntilCount(125_000))
        assertEquals("a", c.check(130_000))
        assertNull(c.msUntilCount(130_000))
    }
}
