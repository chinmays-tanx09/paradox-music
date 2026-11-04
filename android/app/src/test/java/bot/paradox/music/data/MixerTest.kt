package bot.paradox.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixerTest {
    private fun t(id: String, artist: String, genre: String?, year: Int?) =
        Track(id, "Song $id", artist, "Album $id", null, genre, year, 1, 1, 200_000, null, "", "$id.mp3", 1, 1, 0, "r")

    private val lib = listOf(
        t("s", "Arijit Singh", "Bollywood", 2018),
        t("a1", "Arijit Singh", "Bollywood", 2013),
        t("a2", "Arijit Singh", "Romantic", 2022),
        t("a3", "Arijit Singh, Shilpa Rao", "Bollywood", 2023),
        t("g1", "Shreya Ghoshal", "Bollywood", 2016),      // same genre, same era
        t("g2", "Pritam", "bollywood", 2020),               // same genre, same era (spelling differs)
        t("g3", "Kishore Kumar", "Bollywood", 1975),       // same genre, other era
        t("e1", "Dua Lipa", "Pop", 2020),                   // same era, other genre
        t("x1", "Lata Mangeshkar", "Classical", 1965),     // nothing in common
        t("n1", "Unknown artist", null, null),
    )

    @Test fun songMixGoesArtistThenGenreAndEraThenGenreThenEra() {
        val m = Mixer.fromSong(lib[0], lib, rnd = 1)
        assertEquals("s", m.trackIds.first())
        assertEquals("Song s mix", m.name)
        val pos = m.trackIds.withIndex().associate { it.value to it.index }
        val artist = listOf("a1", "a2", "a3").map { pos.getValue(it) }
        val genreEra = listOf("g1", "g2").map { pos.getValue(it) }
        assertTrue(artist.max() < genreEra.min())
        assertTrue(genreEra.max() < pos.getValue("g3"))
        assertTrue(pos.getValue("g3") < pos.getValue("e1"))
        assertTrue("x1" !in m.trackIds && "n1" !in m.trackIds)
        assertEquals(m.trackIds.size, m.trackIds.toSet().size)  // no duplicates
    }

    @Test fun artistMixStartsWithTheArtistAndUsesTheirGenreAndEra() {
        val m = Mixer.fromArtist("arijit singh", lib, rnd = 2)
        assertEquals("arijit singh mix", m.name)
        assertEquals(setOf("s", "a1", "a2", "a3"), m.trackIds.take(4).toSet())
        assertTrue(m.trackIds.indexOf("g2") < m.trackIds.indexOf("g3"))  // g2 is in the artist's era (median 2022), g3 is not
        assertTrue("x1" !in m.trackIds)
    }

    @Test fun limitSeedAndLikedFirst() {
        val big = (1..100).map { t("p$it", "Artist ${it % 7}", "Pop", 2015 + it % 10) }
        val seed = big[0]
        val m = Mixer.fromSong(seed, big, rnd = 9)
        assertEquals(Mixer.LIMIT, m.trackIds.size)
        assertEquals(m, Mixer.fromSong(seed, big, rnd = 9))          // same seed, same mix
        assertTrue(m != Mixer.fromSong(seed, big, rnd = 10))          // a new seed gives a fresh mix
        // Liked songs float up inside their tier.
        val sameArtist = big.filter { it.artist == seed.artist && it.id != seed.id }
        val liked = sameArtist.last().id
        val m2 = Mixer.fromSong(seed, big, mapOf(liked to TrackStats(liked = true)), rnd = 9)
        assertEquals(liked, m2.trackIds[1])
    }

    @Test fun untaggedSongStillMakesAMix() {
        val m = Mixer.fromSong(lib.last(), lib, rnd = 3)
        assertEquals(listOf("n1"), m.trackIds)  // nothing related: just the song, the app says so instead of padding with noise
    }
}
