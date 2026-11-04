package bot.paradox.music.data

import kotlin.math.abs
import kotlin.random.Random

/** A mix made on the spot. It is not saved unless the user taps "Save as playlist". */
data class Mix(val name: String, val trackIds: List<String>, val seedTitle: String, val seedTrackId: String? = null)

/**
 * "Make a mix": the seed's artist first, then songs of the same genre and era, then the same genre,
 * then the same era to fill it up. Offline and pure; [seed] makes each mix fresh but testable.
 */
object Mixer {
    const val LIMIT = 40
    const val ERA_YEARS = 5
    private const val SAME_ARTIST_MAX = 12

    fun artistKey(name: String) = LibraryIndex.fold(name.trim())

    private fun sameArtist(t: Track, artist: String): Boolean {
        val k = artistKey(artist)
        return artistKey(t.artist) == k || t.albumArtist?.let { artistKey(it) } == k ||
            t.artist.split(",", "&", " feat. ", " ft. ").any { artistKey(it) == k }
    }

    private fun genreKeys(t: Track) = SmartEngine.genres(t).map { SmartEngine.genreKey(it) }.filter { it.isNotEmpty() }.toSet()

    private fun Random.weighted(ts: List<Track>, stats: Map<String, TrackStats>): List<Track> =
        // Shuffle, then let liked songs float up a little (stable sort keeps the shuffle inside each group).
        ts.sortedBy { it.id }.shuffled(this).sortedByDescending { if (stats[it.id]?.liked == true) 1 else 0 }

    fun fromSong(seed: Track, all: List<Track>, stats: Map<String, TrackStats> = emptyMap(), rnd: Long = 0, limit: Int = LIMIT): Mix {
        val r = Random(rnd)
        val rest = all.filter { it.id != seed.id }
        val artist = r.weighted(rest.filter { sameArtist(it, seed.artist) }, stats).take(SAME_ARTIST_MAX)
        val ids = buildTiers(listOf(seed) + artist, rest, genreKeys(seed), seed.year, r, stats, limit)
        return Mix("${seed.title} mix", ids, seed.title, seed.id)
    }

    fun fromArtist(artist: String, all: List<Track>, stats: Map<String, TrackStats> = emptyMap(), rnd: Long = 0, limit: Int = LIMIT): Mix {
        val r = Random(rnd)
        val mine = all.filter { sameArtist(it, artist) }
        // The artist's own genres (most common first) and era (median year).
        val genres = mine.flatMap { genreKeys(it) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(2).map { it.key }.toSet()
        val years = mine.mapNotNull { it.year }.sorted()
        val era = years.getOrNull(years.size / 2)
        val head = r.weighted(mine, stats).take(maxOf(SAME_ARTIST_MAX, limit / 2))
        return Mix("$artist mix", buildTiers(head, all, genres, era, r, stats, limit), artist)
    }

    private fun buildTiers(head: List<Track>, pool: List<Track>, genres: Set<String>, year: Int?, r: Random, stats: Map<String, TrackStats>, limit: Int): List<String> {
        val out = LinkedHashSet<String>()
        head.forEach { if (out.size < limit) out.add(it.id) }
        fun era(t: Track) = year != null && t.year != null && abs(t.year - year) <= ERA_YEARS
        fun genre(t: Track) = genres.isNotEmpty() && genreKeys(t).any { it in genres }
        val left = pool.filter { it.id !in out }
        val tiers = listOf(
            left.filter { genre(it) && era(it) },
            left.filter { genre(it) && !era(it) },
            left.filter { !genre(it) && era(it) },
        )
        for (tier in tiers) {
            for (t in r.weighted(tier, stats)) { if (out.size >= limit) break; out.add(t.id) }
        }
        return out.toList()
    }
}
