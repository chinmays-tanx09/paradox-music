package bot.paradox.music.data

import org.json.JSONObject

/** What the app has learned about one song on this phone. Never leaves the device. */
data class TrackStats(val plays: Int = 0, val lastPlayed: Long = 0, val liked: Boolean = false, val likedAt: Long = 0) {
    val isEmpty get() = plays == 0 && lastPlayed == 0L && !liked
}

/** Play counts, last-played times and likes, keyed by track id. Pure functions; the repository saves them as JSON. */
object Stats {
    val NONE = TrackStats()

    fun recordPlay(all: Map<String, TrackStats>, id: String, now: Long): Map<String, TrackStats> {
        val s = all[id] ?: NONE
        return all + (id to s.copy(plays = s.plays + 1, lastPlayed = now))
    }

    fun setLiked(all: Map<String, TrackStats>, id: String, liked: Boolean, now: Long): Map<String, TrackStats> {
        val s = all[id] ?: NONE
        if (s.liked == liked) return all
        val n = s.copy(liked = liked, likedAt = if (liked) now else 0)
        return if (n.isEmpty) all - id else all + (id to n)
    }

    fun toJson(all: Map<String, TrackStats>): String = JSONObject().apply {
        all.forEach { (id, s) ->
            if (!s.isEmpty) put(id, JSONObject().apply {
                if (s.plays > 0) put("p", s.plays)
                if (s.lastPlayed > 0) put("l", s.lastPlayed)
                if (s.liked) { put("f", 1); put("fa", s.likedAt) }
            })
        }
    }.toString()

    fun fromJson(s: String): Map<String, TrackStats> = runCatching {
        val o = JSONObject(s)
        o.keys().asSequence().associateWith { k ->
            val e = o.getJSONObject(k)
            TrackStats(e.optInt("p"), e.optLong("l"), e.optInt("f") == 1, e.optLong("fa"))
        }
    }.getOrElse { emptyMap() }
}
