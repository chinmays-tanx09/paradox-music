package bot.paradox.music.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale
import kotlin.random.Random

/** What a rule looks at. */
enum class Field(val label: String) {
    GENRE("Genre"), ARTIST("Artist"), ALBUM("Album"), YEAR("Year"), ADDED("Added"),
    PLAYS("Play count"), LAST_PLAYED("Last played"), LIKED("Liked"), DURATION("Length");

    val kind: Kind get() = when (this) {
        GENRE, ARTIST, ALBUM -> Kind.TEXT
        YEAR, PLAYS, DURATION -> Kind.NUMBER
        ADDED, LAST_PLAYED -> Kind.DATE
        LIKED -> Kind.BOOL
    }

    /** The comparisons this field offers, in menu order. */
    val ops: List<Op> get() = when (kind) {
        Kind.TEXT -> listOf(Op.IS, Op.IS_NOT, Op.CONTAINS, Op.NOT_CONTAINS)
        Kind.NUMBER -> if (this == YEAR) listOf(Op.BETWEEN, Op.AT_LEAST, Op.AT_MOST) else listOf(Op.AT_LEAST, Op.AT_MOST, Op.BETWEEN)
        Kind.DATE -> if (this == LAST_PLAYED) listOf(Op.IN_LAST, Op.NOT_IN_LAST, Op.NEVER) else listOf(Op.IN_LAST, Op.NOT_IN_LAST)
        Kind.BOOL -> listOf(Op.YES, Op.NO)
    }

    enum class Kind { TEXT, NUMBER, DATE, BOOL }
}

enum class Op { IS, IS_NOT, CONTAINS, NOT_CONTAINS, AT_LEAST, AT_MOST, BETWEEN, IN_LAST, NOT_IN_LAST, NEVER, YES, NO;

    /** Words for this comparison on [f], as the rule builder shows them. */
    fun label(f: Field): String = when (this) {
        IS -> "is"; IS_NOT -> "is not"; CONTAINS -> "contains"; NOT_CONTAINS -> "doesn't contain"
        AT_LEAST -> when (f) { Field.YEAR -> "from"; Field.DURATION -> "longer than"; else -> "at least" }
        AT_MOST -> when (f) { Field.YEAR -> "up to"; Field.DURATION -> "shorter than"; else -> "at most" }
        BETWEEN -> "between"
        IN_LAST -> "in the last"; NOT_IN_LAST -> "not in the last"; NEVER -> "never"
        YES -> "yes"; NO -> "no"
    }
}

/**
 * One rule. [text] is for genre / artist / album; [a] and [b] are numbers: years, plays,
 * seconds (length) or days (added, last played).
 */
data class Cond(val field: Field, val op: Op = field.ops.first(), val text: String = "", val a: Long = 0, val b: Long = 0) {
    fun withField(f: Field): Cond = if (f == field) this else when (f) {
        Field.YEAR -> Cond(f, Op.BETWEEN, a = 2010, b = 2019)
        Field.PLAYS -> Cond(f, Op.AT_LEAST, a = 5)
        Field.DURATION -> Cond(f, Op.AT_MOST, a = 240, b = 360)
        Field.ADDED -> Cond(f, Op.IN_LAST, a = 30)
        Field.LAST_PLAYED -> Cond(f, Op.IN_LAST, a = 7)
        else -> Cond(f)
    }

    /** Short readable form, e.g. "Genre is Pop", "Year 2010–2019", "Played 5+ times". */
    fun summary(): String = when (field) {
        Field.LIKED -> if (op == Op.YES) "Liked" else "Not liked"
        Field.PLAYS -> when (op) {
            Op.AT_LEAST -> if (a <= 0) "Any play count" else "Played ${a}+ ${if (a == 1L) "time" else "times"}"
            Op.AT_MOST -> if (a <= 0) "Never played" else "Played ≤ $a ${if (a == 1L) "time" else "times"}"
            else -> "Played ${minOf(a, b)}–${maxOf(a, b)} times"
        }
        Field.YEAR -> when (op) { Op.AT_LEAST -> "Year $a+"; Op.AT_MOST -> "Year ≤ $a"; else -> "Year ${minOf(a, b)}–${maxOf(a, b)}" }
        Field.DURATION -> when (op) {
            Op.AT_LEAST -> "Longer than ${formatTime(a * 1000)}"; Op.AT_MOST -> "Shorter than ${formatTime(a * 1000)}"
            else -> "Length ${formatTime(minOf(a, b) * 1000)}–${formatTime(maxOf(a, b) * 1000)}"
        }
        Field.ADDED, Field.LAST_PLAYED -> {
            val what = if (field == Field.ADDED) "Added" else "Played"
            when (op) { Op.NEVER -> "Never played"; Op.NOT_IN_LAST -> "$what over ${days(a)} ago"; else -> "$what in the last ${days(a)}" }
        }
        else -> "${field.label} ${op.label(field)} ${text.trim().ifEmpty { "…" }}"
    }

