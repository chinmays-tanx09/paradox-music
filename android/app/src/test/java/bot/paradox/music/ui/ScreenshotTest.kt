package bot.paradox.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bot.paradox.music.data.LibraryIndex
import bot.paradox.music.data.MusicFolder
import bot.paradox.music.data.Playlist
import bot.paradox.music.data.ThemeMode
import bot.paradox.music.data.Track
import bot.paradox.music.play.QEntry
import bot.paradox.music.play.Queue
import bot.paradox.music.play.Repeat
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/** Renders the real screens with a sample library so the look can be checked without a phone (≈ Redmi / Samsung 393×851 dp). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class ScreenshotTest {
    private val out = File(System.getProperty("shots.dir") ?: "build/shots")

    private val artDir: File by lazy {
        val d = Files.createTempDirectory("covers").toFile()
        val src = listOf(File("src/test/resources/covers"), File("app/src/test/resources/covers")).first { it.exists() }
        src.listFiles()!!.forEach { f -> f.copyTo(File(d, f.name)); ArtCache.load(File(d, f.name), 100); ArtCache.load(File(d, f.name), 1000) }
        d
    }

    private fun tr(i: Int, title: String, artist: String, album: String, cover: Int?, n: Int, ms: Long, aa: String? = null, y: Int = 2022, added: Long = 0) =
        Track("t$i", title, artist, album, aa, null, y, n, 1, ms, cover?.let { "cover$it.jpg" }, album, "$title.mp3", 1, 1, added, "r")

    private val tracks = listOf(
        tr(1, "Kesariya", "Arijit Singh", "Brahmastra", 0, 1, 268_000, "Pritam", added = 9),
        tr(2, "Deva Deva", "Arijit Singh", "Brahmastra", 0, 2, 280_000, "Pritam"),
        tr(3, "Rasiya", "Shreya Ghoshal", "Brahmastra", 0, 3, 254_000, "Pritam"),
        tr(4, "Apna Bana Le", "Arijit Singh", "Bhediya", 1, 1, 261_000, y = 2022),
        tr(5, "Blinding Lights", "The Weeknd", "After Hours", 2, 9, 200_000, y = 2020),
        tr(6, "Save Your Tears", "The Weeknd", "After Hours", 2, 11, 215_000, y = 2020),
        tr(7, "Yellow", "Coldplay", "Parachutes", 3, 5, 266_000, y = 2000),
        tr(8, "The Scientist", "Coldplay", "A Rush of Blood to the Head", 4, 4, 309_000, y = 2002),
        tr(9, "Bombe Helutaite", "Rajkumar Bharathi", "Rajakumara", 5, 2, 255_000, y = 2017),
        tr(10, "Belageddu", "Vijay Prakash", "Kirik Party", 6, 1, 238_000, y = 2016),
        tr(11, "Tum Hi Ho", "Arijit Singh", "Aashiqui 2", null, 7, 262_000, y = 2013),
        tr(12, "Levitating", "Dua Lipa", "Future Nostalgia", 7, 5, 203_000, y = 2020),
        tr(13, "Don't Start Now", "Dua Lipa", "Future Nostalgia", 7, 2, 183_000, y = 2020),
        tr(14, "Chaleya", "Arijit Singh, Shilpa Rao", "Jawan", 1, 3, 200_000, y = 2023),
    )
    private val index = LibraryIndex(tracks)
    private val playlists = listOf(
        Playlist(1, "Late night drive", listOf("t5", "t12", "t1", "t8"), 0),
        Playlist(2, "Kannada classics", listOf("t9", "t10"), 0),
        Playlist(3, "Gym", emptyList(), 0),
    )
    private val queue = Queue(tracks.take(8).mapIndexed { i, t -> QEntry(i + 1L, t.id) }, 0, false, emptyList())

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        out.mkdirs()
        val dir = artDir
        captureRoboImage(File(out, "$name.png").path) {
            MusicTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalArtDir provides dir, LocalAnimate provides false) { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
    }

    @Composable private fun WithMini(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            content()
            MiniPlayer(tracks[0], true, 0.37f, PlayerControls(), {}, Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 22.dp))
        }
    }

    @Composable private fun Library(tab: Int, search: String? = null) = WithMini {
        LibraryScreen(index, playlists, "t1", tab, {}, Sorts(), {}, Actions(), {}, {}, {}, {}, scanning = null, bottomPad = 110.dp,
            startSearch = search != null, initialQuery = search ?: "")
    }

    @Test fun setupEmptyLight() = shot("01_setup_light", false) { SetupScreen(null, {}, {}) }
    @Test fun setupEmptyDark() = shot("01_setup_dark", true) { SetupScreen(null, {}, {}) }
    @Test fun setupChosenLight() = shot("02_setup_folder_chosen_light", false) { SetupScreen("Music/Songs", {}, {}) }
    @Test fun setupChosenDark() = shot("02_setup_folder_chosen_dark", true) { SetupScreen("Music/Songs", {}, {}) }
    @Test fun setupScanningDark() = shot("03_setup_scanning_dark", true) { SetupScreen("Music/Songs", {}, {}, scanning = true, scanText = "Scanning… 248 songs found") }

    @Test fun songsLight() = shot("04_library_songs_light", false) { Library(0) }
    @Test fun songsDark() = shot("04_library_songs_dark", true) { Library(0) }
    @Test fun albumsLight() = shot("05_albums_light", false) { Library(1) }
    @Test fun albumsDark() = shot("05_albums_dark", true) { Library(1) }
    @Test fun artistsLight() = shot("06_artists_light", false) { Library(2) }
    @Test fun playlistsDark() = shot("07_playlists_dark", true) { Library(3) }
    @Test fun searchLight() = shot("08_search_light", false) { Library(0, "arijit") }

    @Test fun albumLight() = shot("09_album_light", false) { WithMini { AlbumScreen(index.albums.first { it.title == "Brahmastra" }, "t1", Actions(), {}, 110.dp) } }
    @Test fun albumDark() = shot("09_album_dark", true) { WithMini { AlbumScreen(index.albums.first { it.title == "Brahmastra" }, "t1", Actions(), {}, 110.dp) } }
    @Test fun artistDark() = shot("10_artist_dark", true) { WithMini { ArtistScreen(index.artists.first { it.name == "Arijit Singh" }, "t1", Actions(), {}, 110.dp) } }

    @Composable private fun Now() = NowPlayingScreen(tracks[0], true, 98_000, 268_000, shuffled = true, repeat = Repeat.ALL, upNext = tracks[1], from = "Brahmastra",
        c = PlayerControls(), onClose = {}, onQueue = {})
    @Test fun nowPlayingLight() = shot("11_now_playing_light", false) { Now() }
    @Test fun nowPlayingDark() = shot("11_now_playing_dark", true) { Now() }
    @Test fun nowPlayingNoArtRepeatOne() = shot("12_now_playing_no_cover_repeat_one_light", false) {
        NowPlayingScreen(tracks[10], false, 12_000, 262_000, false, Repeat.ONE, null, "Library", PlayerControls(), {}, {})
    }
    @Test fun queueDark() = shot("13_queue_dark", true) { QueueScreen(queue.entries.map { e -> tracks.first { it.id == e.trackId } }, 0, PlayerControls()) {} }

    @Composable private fun Settings(dark: Boolean) = SettingsScreen(
        listOf(MusicFolder("u1", "Music/Songs"), MusicFolder("u2", "SD card/Bollywood")), setOf("u2"), "248 songs · 32 albums · 2 folders", false,
        if (dark) ThemeMode.DARK else ThemeMode.SYSTEM, "1.0", {}, {}, {}, {}, {}, {}, {}, {}, "Version 1.0", {}, bottomPad = 24.dp)
    @Test fun settingsLight() = shot("14_settings_light", false) { Settings(false) }
    @Test fun settingsDark() = shot("14_settings_dark", true) { Settings(true) }
    @Test fun channelLight() = shot("15_channel_popup_light", false) { Library(0); ChannelCard({}, {}, {}) }
    @Test fun updateBannerDark() = shot("16_update_banner_dark", true) {
        LibraryScreen(index, playlists, null, 0, {}, Sorts(), {}, Actions(), {}, {}, {}, {}, scanning = "Scanning… 120 songs found",
            banner = { UpdateCard("1.1", {}, {}, {}) })
    }
}
