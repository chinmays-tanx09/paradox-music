package bot.paradox.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric only for org.json (the plain JVM stub returns null); the engine itself is pure Kotlin. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartEngineTest {
    private val day = SmartEngine.DAY
    private val now = 1_000 * day

    private fun t(id: String, title: String, artist: String, genre: String?, year: Int?, ms: Long = 200_000, added: Long = now - 100 * day, album: String = "A$id") =
        Track(id, title, artist, album, null, genre, year, 1, 1, ms, null, "", "$title.mp3", 1, 1, added, "r")

    private val lib = listOf(
        t("1", "Kesariya", "Arijit Singh", "Bollywood", 2022, 268_000, added = now - 2 * day, album = "Brahmastra"),
        t("2", "Tum Hi Ho", "Arijit Singh", "Bollywood; Romantic", 2013, 262_000),
        t("3", "Yellow", "Coldplay", "Alternative/Rock", 2000, 266_000),
        t("4", "Blinding Lights", "The Weeknd", "Pop", 2020, 200_000, added = now - 10 * day),
        t("5", "Levitating", "Dua Lipa", "pop", 2020, 203_000),
        t("6", "Untagged", "Unknown artist", null, null, 0),
        t("7", "Belageddu", "Vijay Prakash", "Kannada", 2016, 238_000),
        t("8", "Chaleya", "Arijit Singh, Shilpa Rao", "Bollywood", 2023, 200_000, added = now - 1 * day),
    )
    private val stats = mapOf(
        "1" to TrackStats(plays = 12, lastPlayed = now - 1000, liked = true, likedAt = now - 5 * day),
        "2" to TrackStats(plays = 5, lastPlayed = now - 3 * day),
        "3" to TrackStats(plays = 1, lastPlayed = now - 40 * day, liked = true, likedAt = now - day),
        "4" to TrackStats(plays = 7, lastPlayed = now - 2 * day),
    )

    private fun ids(rule: SmartRule) = SmartEngine.evaluate(rule, lib, stats, now).map { it.id }
    private fun one(c: Cond, sort: SmartSort = SmartSort.TITLE) = ids(SmartRule(Match.ALL, listOf(c), sort))

    @Test fun genreIsMatchesSplitTagsAndIgnoresCaseAndSpelling() {
        assertEquals(listOf("4", "5"), one(Cond(Field.GENRE, Op.IS, "Pop")).sorted())
        assertEquals(listOf("1", "2", "8"), one(Cond(Field.GENRE, Op.IS, "bollywood")).sorted())
        assertEquals(listOf("2"), one(Cond(Field.GENRE, Op.IS, "Romantic")))
        assertEquals(listOf("3"), one(Cond(Field.GENRE, Op.IS, "rock")))
        assertFalse("6" in one(Cond(Field.GENRE, Op.IS, "Pop")))
        // "is not" keeps untagged songs; "contains" works inside words.
        assertTrue("6" in one(Cond(Field.GENRE, Op.IS_NOT, "Pop")))
        assertEquals(listOf("3"), one(Cond(Field.GENRE, Op.CONTAINS, "altern")))
        assertFalse("3" in one(Cond(Field.GENRE, Op.NOT_CONTAINS, "rock")))
    }

    @Test fun artistMatchesFeaturedArtistsAndAccents() {
        assertEquals(listOf("1", "2", "8"), one(Cond(Field.ARTIST, Op.IS, "arijit singh")).sorted())
        assertEquals(listOf("8"), one(Cond(Field.ARTIST, Op.IS, "Shilpa Rao")))
        assertEquals(listOf("4"), one(Cond(Field.ARTIST, Op.CONTAINS, "wéeknd")))
        assertEquals(listOf("1"), one(Cond(Field.ALBUM, Op.IS, "Brahmastra")))
    }

    @Test fun yearRangeIsInclusiveAndUntaggedYearNeverMatches() {
        assertEquals(listOf("4", "5", "7"), one(Cond(Field.YEAR, Op.BETWEEN, a = 2015, b = 2020)).sorted())
        assertEquals(listOf("4", "5", "7"), one(Cond(Field.YEAR, Op.BETWEEN, a = 2020, b = 2015)).sorted())  // reversed bounds
        assertEquals(listOf("1", "8"), one(Cond(Field.YEAR, Op.AT_LEAST, a = 2021)).sorted())
        assertEquals(listOf("3"), one(Cond(Field.YEAR, Op.AT_MOST, a = 2005)))
    }

    @Test fun playCountLikedAndLength() {
        assertEquals(listOf("1", "4", "2"), one(Cond(Field.PLAYS, Op.AT_LEAST, a = 5), SmartSort.MOST_PLAYED))
        assertEquals(listOf("5", "6", "7", "8"), one(Cond(Field.PLAYS, Op.AT_MOST, a = 0)).sorted())
        assertEquals(listOf("2", "4"), one(Cond(Field.PLAYS, Op.BETWEEN, a = 2, b = 7)).sorted())
        assertEquals(listOf("3", "1"), one(Cond(Field.LIKED, Op.YES), SmartSort.LIKED_NEW))
        assertEquals(6, one(Cond(Field.LIKED, Op.NO)).size)
        assertEquals(listOf("4", "8"), one(Cond(Field.DURATION, Op.AT_MOST, a = 200)).sorted())  // 0-length song excluded
        assertEquals(listOf("1"), one(Cond(Field.DURATION, Op.AT_LEAST, a = 267)))
    }

    @Test fun addedAndLastPlayedDates() {
        assertEquals(listOf("1", "8"), one(Cond(Field.ADDED, Op.IN_LAST, a = 7)).sorted())
        assertEquals(listOf("1", "4", "8"), one(Cond(Field.ADDED, Op.IN_LAST, a = 30)).sorted())
        assertEquals(5, one(Cond(Field.ADDED, Op.NOT_IN_LAST, a = 30)).size)
        assertEquals(listOf("1", "2", "4"), one(Cond(Field.LAST_PLAYED, Op.IN_LAST, a = 7)).sorted())
        assertEquals(listOf("5", "6", "7", "8"), one(Cond(Field.LAST_PLAYED, Op.NEVER)).sorted())
        // "not in the last 30 days" includes songs never played.
        assertEquals(listOf("3", "5", "6", "7", "8"), one(Cond(Field.LAST_PLAYED, Op.NOT_IN_LAST, a = 30)).sorted())
    }

    @Test fun allVersusAny() {
        val pop = Cond(Field.GENRE, Op.IS, "Pop"); val recent = Cond(Field.YEAR, Op.AT_LEAST, a = 2021)
        assertEquals(emptyList<String>(), ids(SmartRule(Match.ALL, listOf(pop, recent))))
        assertEquals(listOf("1", "4", "5", "8"), ids(SmartRule(Match.ANY, listOf(pop, recent))).sorted())
        val bolly = Cond(Field.GENRE, Op.IS, "Bollywood"); val played = Cond(Field.PLAYS, Op.AT_LEAST, a = 5)
        assertEquals(listOf("1", "2"), ids(SmartRule(Match.ALL, listOf(bolly, played))).sorted())
    }

    @Test fun emptyRuleOrUnfinishedTextMatchesEverything() {
        assertEquals(lib.size, ids(SmartRule()).size)
        assertEquals(lib.size, ids(SmartRule(Match.ALL, listOf(Cond(Field.GENRE, Op.IS, "  ")))).size)
    }

    @Test fun sortAndLimit() {
        assertEquals(listOf("1", "4"), ids(SmartRule(Match.ALL, emptyList(), SmartSort.MOST_PLAYED, 2)))
        assertEquals(listOf("8", "1", "4"), ids(SmartRule(Match.ALL, emptyList(), SmartSort.ADDED_NEW, 3)))
        assertEquals("8", ids(SmartRule(Match.ALL, emptyList(), SmartSort.YEAR_NEW)).first())
        assertEquals("6", ids(SmartRule(Match.ALL, emptyList(), SmartSort.YEAR_OLD)).last())  // no year sorts last
        assertEquals(listOf("1", "4", "2", "3"), ids(SmartRule(Match.ALL, listOf(Cond(Field.PLAYS, Op.AT_LEAST, a = 1)), SmartSort.LAST_PLAYED)))
        assertEquals("6", ids(SmartRule(Match.ALL, emptyList(), SmartSort.SHORTEST)).first())
        // Random is stable for a seed (no reshuffle every time a play is counted) and keeps every song.
        val r1 = SmartEngine.evaluate(SmartRule(sort = SmartSort.RANDOM), lib, stats, now, seed = 3).map { it.id }
        val r2 = SmartEngine.evaluate(SmartRule(sort = SmartSort.RANDOM), lib, emptyMap(), now, seed = 3).map { it.id }
        assertEquals(r1, r2); assertEquals(lib.map { it.id }.sorted(), r1.sorted())
    }

    @Test fun updatesLiveWhenStatsChange() {
        val fav = AutoLists.FAVORITES.rule
        var s = stats
        assertEquals(listOf("3", "1"), SmartEngine.evaluate(fav, lib, s, now).map { it.id })
        s = Stats.setLiked(s, "7", true, now)
        assertEquals(listOf("7", "3", "1"), SmartEngine.evaluate(fav, lib, s, now).map { it.id })
        s = Stats.setLiked(s, "3", false, now)
        assertEquals(listOf("7", "1"), SmartEngine.evaluate(fav, lib, s, now).map { it.id })
        val most = AutoLists.MOST_PLAYED.rule
        repeat(13) { s = Stats.recordPlay(s, "7", now) }
        assertEquals("7", SmartEngine.evaluate(most, lib, s, now).first().id)
        assertFalse("7" in SmartEngine.evaluate(AutoLists.NEVER_PLAYED.rule, lib, s, now).map { it.id })
    }

    @Test fun autoGenresMergeSpellingsAndDecades() {
        val g = AutoLists.genres(lib)
        assertEquals("Bollywood", g.first().name)  // biggest first
        assertEquals(1, g.count { it.key == "genre:pop" })
        assertEquals("Pop", g.first { it.key == "genre:pop" }.name)  // ties pick a stable spelling
        assertEquals(listOf("4", "5"), SmartEngine.evaluate(g.first { it.key == "genre:pop" }.rule, lib, stats, now).map { it.id }.sorted())
        val d = AutoLists.decades(lib)
        assertEquals(listOf("2020s", "2010s", "2000s"), d.map { it.name })
        assertEquals(listOf("2", "7"), SmartEngine.evaluate(d.first { it.name == "2010s" }.rule, lib, stats, now).map { it.id }.sorted())
        assertEquals("90s", AutoLists.decadeName(1990)); assertEquals("00s", AutoLists.decadeName(1900))
        assertEquals("Pop", AutoLists.byKey("genre:pop", lib)?.name)
        assertEquals(null, AutoLists.byKey("genre:nope", lib))
    }

    @Test fun savedSmartPlaylistsCrudAndJson() {
        val rule = SmartRule(Match.ANY, listOf(Cond(Field.GENRE, Op.IS, "Pop"), Cond(Field.PLAYS, Op.BETWEEN, a = 2, b = 9), Cond(Field.LIKED, Op.NO),
            Cond(Field.DURATION, Op.AT_MOST, a = 240), Cond(Field.ADDED, Op.NOT_IN_LAST, a = 30)), SmartSort.RANDOM, 25)
        var (all, p) = SmartEngine.create(emptyList(), "Workout", rule, 5)
        val (all2, p2) = SmartEngine.create(all, "workout", SmartRule(), 6)
        assertEquals("workout 2", p2.name)
        all = SmartEngine.update(all2, p.id, "Gym", rule.copy(limit = 10))
        assertEquals("Gym", all.first { it.id == p.id }.name); assertEquals(10, all.first { it.id == p.id }.rule.limit)
        val back = SmartEngine.fromJson(SmartEngine.toJson(all))
        assertEquals(all, back)
        assertEquals(listOf(p2.id), SmartEngine.delete(all, p.id).map { it.id })
        assertEquals(emptyList<SmartPlaylist>(), SmartEngine.fromJson("not json"))
        // An op that doesn't fit its field is repaired on load instead of crashing.
        val bad = Cond.fromJson(org.json.JSONObject().put("f", "LIKED").put("o", "BETWEEN"))
        assertEquals(Op.YES, bad?.op)
    }

    @Test fun summariesReadNaturally() {
        assertEquals("Genre is Pop", Cond(Field.GENRE, Op.IS, "Pop").summary())
        assertEquals("Year 2010–2019", Cond(Field.YEAR, Op.BETWEEN, a = 2019, b = 2010).summary())
        assertEquals("Played 5+ times", Cond(Field.PLAYS, Op.AT_LEAST, a = 5).summary())
        assertEquals("Shorter than 4:00", Cond(Field.DURATION, Op.AT_MOST, a = 240).summary())
        assertEquals("Added in the last 30 days", Cond(Field.ADDED, Op.IN_LAST, a = 30).summary())
        assertEquals("Any of: Liked · Year 2020+", SmartRule(Match.ANY, listOf(Cond(Field.LIKED, Op.YES), Cond(Field.YEAR, Op.AT_LEAST, a = 2020))).summary())
        assertEquals("All songs", SmartRule().summary())
    }

    @Test fun statsJsonRoundTripDropsEmptyEntries() {
        var s = Stats.recordPlay(emptyMap(), "a", 100)
        s = Stats.recordPlay(s, "a", 200)
        s = Stats.setLiked(s, "b", true, 300)
        assertEquals(TrackStats(2, 200, false, 0), s["a"])
        assertEquals(s, Stats.fromJson(Stats.toJson(s)))
        s = Stats.setLiked(s, "b", false, 400)
        assertFalse("b" in s)
        assertEquals(emptyMap<String, TrackStats>(), Stats.fromJson("{oops"))
    }
}
