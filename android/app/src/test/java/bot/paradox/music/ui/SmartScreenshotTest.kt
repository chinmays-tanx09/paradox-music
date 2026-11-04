package bot.paradox.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bot.paradox.music.data.AutoKind
import bot.paradox.music.data.AutoLists
import bot.paradox.music.data.Cond
import bot.paradox.music.data.Field
import bot.paradox.music.data.LibraryIndex
import bot.paradox.music.data.Match
import bot.paradox.music.data.Mixer
import bot.paradox.music.data.Op
import bot.paradox.music.data.Playlist
import bot.paradox.music.data.SmartEngine
import bot.paradox.music.data.SmartPlaylist
import bot.paradox.music.data.SmartRule
import bot.paradox.music.data.SmartSort
import bot.paradox.music.data.ThemeMode
import bot.paradox.music.data.Track
import bot.paradox.music.data.TrackStats
import bot.paradox.music.play.Repeat
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/** v1.1 smart playlists: the Smart section, the rule builder, smart / mix pages and the heart, light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class SmartScreenshotTest {
    private val out = File(System.getProperty("shots.dir") ?: "build/shots")
    private val day = 86_400_000L
    private val now = 20_000 * day

    private val artDir: File by lazy {
        val d = Files.createTempDirectory("covers").toFile()
        val src = listOf(File("src/test/resources/covers"), File("app/src/test/resources/covers")).first { it.exists() }
        src.listFiles()!!.forEach { f -> f.copyTo(File(d, f.name)); ArtCache.load(File(d, f.name), 100); ArtCache.load(File(d, f.name), 1000) }
        d
    }

    private fun tr(i: Int, title: String, artist: String, album: String, cover: Int?, genre: String?, y: Int?, ms: Long, addedDaysAgo: Int = 200) =
        Track("t$i", title, artist, album, null, genre, y, i, 1, ms, cover?.let { "cover$it.jpg" }, album, "$title.mp3", 1, 1, now - addedDaysAgo * day, "r")

    private val tracks = listOf(
        tr(1, "Kesariya", "Arijit Singh", "Brahmastra", 0, "Bollywood", 2022, 268_000, 3),
        tr(2, "Deva Deva", "Arijit Singh", "Brahmastra", 0, "Bollywood", 2022, 280_000, 3),
        tr(3, "Rasiya", "Shreya Ghoshal", "Brahmastra", 0, "Bollywood", 2022, 254_000, 3),
        tr(4, "Apna Bana Le", "Arijit Singh", "Bhediya", 1, "Bollywood", 2022, 261_000),
        tr(5, "Blinding Lights", "The Weeknd", "After Hours", 2, "Pop", 2020, 200_000),
        tr(6, "Save Your Tears", "The Weeknd", "After Hours", 2, "Pop", 2020, 215_000),
        tr(7, "Yellow", "Coldplay", "Parachutes", 3, "Alternative", 2000, 266_000),
        tr(8, "The Scientist", "Coldplay", "A Rush of Blood to the Head", 4, "Alternative", 2002, 309_000),
        tr(9, "Bombe Helutaite", "Rajkumar Bharathi", "Rajakumara", 5, "Kannada", 2017, 255_000),
        tr(10, "Belageddu", "Vijay Prakash", "Kirik Party", 6, "Kannada", 2016, 238_000),
        tr(11, "Tum Hi Ho", "Arijit Singh", "Aashiqui 2", null, "Bollywood", 2013, 262_000),
        tr(12, "Levitating", "Dua Lipa", "Future Nostalgia", 7, "Pop", 2020, 203_000, 12),
        tr(13, "Don't Start Now", "Dua Lipa", "Future Nostalgia", 7, "Pop", 2020, 183_000, 12),
        tr(14, "Chaleya", "Arijit Singh, Shilpa Rao", "Jawan", 1, "Bollywood", 2023, 200_000, 1),
        tr(15, "Clocks", "Coldplay", "A Rush of Blood to the Head", 4, "Alternative", 2002, 307_000),
        tr(16, "Hosanna", "A.R. Rahman", "Ekk Deewana Tha", null, "Bollywood", 2012, 330_000),
        tr(17, "Fix You", "Coldplay", "X&Y", null, "Alternative", 2005, 295_000),
        tr(18, "Kun Faya Kun", "A.R. Rahman", "Rockstar", null, "Sufi", 2011, 473_000),
    )
    private val index = LibraryIndex(tracks)
    private val stats = mapOf(
        "t1" to TrackStats(23, now - 2 * 3_600_000L, true, now - 2 * day),
        "t14" to TrackStats(17, now - 26 * 3_600_000L, true, now - day),
        "t5" to TrackStats(12, now - 3 * day),
        "t12" to TrackStats(9, now - 40 * 60_000L, true, now - 5 * day),
        "t11" to TrackStats(7, now - 6 * day),
        "t7" to TrackStats(5, now - 9 * day, true, now - 20 * day),
        "t9" to TrackStats(3, now - 2 * day),
        "t3" to TrackStats(1, now - 12 * day),
    )
    private val playlists = listOf(Playlist(1, "Late night drive", listOf("t5", "t12", "t1", "t8"), 0), Playlist(2, "Kannada classics", listOf("t9", "t10"), 0))
    private val bollyRule = SmartRule(Match.ALL, listOf(Cond(Field.GENRE, Op.IS, "Bollywood"), Cond(Field.YEAR, Op.AT_LEAST, a = 2015), Cond(Field.PLAYS, Op.AT_LEAST, a = 5)), SmartSort.MOST_PLAYED, 50)
    private val smart = listOf(
        SmartPlaylist(1, "Bollywood heavy rotation", bollyRule, 0),
        SmartPlaylist(2, "Short & sweet", SmartRule(Match.ALL, listOf(Cond(Field.DURATION, Op.AT_MOST, a = 215), Cond(Field.LIKED, Op.NO)), SmartSort.RANDOM, 25), 0),
    )
    private val likes = stats.filterValues { it.liked }.keys

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        out.mkdirs()
        val dir = artDir
        captureRoboImage(File(out, "$name.png").path) {
            MusicTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalArtDir provides dir, LocalAnimate provides false, LocalLikes provides likes) { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
    }

    @Composable private fun WithMini(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            content()
            MiniPlayer(tracks[0], true, 0.37f, PlayerControls(), {}, Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 22.dp))
        }
    }

    @Composable private fun SmartTab() = WithMini {
        LibraryScreen(index, playlists, "t1", 3, {}, Sorts(), {}, Actions(), {}, {}, {}, {}, scanning = null, bottomPad = 110.dp,
            smart = smart, stats = stats, now = now)
    }

    @Test fun smartSectionLight() = shot("20_smart_section_light", false) { SmartTab() }
    @Test fun smartSectionDark() = shot("20_smart_section_dark", true) { SmartTab() }
    @Config(qualifiers = "w393dp-h1500dp-xxhdpi")
    @Test fun smartSectionFullDark() = shot("21_smart_section_full_dark", true) { SmartTab() }
    @Config(qualifiers = "w393dp-h1500dp-xxhdpi")
    @Test fun smartSectionFullLight() = shot("21_smart_section_full_light", false) { SmartTab() }

    @Composable private fun Editor(sp: SmartPlaylist?) = SmartEditor(sp, index, stats, now, { _, _ -> }, if (sp != null) ({}) else null, {})
    @Test fun builderEditLight() = shot("22_rule_builder_light", false) { Editor(smart[0]) }
    @Test fun builderEditDark() = shot("22_rule_builder_dark", true) { Editor(smart[0]) }
    @Test fun builderNewLight() = shot("23_rule_builder_new_light", false) { Editor(null) }
    @Config(qualifiers = "w393dp-h1500dp-xxhdpi")
    @Test fun builderFullDark() = shot("23_rule_builder_full_dark", true) {
        SmartEditor(SmartPlaylist(3, "Mixed rules", SmartRule(Match.ANY, listOf(Cond(Field.ARTIST, Op.IS, "Coldplay"), Cond(Field.DURATION, Op.BETWEEN, a = 180, b = 270),
            Cond(Field.ADDED, Op.IN_LAST, a = 30), Cond(Field.LIKED, Op.YES)), SmartSort.RANDOM, 25), 0), index, stats, now, { _, _ -> }, {}, {})
    }

    @Composable private fun Saved() = WithMini {
        val sp = smart[0]
        SmartScreen(sp.name, null, sp.rule, SmartEngine.evaluate(sp.rule, tracks, stats, now), stats, now, "t1", Actions(), SmartActions(), {}, sp, 110.dp)
    }
    @Test fun smartPageLight() = shot("24_smart_playlist_light", false) { Saved() }
    @Test fun smartPageDark() = shot("24_smart_playlist_dark", true) { Saved() }

    @Composable private fun Auto(key: String) = WithMini {
        val a = AutoLists.byKey(key, tracks)!!
        SmartScreen(a.name, a.kind, a.rule, SmartEngine.evaluate(a.rule, tracks, stats, now, seed = 7), stats, now, "t1", Actions(), SmartActions(), {}, null, 110.dp)
    }
    @Test fun mostPlayedLight() = shot("25_most_played_light", false) { Auto("auto:most") }
    @Test fun recentlyPlayedDark() = shot("25_recently_played_dark", true) { Auto("auto:recent") }
    @Test fun genreDark() = shot("26_genre_bollywood_dark", true) { Auto("genre:bollywood") }
    @Test fun decadeLight() = shot("26_decade_2000s_light", false) { Auto("decade:2000") }
    @Test fun emptyFavoritesLight() = shot("27_favorites_empty_light", false) {
        val a = AutoLists.FAVORITES
        SmartScreen(a.name, AutoKind.FAVORITES, a.rule, emptyList(), emptyMap(), now, null, Actions(), SmartActions(), {}, null, 24.dp)
    }

    @Composable private fun MixPage() = WithMini {
        val m = Mixer.fromSong(tracks[0], tracks, stats, rnd = 4)
        MixScreen(m.name, m.seedTitle, m.trackIds.map { id -> tracks.first { it.id == id } }, "t1", Actions(), {}, {}, {}, 110.dp)
    }
    @Test fun mixLight() = shot("28_mix_light", false) { MixPage() }
    @Test fun mixDark() = shot("28_mix_dark", true) { MixPage() }

    @Test fun nowPlayingLikedDark() = shot("29_now_playing_liked_dark", true) {
        NowPlayingScreen(tracks[0], true, 98_000, 268_000, false, Repeat.OFF, tracks[1], "Favorites", PlayerControls(), {}, {}, liked = true, onLike = {})
    }
    @Test fun nowPlayingNotLikedLight() = shot("29_now_playing_like_light", false) {
        NowPlayingScreen(tracks[4], true, 61_000, 200_000, true, Repeat.ALL, tracks[5], "Pop", PlayerControls(), {}, {}, liked = false, onLike = {})
    }
    @Test fun songsWithHeartsLight() = shot("30_songs_hearts_light", false) {
        WithMini { LibraryScreen(index, playlists, "t1", 0, {}, Sorts(), {}, Actions(), {}, {}, {}, {}, scanning = null, bottomPad = 110.dp) }
    }
}
