package bot.paradox.music.data

import org.json.JSONArray
import org.json.JSONObject

data class Playlist(val id: Long, val name: String, val trackIds: List<String>, val created: Long)

/** Local playlists: pure list operations, saved as JSON by the repository. */
object Playlists {
    fun create(all: List<Playlist>, name: String, now: Long): Pair<List<Playlist>, Playlist> {
        val id = (all.maxOfOrNull { it.id } ?: 0) + 1
        val p = Playlist(id, uniqueName(all, name.trim().ifEmpty { "Playlist" }), emptyList(), now)
        return (all + p) to p
    }
    fun uniqueName(all: List<Playlist>, name: String, except: Long? = null): String {
        val taken = all.filter { it.id != except }.map { it.name.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        var n = 2
        while ("$name $n".lowercase() in taken) n++
        return "$name $n"
    }
    fun rename(all: List<Playlist>, id: Long, name: String) = all.map { if (it.id == id) it.copy(name = uniqueName(all, name.trim().ifEmpty { it.name }, id)) else it }
    fun delete(all: List<Playlist>, id: Long) = all.filter { it.id != id }
    /** Adds songs at the end; a song already in the playlist is not added twice. */
    fun add(all: List<Playlist>, id: Long, trackIds: List<String>) = all.map { p ->
        if (p.id == id) p.copy(trackIds = p.trackIds + trackIds.filter { t -> t !in p.trackIds }.distinct()) else p
    }
    fun removeAt(all: List<Playlist>, id: Long, index: Int) = all.map { p ->
        if (p.id == id && index in p.trackIds.indices) p.copy(trackIds = p.trackIds.toMutableList().apply { removeAt(index) }) else p
    }
    fun move(all: List<Playlist>, id: Long, from: Int, to: Int) = all.map { p ->
        if (p.id == id && from in p.trackIds.indices && to in p.trackIds.indices) p.copy(trackIds = p.trackIds.toMutableList().apply { add(to, removeAt(from)) }) else p
    }

    fun toJson(all: List<Playlist>): String = JSONArray().apply {
        all.forEach { p -> put(JSONObject().put("id", p.id).put("name", p.name).put("c", p.created).put("t", JSONArray(p.trackIds))) }
    }.toString()
    fun fromJson(s: String): List<Playlist> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
            val t = o.getJSONArray("t"); Playlist(o.getLong("id"), o.getString("name"), (0 until t.length()).map { t.getString(it) }, o.optLong("c"))
        } }
    }.getOrElse { emptyList() }
}
