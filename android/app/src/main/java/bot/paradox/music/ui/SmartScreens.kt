package bot.paradox.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bot.paradox.music.data.AutoKind
import bot.paradox.music.data.AutoLists
import bot.paradox.music.data.Cond
import bot.paradox.music.data.Field
import bot.paradox.music.data.LibraryIndex
import bot.paradox.music.data.Match
import bot.paradox.music.data.Op
import bot.paradox.music.data.SmartEngine
import bot.paradox.music.data.SmartPlaylist
import bot.paradox.music.data.SmartRule
import bot.paradox.music.data.SmartSort
import bot.paradox.music.data.Track
import bot.paradox.music.data.TrackStats

/** What the Smart section and its screens can ask the app to do. Tests pass the no-op default. */
class SmartActions(
    val open: (key: String) -> Unit = {},
    val new: () -> Unit = {},
    val edit: (SmartPlaylist) -> Unit = {},
    val delete: (SmartPlaylist) -> Unit = {},
    val saveAs: (name: String, ids: List<String>) -> Unit = { _, _ -> },
)

fun smartKey(p: SmartPlaylist) = "smart:${p.id}"

fun autoIcon(k: AutoKind): ImageVector = when (k) {
    AutoKind.FAVORITES -> Icons.Rounded.Favorite
    AutoKind.MOST_PLAYED -> Icons.AutoMirrored.Rounded.TrendingUp
    AutoKind.RECENTLY_PLAYED -> Icons.Rounded.History
    AutoKind.RECENTLY_ADDED -> Icons.Rounded.NewReleases
    AutoKind.NEVER_PLAYED -> Icons.Rounded.Explore
    AutoKind.GENRE -> Icons.Rounded.LibraryMusic
    AutoKind.DECADE -> Icons.Rounded.CalendarMonth
}

/** A calm monochrome tile with one icon, used where a list has no cover of its own. */
@Composable
fun IconTile(icon: ImageVector, size: Dp, shape: Shape = RoundedCornerShape(size * 0.22f), strong: Boolean = false, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Box(modifier.size(size).clip(shape).background(if (strong) p.primary else p.surface2), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (strong) p.onPrimary else p.text, modifier = Modifier.size(size * 0.36f))
    }
}

/** A list with its songs worked out, for the Smart section. */
data class SmartEntry(val key: String, val name: String, val icon: ImageVector, val tracks: List<Track>, val strong: Boolean = false, val useArt: Boolean = false)

