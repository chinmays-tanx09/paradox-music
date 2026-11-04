package bot.paradox.music.data

import org.junit.Assert.*
import org.junit.Test

class LibraryIndexTest {
    private fun t(id: String, title: String, artist: String, album: String, aa: String? = null, n: Int? = null, d: Int? = null, y: Int? = null, ms: Long = 1000, added: Long = 0, folder: String = "f", art: String? = null) =
        Track(id, title, artist, album, aa, null, y, n, d, ms, art, folder, "$title.mp3", 1, 1, added, "r")

    private val lib = LibraryIndex(listOf(
        t("1", "Kesariya", "Arijit Singh", "Brahmastra", "Pritam", 2, y = 2022, added = 5, art = "a.jpg"),
        t("2", "Deva Deva", "Arijit Singh", "Brahmastra", "Pritam", 1, y = 2022, added = 5),
        t("3", "Rasiya", "Shreya Ghoshal", "Brahmastra", "Pritam", 3, y = 2022, added = 5),
        t("4", "Halo", "Beyoncé", "I Am... Sasha Fierce", n = 1, y = 2008, ms = 260_000, added = 9),
        t("5", "The Scientist", "Coldplay", "A Rush of Blood", n = 4, y = 2002, added = 1),
        t("6", "Intro", "Unknown artist", "Album X", folder = "Album X"),
        t("7", "Intro", "Unknown artist", "Album X", folder = "Other/Album X"),
        t("8", "Disc two song", "Coldplay", "A Rush of Blood", n = 1, d = 2, y = 2002),
    ))

    @Test fun albumsGroupByAlbumArtistAndKeepCompilationsTogether() {
        val b = lib.albums.first { it.title == "Brahmastra" }
        assertEquals(3, b.tracks.size)
        assertEquals("Pritam", b.artist)
        assertEquals(listOf("Deva Deva", "Kesariya", "Rasiya"), b.tracks.map { it.title })   // by track number
        assertEquals("a.jpg", b.art)
    }

    @Test fun sameAlbumNameInTwoFoldersWithoutAlbumArtistIsTwoAlbums() {
        assertEquals(2, lib.albums.count { it.title == "Album X" })
    }

    @Test fun discThenTrackOrder() {
        assertEquals(listOf("The Scientist", "Disc two song"), lib.albums.first { it.title == "A Rush of Blood" }.tracks.map { it.title })
    }

    @Test fun artists() {
        val a = lib.artists.first { it.name == "Arijit Singh" }
        assertEquals(2, a.tracks.size); assertEquals(1, a.albums.size); assertEquals("a.jpg", a.art)
        assertEquals("Arijit Singh", lib.artists(ArtistSort.SONGS).first().name)
    }

    @Test fun songSorts() {
        assertEquals("Deva Deva", lib.songs(SongSort.TITLE).first().title)
        assertEquals("The Scientist", lib.songs(SongSort.TITLE).let { l -> l[l.indexOfFirst { it.title == "Rasiya" } + 1].title })  // "The" is ignored
        assertEquals("Halo", lib.songs(SongSort.ADDED).first().title)
        assertEquals("Halo", lib.songs(SongSort.DURATION).first().title)
        assertEquals("Arijit Singh", lib.songs(SongSort.ARTIST).first().artist)
    }

    @Test fun albumSorts() {
        assertEquals("Brahmastra", lib.albums(AlbumSort.YEAR).first().title)
        assertEquals(listOf("A Rush of Blood", "Album X", "Album X", "Brahmastra", "I Am... Sasha Fierce").sorted(), lib.albums(AlbumSort.TITLE).map { it.title }.sorted())
        assertEquals("Brahmastra", lib.albums(AlbumSort.TITLE)[3].title)
        assertEquals("I Am... Sasha Fierce", lib.albums(AlbumSort.ADDED).first().title)
    }

    @Test fun searchIgnoresCaseAndAccentsAndMatchesAllWords() {
        assertEquals(listOf("Halo"), lib.search("beyonce").songs.map { it.title })
        assertEquals(1, lib.search("BEYONCÉ").artists.size)
        assertEquals(listOf("Kesariya"), lib.search("arijit kes").songs.map { it.title })
        assertEquals(1, lib.search("brahm").albums.size)
        assertTrue(lib.search("zzz").isEmpty)
        assertTrue(lib.search("   ").isEmpty)
    }

    @Test fun timeFormat() {
        assertEquals("0:00", formatTime(0)); assertEquals("3:05", formatTime(185_000)); assertEquals("1:01:01", formatTime(3_661_000)); assertEquals("0:00", formatTime(-5))
    }
}
