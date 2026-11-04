package bot.paradox.music.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

enum class SongSort(val label: String) { TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), ADDED("Recently added"), DURATION("Length") }
enum class AlbumSort(val label: String) { TITLE("Title"), ARTIST("Artist"), YEAR("Year"), ADDED("Recently added") }
enum class ArtistSort(val label: String) { NAME("Name"), SONGS("Most songs") }

/** The library, grouped and sortable. Built once per scan; pure Kotlin so it is unit-tested. */
class LibraryIndex(val tracks: List<Track>) {
    private val collator: Collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    private fun key(s: String) = s.removePrefix("The ").removePrefix("the ")

    val byId: Map<String, Track> = tracks.associateBy { it.id }

    val albums: List<Album> = tracks.groupBy { it.albumKey }.map { (k, ts) ->
        val sorted = ts.sortedWith(compareBy<Track>({ it.disc ?: 1 }, { it.track ?: Int.MAX_VALUE }).thenBy(collator) { it.title })
        val artists = ts.map { it.displayAlbumArtist }.distinct()
        Album(k, ts.first().album, if (artists.size == 1) artists[0] else "Various artists",
            ts.mapNotNull { it.year }.maxOrNull(), sorted, sorted.firstNotNullOfOrNull { it.art }, ts.maxOf { it.addedAt })
    }

    val albumByKey: Map<String, Album> = albums.associateBy { it.key }

    val artists: List<Artist> = tracks.groupBy { it.artist }.map { (name, ts) ->
        val al = ts.map { it.albumKey }.distinct().mapNotNull { albumByKey[it] }
        Artist(name, ts.sortedWith(compareBy(collator) { it.title }), al.sortedByDescending { it.year ?: 0 }, al.firstNotNullOfOrNull { it.art } ?: ts.firstNotNullOfOrNull { it.art })
    }

    fun songs(sort: SongSort): List<Track> = when (sort) {
        SongSort.TITLE -> tracks.sortedWith(compareBy(collator) { key(it.title) })
        SongSort.ARTIST -> tracks.sortedWith(compareBy<Track, String>(collator) { key(it.artist) }.thenBy(collator) { it.album }.thenBy { it.disc ?: 1 }.thenBy { it.track ?: 0 })
        SongSort.ALBUM -> tracks.sortedWith(compareBy<Track, String>(collator) { it.album }.thenBy { it.disc ?: 1 }.thenBy { it.track ?: 0 })
        SongSort.ADDED -> tracks.sortedWith(compareByDescending<Track> { it.addedAt }.thenBy(collator) { it.title })
        SongSort.DURATION -> tracks.sortedByDescending { it.durationMs }
    }

    fun albums(sort: AlbumSort): List<Album> = when (sort) {
        AlbumSort.TITLE -> albums.sortedWith(compareBy(collator) { key(it.title) })
        AlbumSort.ARTIST -> albums.sortedWith(compareBy<Album, String>(collator) { key(it.artist) }.thenBy { it.year ?: 0 })
        AlbumSort.YEAR -> albums.sortedWith(compareByDescending<Album> { it.year ?: 0 }.thenBy(collator) { it.title })
        AlbumSort.ADDED -> albums.sortedByDescending { it.addedAt }
    }

    fun artists(sort: ArtistSort): List<Artist> = when (sort) {
        ArtistSort.NAME -> artists.sortedWith(compareBy(collator) { key(it.name) })
        ArtistSort.SONGS -> artists.sortedByDescending { it.tracks.size }
    }

    data class SearchResult(val songs: List<Track>, val albums: List<Album>, val artists: List<Artist>) {
        val isEmpty get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
    }

    /** Case- and accent-insensitive; every word must match somewhere in title, artist or album. */
    fun search(q: String): SearchResult {
        val words = fold(q).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return SearchResult(emptyList(), emptyList(), emptyList())
        fun hit(vararg f: String): Boolean { val h = f.joinToString(" ") { fold(it) }; return words.all { h.contains(it) } }
        return SearchResult(
            songs(SongSort.TITLE).filter { hit(it.title, it.artist, it.album, it.fileName) },
            albums(AlbumSort.TITLE).filter { hit(it.title, it.artist) },
            artists(ArtistSort.NAME).filter { hit(it.name) },
        )
    }

    companion object {
        private val MARKS = Regex("\\p{Mn}+")
        fun fold(s: String): String = MARKS.replace(Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
    }
}

fun formatTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