@Composable
private fun EntryTile(e: SmartEntry, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.width(112.dp).clip(shape).clickable(onClick = onClick).padding(bottom = 4.dp)) {
        val art = if (e.useArt) e.tracks.firstNotNullOfOrNull { it.art } else null
        if (art != null) Cover(art, 112.dp, shape)
        else IconTile(e.icon, 112.dp, shape, strong = e.strong,
            modifier = if (!p.dark && !e.strong) Modifier.border(1.dp, p.hairline, shape) else Modifier)
        Spacer(Modifier.height(8.dp))
        Text(e.name, style = ts(14.5f, 750, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(songs(e.tracks.size), style = ts(12.5f, 600, p.muted), maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun TileRow(entries: List<SmartEntry>, open: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(entries, key = { it.key }) { e -> EntryTile(e) { open(e.key) } }
    }
}

/** Works out every built-in list once; genre and decade lists don't depend on plays, so they are cached on the library alone. */
@Composable
fun rememberSmartEntries(index: LibraryIndex, stats: Map<String, TrackStats>, now: Long): Triple<List<SmartEntry>, List<SmartEntry>, List<SmartEntry>> {
    val fixed = remember(index, stats) {
        AutoLists.fixed.map { a -> SmartEntry(a.key, a.name, autoIcon(a.kind), SmartEngine.evaluate(a.rule, index.tracks, stats, now, seed = 7), strong = a.kind == AutoKind.FAVORITES) }
    }
    val genres = remember(index) {
        AutoLists.genres(index.tracks).map { a -> SmartEntry(a.key, a.name, autoIcon(a.kind), SmartEngine.evaluate(a.rule, index.tracks, emptyMap(), now), useArt = true) }
    }
    val decades = remember(index) {
        AutoLists.decades(index.tracks).map { a -> SmartEntry(a.key, a.name, autoIcon(a.kind), SmartEngine.evaluate(a.rule, index.tracks, emptyMap(), now), useArt = true) }
    }
    return Triple(fixed, genres, decades)
}

/** The "Smart" section at the top of the Playlists tab: built-in lists, your rule-based lists, genres and decades. */
fun LazyListScope.smartSection(
    fixed: List<SmartEntry>, genres: List<SmartEntry>, decades: List<SmartEntry>,
    saved: List<Pair<SmartPlaylist, Int>>, s: SmartActions,
) {
    item(key = "h-smart") { SectionLabel("Smart", Modifier.padding(start = 4.dp)) }
    item(key = "smart-fixed") { TileRow(fixed, s.open) }
    item(key = "smart-new") {
        val p = LocalPal.current
        Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = s.new).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.AutoAwesome, 52.dp, RoundedCornerShape(14.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("New smart playlist", style = ts(15.5f, 750, p.text))
                Text("Pick rules; it fills and updates itself", style = ts(13f, 600, p.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    items(saved, key = { "sp" + it.first.id }) { (sp, n) ->
        val p = LocalPal.current
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { s.open(smartKey(sp)) }.padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.AutoAwesome, 52.dp, RoundedCornerShape(14.dp), strong = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(sp.name, style = ts(15.5f, 750, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(songs(n) + " · " + sp.rule.summary(), style = ts(13f, 600, p.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RowMenu(listOf(MenuItem("Edit rules", Icons.Rounded.Edit) { s.edit(sp) }, MenuItem("Delete", Icons.Rounded.Delete) { s.delete(sp) }))
        }
    }
    if (genres.isNotEmpty()) {
        item(key = "h-genres") { SectionLabel("Genres", Modifier.padding(start = 4.dp)) }
        item(key = "smart-genres") { TileRow(genres, s.open) }
    }
    if (decades.isNotEmpty()) {
        item(key = "h-decades") { SectionLabel("Decades", Modifier.padding(start = 4.dp)) }
        item(key = "smart-decades") { TileRow(decades, s.open) }
    }
}

fun ago(at: Long, now: Long): String {
    val d = (now - at).coerceAtLeast(0) / 60_000
    return when {
        d < 1 -> "just now"; d < 60 -> "$d min ago"; d < 24 * 60 -> "${d / 60} h ago"
        d < 48 * 60 -> "yesterday"; else -> "${d / (24 * 60)} days ago"
    }
}

/** A smart, built-in or genre/decade list: always worked out fresh from its rule. */
@Composable
fun SmartScreen(
    title: String, kind: AutoKind?, rule: SmartRule, tracks: List<Track>, stats: Map<String, TrackStats>, now: Long,
    currentId: String?, a: Actions, s: SmartActions, onBack: () -> Unit, saved: SmartPlaylist? = null, bottomPad: Dp = 96.dp,
) {
    val ids = tracks.map { it.id }
    val sub = when (kind) {
        AutoKind.GENRE -> "Genre"; AutoKind.DECADE -> "Decade"; null -> "Smart playlist"; else -> "Updates by itself"
    }
    val menu = groupMenu(ids, a).filter { it.label != "Add to playlist" } + listOfNotNull(
        MenuItem("Save as playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) { s.saveAs(title, ids) },
        saved?.let { sp -> MenuItem("Edit rules", Icons.Rounded.Edit) { s.edit(sp) } },
        saved?.let { sp -> MenuItem("Delete", Icons.Rounded.Delete) { s.delete(sp) } },
    )
    val icon = if (kind == null) Icons.Rounded.AutoAwesome else autoIcon(kind)
    val useArt = kind == AutoKind.GENRE || kind == AutoKind.DECADE
    LazyColumn(Modifier.fillMaxSize().background(LocalPal.current.bg), contentPadding = PaddingValues(bottom = bottomPad)) {
        item { TopBack(onBack, menu) }
        item {
            Hero(if (useArt) tracks.firstNotNullOfOrNull { it.art } else null, title, sub,
                songs(tracks.size) + (if (tracks.isNotEmpty()) " · " + totalTime(tracks) else ""), artSize = 180.dp, icon = icon)
        }
        if (kind == null || kind == AutoKind.NEVER_PLAYED || kind == AutoKind.MOST_PLAYED) item {
            Text(rule.summary() + if (rule.limit > 0) " · top ${rule.limit} by ${rule.sort.label.lowercase()}" else " · ${rule.sort.label.lowercase()}",
                style = ts(13f, 650, LocalPal.current.faint), maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (tracks.isEmpty()) item {
            when (kind) {
                AutoKind.FAVORITES -> Empty("No favorites yet", "Tap ♡ on the now playing screen, or Like in a song's ⋮ menu.")
                AutoKind.MOST_PLAYED, AutoKind.RECENTLY_PLAYED -> Empty("Nothing played yet", "A song counts once you've heard half of it, or 30 seconds.")
                AutoKind.NEVER_PLAYED -> Empty("You've played everything", "Every song in your library has been played.")
                else -> Empty("No songs match", if (saved != null) "Edit the rules to let more songs in." else "Nothing in your library fits this yet.")
            }
            if (saved != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PillButton("Edit rules", primary = false, icon = Icons.Rounded.Edit) { s.edit(saved) } }
        } else item { PlayShuffle({ a.play(ids, 0) }, { a.shuffle(ids) }) }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            val st = stats[t.id]
            val subtitle = when (kind) {
                AutoKind.MOST_PLAYED -> st?.let { "${t.artist} · ${it.plays} ${if (it.plays == 1) "play" else "plays"}" }
                AutoKind.RECENTLY_PLAYED -> st?.takeIf { it.lastPlayed > 0 }?.let { "${t.artist} · ${ago(it.lastPlayed, now)}" }
                else -> null
            }
            Box(Modifier.padding(horizontal = 10.dp)) { SongRow(t, t.id == currentId, a, { a.play(ids, i) }, subtitle = subtitle) }
        }
    }
}

/** A mix made from a song or an artist; it can be played, re-rolled or saved as a normal playlist. */
@Composable
fun MixScreen(name: String, from: String, tracks: List<Track>, currentId: String?, a: Actions, onBack: () -> Unit, onSave: () -> Unit, onReroll: () -> Unit, bottomPad: Dp = 96.dp) {
    val ids = tracks.map { it.id }
    LazyColumn(Modifier.fillMaxSize().background(LocalPal.current.bg), contentPadding = PaddingValues(bottom = bottomPad)) {
        item { TopBack(onBack, groupMenu(ids, a).filter { it.label != "Add to playlist" } + MenuItem("New mix", Icons.Rounded.Refresh, onReroll)) }
        item { Hero(null, name, "Mix · from $from", songs(tracks.size) + " · " + totalTime(tracks), artSize = 180.dp, icon = MixIcon) }
        item { PlayShuffle({ a.play(ids, 0) }, { a.shuffle(ids) }) }
        item {
            Box(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 8.dp)) {
                PillButton("Save as playlist", Modifier.fillMaxWidth(), primary = false, icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = onSave)
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            Box(Modifier.padding(horizontal = 10.dp)) { SongRow(t, t.id == currentId, a, { a.play(ids, i) }) }
        }
    }
}

// ---------------------------------------------------------------------- rule builder

/** A small rounded choice button that opens a menu, like the sort control. */
@Composable
fun ChoicePill(label: String, options: List<String>, selected: String = label, filled: Boolean = true, onPick: (Int) -> Unit) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clip(RoundedCornerShape(16.dp)).background(if (filled) p.surface2 else p.surface).clickable { open = true }
            .padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = ts(14f, 750, p.text), maxLines = 1)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Rounded.ExpandMore, null, tint = p.muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(18.dp), containerColor = p.surface) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o, style = ts(15f, if (o == selected) 850 else 650, if (o == selected) p.text else p.muted)) }, onClick = { open = false; onPick(i) })
            }
        }
    }
}

@Composable
private fun InputBox(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, number: Boolean = false) {
    val p = LocalPal.current
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(p.surface2).padding(horizontal = 14.dp, vertical = 11.dp)) {
        if (value.isEmpty()) Text(hint, style = ts(15f, 600, p.faint), maxLines = 1)
        BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, textStyle = ts(15f, 700, p.text), cursorBrush = SolidColor(p.text),
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default)
    }
}

