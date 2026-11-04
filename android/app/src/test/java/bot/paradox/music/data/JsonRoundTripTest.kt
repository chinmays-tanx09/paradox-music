package bot.paradox.music.data

import bot.paradox.music.update.Updates
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json is Android's, so these run on Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JsonRoundTripTest {
    @Test fun tracks() {
        val a = Track("content://x/1", "Tïtle \"q\"", "Artist", "Album", "AA", "Pop", 2020, 3, 1, 123_456, "abc.jpg", "Sub/Dir", "f.mp3", 99, 77, 55, "content://tree")
        val b = a.copy(id = "2", albumArtist = null, genre = null, year = null, track = null, disc = null, art = null)
        assertEquals(listOf(a, b), Track.listFromJson(Track.listToJson(listOf(a, b))))
        assertEquals(emptyList<Track>(), Track.listFromJson("not json"))
    }

    @Test fun playlists() {
        var all = emptyList<Playlist>()
        val (a1, p1) = Playlists.create(all, "Road trip", 1); all = a1
        val (a2, p2) = Playlists.create(all, "road trip", 2); all = a2
        assertEquals("road trip 2", p2.name)
        all = Playlists.add(all, p1.id, listOf("s1", "s2", "s1"))
        all = Playlists.add(all, p1.id, listOf("s2", "s3"))
        assertEquals(listOf("s1", "s2", "s3"), all.first { it.id == p1.id }.trackIds)
        all = Playlists.move(all, p1.id, 0, 2)
        assertEquals(listOf("s2", "s3", "s1"), all.first { it.id == p1.id }.trackIds)
        all = Playlists.removeAt(all, p1.id, 1)
        assertEquals(listOf("s2", "s1"), all.first { it.id == p1.id }.trackIds)
        all = Playlists.rename(all, p2.id, "Gym")
        assertEquals("Gym", all.first { it.id == p2.id }.name)
        assertEquals("Road trip 2", Playlists.rename(all, p2.id, "Road trip").first { it.id == p2.id }.name)
        assertEquals(all, Playlists.fromJson(Playlists.toJson(all)))
        assertEquals(1, Playlists.delete(all, p1.id).size)
        assertTrue(Playlists.fromJson("{oops").isEmpty())
    }

    @Test fun updateCheckPicksTheNewestAndroidReleaseAndIgnoresWindowsOnes() {
        val json = """[
          {"tag_name":"desktop-v1.0.0","name":"Windows 1.0","draft":false,"body":"Windows build","html_url":"https://g/w","assets":[{"name":"Paradox.Music_1.0.0_x64-setup.exe","browser_download_url":"https://g/w.exe"}]},
          {"tag_name":"android-v1.1","name":"Paradox Music 1.1","draft":false,"body":"versionCode: 2\nFixes","html_url":"https://g/a11","assets":[{"name":"ParadoxMusic-1.1.apk","browser_download_url":"https://g/a11.apk"}]},
          {"tag_name":"android-v1.2","name":"","draft":true,"body":"versionCode: 3","html_url":"https://g/a12","assets":[{"name":"x.apk","browser_download_url":"https://g/a12.apk"}]}
        ]"""
        val u = Updates.parse(json)!!
        assertEquals(2, u.versionCode); assertEquals("Paradox Music 1.1", u.versionName); assertEquals("https://g/a11", u.pageUrl)
        assertTrue(Updates.shouldShow(u, 1, 0)); assertFalse(Updates.shouldShow(u, 2, 0)); assertFalse(Updates.shouldShow(u, 1, 2))
        assertNull(Updates.parse("[]")); assertNull(Updates.parse("garbage"))
        assertTrue(Updates.shouldCheck(Updates.INTERVAL_MS + 10, 5)); assertFalse(Updates.shouldCheck(1000, 500))
    }
}
