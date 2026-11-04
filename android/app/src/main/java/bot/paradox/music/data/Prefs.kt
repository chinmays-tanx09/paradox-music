package bot.paradox.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** A folder the user picked through the system folder picker (a persisted tree URI). */
data class MusicFolder(val uri: String, val name: String)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("paradox_music", Context.MODE_PRIVATE)

    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) = sp.edit().putBoolean("setup_done", v).apply()

    var folders: List<MusicFolder>
        get() = runCatching {
            val a = JSONArray(sp.getString("folders", "[]"))
            (0 until a.length()).map { a.getJSONObject(it).let { o -> MusicFolder(o.getString("u"), o.optString("n")) } }
        }.getOrElse { emptyList() }
        set(v) = sp.edit().putString("folders", JSONArray().apply { v.forEach { put(JSONObject().put("u", it.uri).put("n", it.name)) } }.toString()).apply()

    var theme: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
        set(v) = sp.edit().putString("theme", v.name).apply()

    var songSort: SongSort
        get() = runCatching { SongSort.valueOf(sp.getString("song_sort", "TITLE")!!) }.getOrDefault(SongSort.TITLE)
        set(v) = sp.edit().putString("song_sort", v.name).apply()
    var albumSort: AlbumSort
        get() = runCatching { AlbumSort.valueOf(sp.getString("album_sort", "TITLE")!!) }.getOrDefault(AlbumSort.TITLE)
        set(v) = sp.edit().putString("album_sort", v.name).apply()
    var artistSort: ArtistSort
        get() = runCatching { ArtistSort.valueOf(sp.getString("artist_sort", "NAME")!!) }.getOrDefault(ArtistSort.NAME)
        set(v) = sp.edit().putString("artist_sort", v.name).apply()
    var tab: Int
        get() = sp.getInt("tab", 0)
        set(v) = sp.edit().putInt("tab", v).apply()

    var lastScan: Long
        get() = sp.getLong("last_scan", 0)
        set(v) = sp.edit().putLong("last_scan", v).apply()

    /** The Paradox intro plays on first install and once after each update. */
    var introVc: Int
        get() = sp.getInt("paradox_intro_vc", 0)
        set(v) = sp.edit().putInt("paradox_intro_vc", v).apply()

    var channelNever: Boolean
        get() = sp.getBoolean("channel_never", false)
        set(v) = sp.edit().putBoolean("channel_never", v).apply()
    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0)
        set(v) = sp.edit().putLong("last_update_check", v).apply()
    var skippedVersion: Int
        get() = sp.getInt("skipped_version", 0)
        set(v) = sp.edit().putInt("skipped_version", v).apply()
    var cachedRelease: String?
        get() = sp.getString("cached_release", null)
        set(v) = sp.edit().putString("cached_release", v).apply()
}