private fun num(s: String): Long? = s.trim().toLongOrNull()
private fun minutesText(sec: Long): String = if (sec % 60 == 0L) (sec / 60).toString() else "%.1f".format(java.util.Locale.ROOT, sec / 60.0)
private fun minutesToSec(s: String): Long? = s.trim().replace(',', '.').toDoubleOrNull()?.let { (it * 60).toLong() }

/** One editable rule: field ▾, comparison ▾, the value, and ✕. */
@Composable
private fun RuleCard(c: Cond, suggestions: (Field) -> List<String>, onChange: (Cond) -> Unit, onRemove: () -> Unit) {
    val p = LocalPal.current
    PCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChoicePill(c.field.label, Field.entries.map { it.label }) { onChange(c.withField(Field.entries[it])) }
            Spacer(Modifier.width(8.dp))
            ChoicePill(c.op.label(c.field), c.field.ops.map { it.label(c.field) }) { onChange(c.copy(op = c.field.ops[it])) }
            Spacer(Modifier.weight(1f))
            BareIcon(Icons.Rounded.Close, "Remove rule", tint = p.faint, size = 36.dp, iconSize = 20.dp, onClick = onRemove)
        }
        when (c.field.kind) {
            Field.Kind.TEXT -> {
                Spacer(Modifier.height(10.dp))
                InputBox(c.text, { onChange(c.copy(text = it)) }, when (c.field) { Field.GENRE -> "e.g. Bollywood"; Field.ARTIST -> "e.g. Arijit Singh"; else -> "Album name" }, Modifier.fillMaxWidth())
                val q = LibraryIndex.fold(c.text.trim())
                val sug = remember(c.field, q) { suggestions(c.field).filter { q.isEmpty() || LibraryIndex.fold(it).contains(q) }.filter { LibraryIndex.fold(it) != q }.take(8) }
                if (sug.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sug) { v ->
                            Text(v, style = ts(13f, 700, p.muted), maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                .border(1.dp, p.hairline, RoundedCornerShape(14.dp)).clickable { onChange(c.copy(text = v)) }.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                }
            }
            Field.Kind.NUMBER, Field.Kind.DATE -> if (c.op != Op.NEVER) {
                Spacer(Modifier.height(10.dp))
                val unit = when (c.field) { Field.PLAYS -> "times"; Field.DURATION -> "min"; Field.ADDED, Field.LAST_PLAYED -> "days"; else -> "" }
                val dur = c.field == Field.DURATION
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var ta by remember(c.field, c.op) { mutableStateOf(if (dur) minutesText(c.a) else c.a.toString()) }
                    InputBox(ta, { v -> ta = v; (if (dur) minutesToSec(v) else num(v))?.let { onChange(c.copy(a = it)) } }, "0", Modifier.width(96.dp), number = true)
                    if (c.op == Op.BETWEEN) {
                        Text("and", style = ts(14f, 700, p.muted), modifier = Modifier.padding(horizontal = 10.dp))
                        var tb by remember(c.field, c.op) { mutableStateOf(if (dur) minutesText(c.b) else c.b.toString()) }
                        InputBox(tb, { v -> tb = v; (if (dur) minutesToSec(v) else num(v))?.let { onChange(c.copy(b = it)) } }, "0", Modifier.width(96.dp), number = true)
                    }
                    if (unit.isNotEmpty()) Text(unit, style = ts(14f, 700, p.muted), modifier = Modifier.padding(start = 10.dp))
                }
            }
            Field.Kind.BOOL -> {}
        }
    }
}

