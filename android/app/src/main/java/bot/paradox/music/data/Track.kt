package bot.paradox.music.data

import org.json.JSONArray
import org.json.JSONObject

/** One song in the library. [id] is the file's document URI, stable across rescans. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val genre: String?,
    val year: Int?,
    val track: Int?,
    val disc: Int?,
    val durationMs: Long,
    /** File name of the cover in the art store, or null. */
    val art: String?,
    /** Folder path relative to the chosen music folder, "" for the top. */
    val folder: String,
    val fileName: String,
    val size: Long,
    val modified: Long,
    val addedAt: Long,
    /** Which chosen root folder it came from. */
    val root: String,
) {
    val displayAlbumArtist: String get() = albumArtist ?: artist
    /** Albums group by title + album artist; without an album artist, by title + folder (keeps compilations together). */
    val albumKey: String get() = album.lowercase() + "\u0001" + (albumArtist?.lowercase() ?: ("dir:" + root + "/" + folder))

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("t", title); put("a", artist); put("al", album)
        albumArtist?.let { put("aa", it) }; genre?.let { put("g", it) }; year?.let { put("y", it) }
        track?.let { put("n", it) }; disc?.let { put("d", it) }
        put("ms", durationMs); art?.let { put("art", it) }
        put("f", folder); put("fn", fileName); put("sz", size); put("m", modified); put("add", addedAt); put("r", root)
    }

    companion object {
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"

        fun fromJson(o: JSONObject) = Track(
            id = o.getString("id"), title = o.getString("t"), artist = o.getString("a"), album = o.getString("al"),
            albumArtist = o.optStringOrNull("aa"), genre = o.optStringOrNull("g"),
            year = if (o.has("y")) o.getInt("y") else null, track = if (o.has("n")) o.getInt("n") else null,
            disc = if (o.has("d")) o.getInt("d") else null, durationMs = o.optLong("ms"), art = o.optStringOrNull("art"),
            folder = o.optString("f"), fileName = o.optString("fn"), size = o.optLong("sz"), modified = o.optLong("m"),
            addedAt = o.optLong("add"), root = o.optString("r"),
        )

        fun listToJson(l: List<Track>): String = JSONArray().apply { l.forEach { put(it.toJson()) } }.toString()
        fun listFromJson(s: String): List<Track> = runCatching {
            val a = JSONArray(s); (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        }.getOrElse { emptyList() }
    }
}

internal fun JSONObject.optStringOrNull(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

data class Album(val key: String, val title: String, val artist: String, val year: Int?, val tracks: List<Track>, val art: String?, val addedAt: Long)
data class Artist(val name: String, val tracks: List<Track>, val albums: List<Album>, val art: String?)