    fun toJson(): JSONObject = JSONObject().put("f", field.name).put("o", op.name).put("t", text).put("a", a).put("b", b)

    companion object {
        fun fromJson(o: JSONObject): Cond? = runCatching {
            val f = Field.valueOf(o.getString("f"))
            val op = Op.valueOf(o.getString("o")).takeIf { it in f.ops } ?: f.ops.first()
            Cond(f, op, o.optString("t"), o.optLong("a"), o.optLong("b"))
        }.getOrNull()
        private fun days(n: Long) = if (n == 1L) "1 day" else "$n days"
    }
}

enum class SmartSort(val label: String) {
    TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), YEAR_NEW("Newest first"), YEAR_OLD("Oldest first"),
    ADDED_NEW("Recently added"), MOST_PLAYED("Most played"), LEAST_PLAYED("Least played"), LAST_PLAYED("Recently played"),
    LIKED_NEW("Recently liked"), LONGEST("Longest"), SHORTEST("Shortest"), RANDOM("Random");
}

enum class Match(val label: String) { ALL("All"), ANY("Any") }

data class SmartRule(
    val match: Match = Match.ALL,
    val conds: List<Cond> = emptyList(),
    val sort: SmartSort = SmartSort.ARTIST,
    /** Max songs; 0 = no limit. */
    val limit: Int = 0,
) {
    fun summary(): String {
        if (conds.isEmpty()) return "All songs"
        val parts = conds.map { it.summary() }
        return if (match == Match.ANY && parts.size > 1) "Any of: " + parts.joinToString(" · ") else parts.joinToString(" · ")
    }

    fun toJson(): JSONObject = JSONObject().put("m", match.name).put("s", sort.name).put("l", limit)
        .put("c", JSONArray().apply { conds.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): SmartRule {
            val c = o.optJSONArray("c")
            return SmartRule(
                runCatching { Match.valueOf(o.getString("m")) }.getOrDefault(Match.ALL),
                if (c == null) emptyList() else (0 until c.length()).mapNotNull { Cond.fromJson(c.getJSONObject(it)) },
                runCatching { SmartSort.valueOf(o.getString("s")) }.getOrDefault(SmartSort.ARTIST),
                o.optInt("l").coerceAtLeast(0),
            )
        }
    }
}

/** A saved, rule-based playlist. Its songs are never stored: they are worked out from the rule every time. */
data class SmartPlaylist(val id: Long, val name: String, val rule: SmartRule, val created: Long)