private val LIMITS = listOf(0, 10, 25, 50, 100, 200, 500)
private fun limitLabel(n: Int) = if (n == 0) "No limit" else "$n songs"

/** Auto name when the user leaves the name empty: the first rule's value, or its summary. */
fun autoName(rule: SmartRule): String {
    val c = rule.conds.firstOrNull() ?: return "Smart playlist"
    return if (c.field.kind == Field.Kind.TEXT && c.op == Op.IS && c.text.isNotBlank()) c.text.trim() else c.summary().take(32)
}

private data class EditRow(val uid: Int, val cond: Cond)

/**
 * "New smart playlist": match all / any of the rules, a sort and a limit, with a live count of what matches.
 * Saving stores only the rule; the songs are worked out again every time, so the list stays current.
 */
@Composable
fun SmartEditor(
    initial: SmartPlaylist?, index: LibraryIndex, stats: Map<String, TrackStats>, now: Long,
    onSave: (name: String, rule: SmartRule) -> Unit, onDelete: (() -> Unit)?, onBack: () -> Unit,
) {
    val p = LocalPal.current
    val start = initial?.rule ?: SmartRule(Match.ALL, listOf(Cond(Field.GENRE)), SmartSort.ARTIST, 0)
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var match by remember { mutableStateOf(start.match) }
    var sort by remember { mutableStateOf(start.sort) }
    var limit by remember { mutableIntStateOf(start.limit) }
    var nextUid by remember { mutableIntStateOf(start.conds.size) }
    val rows = remember { mutableStateListOf<EditRow>().apply { start.conds.forEachIndexed { i, c -> add(EditRow(i, c)) } } }
    val rule = SmartRule(match, rows.map { it.cond }, sort, limit)
    val result = remember(rule, index, stats) { SmartEngine.evaluate(rule, index.tracks, stats, now, seed = 7) }
    val sugGenres = remember(index) { AutoLists.genres(index.tracks).map { it.name } }
    val sugArtists = remember(index) { index.artists(bot.paradox.music.data.ArtistSort.SONGS).map { it.name } }
    val sugAlbums = remember(index) { index.albums(bot.paradox.music.data.AlbumSort.TITLE).map { it.title }.distinct() }
    val suggestions: (Field) -> List<String> = { f -> when (f) { Field.GENRE -> sugGenres; Field.ARTIST -> sugArtists; Field.ALBUM -> sugAlbums; else -> emptyList() } }
    val bars = WindowInsets.systemBars.asPaddingValues()

    Column(Modifier.fillMaxSize().background(p.bg).padding(top = bars.calculateTopPadding()).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BareIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Text(if (initial == null) "New smart playlist" else "Edit smart playlist", style = ts(18f, 800, p.text), modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            PillButton("Save", Modifier.heightIn(max = 44.dp)) { onSave(name.trim().ifEmpty { autoName(rule) }, rule) }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(8.dp))
            InputBox(name, { name = it }, "Name (optional)", Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Songs that match", style = ts(15f, 750, p.text))
                Spacer(Modifier.weight(1f))
                Segmented(Match.entries.map { it.label }, match.ordinal, Modifier.width(132.dp)) { match = Match.entries[it] }
            }
            Text(if (match == Match.ALL) "every rule below" else "at least one rule below", style = ts(13f, 600, p.muted))
            Spacer(Modifier.height(12.dp))
            rows.forEachIndexed { i, r ->
                key(r.uid) {
                    RuleCard(r.cond, suggestions, onChange = { rows[i] = r.copy(cond = it) }, onRemove = { rows.removeAt(i) })
                    Spacer(Modifier.height(10.dp))
                }
            }
            PillButton("Add rule", primary = false, icon = Icons.Rounded.Add) {
                val used = rows.map { it.cond.field }.toSet()
                val f = Field.entries.firstOrNull { it !in used } ?: Field.GENRE
                rows.add(EditRow(nextUid++, Cond(Field.GENRE).withField(f)))
            }
            SectionLabel("Order", Modifier.padding(start = 0.dp))
            PCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort by", style = ts(15f, 750, p.text), modifier = Modifier.weight(1f))
                    ChoicePill(sort.label, SmartSort.entries.filter { it != SmartSort.LIKED_NEW }.map { it.label }) { sort = SmartSort.entries.filter { s -> s != SmartSort.LIKED_NEW }[it] }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Limit", style = ts(15f, 750, p.text), modifier = Modifier.weight(1f))
                    ChoicePill(limitLabel(limit), LIMITS.map { limitLabel(it) }) { limit = LIMITS[it] }
                }
            }
            SectionLabel("Preview", Modifier.padding(start = 0.dp))
            PCard(padding = 16.dp) {
                Text(if (result.isEmpty()) "No songs match yet" else "${songs(result.size)} match" + if (result.isNotEmpty()) " · " + totalTime(result) else "",
                    style = ts(16f, 800, p.text))
                Text("Updates by itself as you add songs, play and like them.", style = ts(13f, 600, p.muted))
                result.take(4).forEach { t ->
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cover(t.art, 36.dp, RoundedCornerShape(10.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(t.title + " · " + t.artist, style = ts(14f, 700, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (result.size > 4) { Spacer(Modifier.height(8.dp)); Text("and ${result.size - 4} more", style = ts(13f, 650, p.faint)) }
            }
            if (onDelete != null) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onDelete).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Delete, null, tint = p.muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("Delete smart playlist", style = ts(14f, 750, p.muted))
                    }
                }
            }
            Spacer(Modifier.height(bars.calculateBottomPadding() + 28.dp))
        }
    }
}

/** For the Playlists tab: each saved smart list with its live song count. */
@Composable
fun rememberSavedCounts(saved: List<SmartPlaylist>, index: LibraryIndex, stats: Map<String, TrackStats>, now: Long): List<Pair<SmartPlaylist, Int>> =
    remember(saved, index, stats) { saved.map { it to SmartEngine.evaluate(it.rule, index.tracks, stats, now, seed = it.id).size } }