/** The rule engine: pure Kotlin, offline, unit-tested. */
object SmartEngine {
    const val DAY = 86_400_000L
    private val collator: Collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }

    /** Splits a genre tag like "Pop; Dance" or "Rock/Pop" into its parts. */
    fun genres(t: Track): List<String> = splitGenres(t.genre)

    fun splitGenres(g: String?): List<String> =
        g?.split(';', '\u0000', '/', ',', '|')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList()

    /** Genre identity: "Hip-Hop", "hip hop" and "HipHop" are one genre. */
    fun genreKey(g: String): String = LibraryIndex.fold(g).filter { it.isLetterOrDigit() }

    fun matches(c: Cond, t: Track, s: TrackStats, now: Long): Boolean = when (c.field) {
        Field.GENRE -> text(c, genres(t))
        Field.ARTIST -> text(c, listOf(t.artist) + listOfNotNull(t.albumArtist) + t.artist.split(",", "&", " feat. ", " ft. ", " x ").map { it.trim() })
        Field.ALBUM -> text(c, listOf(t.album))
        Field.YEAR -> t.year?.let { num(c, it.toLong()) } ?: false
        Field.PLAYS -> num(c, s.plays.toLong())
        Field.DURATION -> t.durationMs > 0 && num(c, t.durationMs / 1000)
        Field.ADDED -> date(c, t.addedAt.takeIf { it > 0 }, now)
        Field.LAST_PLAYED -> date(c, s.lastPlayed.takeIf { it > 0 }, now)
        Field.LIKED -> if (c.op == Op.NO) !s.liked else s.liked
    }

    private fun text(c: Cond, values: List<String>): Boolean {
        val q = LibraryIndex.fold(c.text.trim())
        if (q.isEmpty()) return true  // an unfinished rule filters nothing out
        val vs = values.map { LibraryIndex.fold(it.trim()) }
        val qk = q.filter { it.isLetterOrDigit() }
        return when (c.op) {
            Op.IS -> vs.any { it == q || (qk.isNotEmpty() && it.filter { ch -> ch.isLetterOrDigit() } == qk) }
            Op.IS_NOT -> vs.none { it == q || (qk.isNotEmpty() && it.filter { ch -> ch.isLetterOrDigit() } == qk) }
            Op.CONTAINS -> vs.any { it.contains(q) }
            else -> vs.none { it.contains(q) }
        }
    }

    private fun num(c: Cond, v: Long): Boolean = when (c.op) {
        Op.AT_LEAST -> v >= c.a
        Op.AT_MOST -> v <= c.a
        else -> v in minOf(c.a, c.b)..maxOf(c.a, c.b)
    }

    private fun date(c: Cond, at: Long?, now: Long): Boolean = when (c.op) {
        Op.NEVER -> at == null
        Op.IN_LAST -> at != null && at >= now - c.a * DAY
        else -> at == null || at < now - c.a * DAY
    }

    fun evaluate(rule: SmartRule, tracks: List<Track>, stats: Map<String, TrackStats>, now: Long, seed: Long = 0): List<Track> {
        fun st(t: Track) = stats[t.id] ?: Stats.NONE
        val hit = if (rule.conds.isEmpty()) tracks else tracks.filter { t ->
            val s = st(t)
            if (rule.match == Match.ALL) rule.conds.all { matches(it, t, s, now) } else rule.conds.any { matches(it, t, s, now) }
        }
        val sorted = sort(hit, rule.sort, stats, seed)
        return if (rule.limit > 0) sorted.take(rule.limit) else sorted
    }

    fun sort(ts: List<Track>, sort: SmartSort, stats: Map<String, TrackStats>, seed: Long = 0): List<Track> {
        fun st(t: Track) = stats[t.id] ?: Stats.NONE
        val byTitle = compareBy<Track, String>(collator) { it.title }
        val byArtist = compareBy<Track, String>(collator) { it.artist }.thenBy(collator) { it.album }.thenBy { it.disc ?: 1 }.thenBy { it.track ?: 0 }.then(byTitle)
        return when (sort) {
            SmartSort.TITLE -> ts.sortedWith(byTitle)
            SmartSort.ARTIST -> ts.sortedWith(byArtist)
            SmartSort.ALBUM -> ts.sortedWith(compareBy<Track, String>(collator) { it.album }.thenBy { it.disc ?: 1 }.thenBy { it.track ?: 0 }.then(byTitle))
            SmartSort.YEAR_NEW -> ts.sortedWith(compareByDescending<Track> { it.year ?: Int.MIN_VALUE }.then(byArtist))
            SmartSort.YEAR_OLD -> ts.sortedWith(compareBy<Track> { it.year ?: Int.MAX_VALUE }.then(byArtist))
            SmartSort.ADDED_NEW -> ts.sortedWith(compareByDescending<Track> { it.addedAt }.then(byArtist))
            SmartSort.MOST_PLAYED -> ts.sortedWith(compareByDescending<Track> { st(it).plays }.thenByDescending { st(it).lastPlayed }.then(byTitle))
            SmartSort.LEAST_PLAYED -> ts.sortedWith(compareBy<Track> { st(it).plays }.thenBy { st(it).lastPlayed }.then(byTitle))
            SmartSort.LAST_PLAYED -> ts.sortedWith(compareByDescending<Track> { st(it).lastPlayed }.then(byTitle))
            SmartSort.LIKED_NEW -> ts.sortedWith(compareByDescending<Track> { st(it).likedAt }.then(byTitle))
            SmartSort.LONGEST -> ts.sortedWith(compareByDescending<Track> { it.durationMs }.then(byTitle))
            SmartSort.SHORTEST -> ts.sortedWith(compareBy<Track> { it.durationMs }.then(byTitle))
            // Stable for a given seed, so a random list doesn't reshuffle every time a play is counted.
            SmartSort.RANDOM -> ts.sortedBy { it.id }.shuffled(Random(seed))
        }
    }

    // ------------------------------------------------------------------ saved smart playlists (pure list ops)
    fun create(all: List<SmartPlaylist>, name: String, rule: SmartRule, now: Long): Pair<List<SmartPlaylist>, SmartPlaylist> {
        val id = (all.maxOfOrNull { it.id } ?: 0) + 1
        val p = SmartPlaylist(id, uniqueName(all, name.trim().ifEmpty { "Smart playlist" }), rule, now)
        return (all + p) to p
    }
    fun update(all: List<SmartPlaylist>, id: Long, name: String, rule: SmartRule) =
        all.map { if (it.id == id) it.copy(name = uniqueName(all, name.trim().ifEmpty { it.name }, id), rule = rule) else it }
    fun delete(all: List<SmartPlaylist>, id: Long) = all.filter { it.id != id }
    private fun uniqueName(all: List<SmartPlaylist>, name: String, except: Long? = null): String {
        val taken = all.filter { it.id != except }.map { it.name.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        var n = 2
        while ("$name $n".lowercase() in taken) n++
        return "$name $n"
    }

    fun toJson(all: List<SmartPlaylist>): String = JSONArray().apply {
        all.forEach { p -> put(JSONObject().put("id", p.id).put("name", p.name).put("c", p.created).put("r", p.rule.toJson())) }
    }.toString()
    fun fromJson(s: String): List<SmartPlaylist> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).mapNotNull { i -> runCatching { a.getJSONObject(i).let { o ->
            SmartPlaylist(o.getLong("id"), o.getString("name"), SmartRule.fromJson(o.getJSONObject("r")), o.optLong("c"))
        } }.getOrNull() }
    }.getOrElse { emptyList() }
}

/** The built-in lists that keep themselves up to date. [key] is stable, so a screen can be reopened by it. */
data class AutoList(val key: String, val name: String, val kind: AutoKind, val rule: SmartRule)

enum class AutoKind { FAVORITES, MOST_PLAYED, RECENTLY_PLAYED, RECENTLY_ADDED, NEVER_PLAYED, GENRE, DECADE }

object AutoLists {
    val FAVORITES = AutoList("auto:favorites", "Favorites", AutoKind.FAVORITES, SmartRule(Match.ALL, listOf(Cond(Field.LIKED, Op.YES)), SmartSort.LIKED_NEW))
    val MOST_PLAYED = AutoList("auto:most", "Most played", AutoKind.MOST_PLAYED, SmartRule(Match.ALL, listOf(Cond(Field.PLAYS, Op.AT_LEAST, a = 1)), SmartSort.MOST_PLAYED, 50))
    val RECENTLY_PLAYED = AutoList("auto:recent", "Recently played", AutoKind.RECENTLY_PLAYED, SmartRule(Match.ALL, listOf(Cond(Field.PLAYS, Op.AT_LEAST, a = 1)), SmartSort.LAST_PLAYED, 50))
    val RECENTLY_ADDED = AutoList("auto:added", "Recently added", AutoKind.RECENTLY_ADDED, SmartRule(Match.ALL, emptyList(), SmartSort.ADDED_NEW, 50))
    val NEVER_PLAYED = AutoList("auto:never", "Never played", AutoKind.NEVER_PLAYED, SmartRule(Match.ALL, listOf(Cond(Field.PLAYS, Op.AT_MOST, a = 0)), SmartSort.RANDOM))

    val fixed = listOf(FAVORITES, MOST_PLAYED, RECENTLY_PLAYED, RECENTLY_ADDED, NEVER_PLAYED)

    /** One list per genre in the tags, biggest first. Spelling variants of a genre are merged. */
    fun genres(tracks: List<Track>): List<AutoList> {
        val byKey = LinkedHashMap<String, MutableList<String>>()
        tracks.forEach { t -> SmartEngine.genres(t).forEach { g -> val k = SmartEngine.genreKey(g); if (k.isNotEmpty()) byKey.getOrPut(k) { ArrayList() }.add(g) } }
        return byKey.entries.sortedWith(compareByDescending<Map.Entry<String, MutableList<String>>> { it.value.size }.thenBy { it.key }).map { (k, names) ->
            val name = names.groupingBy { it }.eachCount().maxWith(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key }).key
            AutoList("genre:$k", name, AutoKind.GENRE, SmartRule(Match.ALL, listOf(Cond(Field.GENRE, Op.IS, name)), SmartSort.ARTIST))
        }
    }

    /** One list per decade in the tags, newest first. */
    fun decades(tracks: List<Track>): List<AutoList> =
        tracks.mapNotNull { it.year }.filter { it in 1000..2999 }.map { it / 10 * 10 }.distinct().sortedDescending().map { d ->
            AutoList("decade:$d", decadeName(d), AutoKind.DECADE, SmartRule(Match.ALL, listOf(Cond(Field.YEAR, Op.BETWEEN, a = d.toLong(), b = d + 9L)), SmartSort.YEAR_OLD))
        }

    fun decadeName(d: Int): String = if (d in 1900..1999) "${d % 100}s".padStart(3, '0') else "${d}s"

    /** Finds any built-in list by key (genre and decade lists are rebuilt from the library). */
    fun byKey(key: String, tracks: List<Track>): AutoList? =
        fixed.firstOrNull { it.key == key } ?: when {
            key.startsWith("genre:") -> genres(tracks).firstOrNull { it.key == key }
            key.startsWith("decade:") -> decades(tracks).firstOrNull { it.key == key }
            else -> null
        }
}
