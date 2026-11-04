package bot.paradox.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bot.paradox.music.data.AlbumSort
import bot.paradox.music.data.ArtistSort
import bot.paradox.music.data.LibraryIndex
import bot.paradox.music.data.Playlist
import bot.paradox.music.data.SongSort

data class Sorts(val song: SongSort = SongSort.TITLE, val album: AlbumSort = AlbumSort.TITLE, val artist: ArtistSort = ArtistSort.NAME)

/** The library: Songs / Albums / Artists / Playlists, search, sort and Shuffle all. */
@Composable
fun LibraryScreen(
    index: LibraryIndex,
    playlists: List<Playlist>,
    currentId: String?,
    tab: Int,
    onTab: (Int) -> Unit,
    sorts: Sorts,
    onSorts: (Sorts) -> Unit,
    a: Actions,
    onSettings: () -> Unit,
    onNewPlaylist: () -> Unit,
    onRenamePlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    scanning: String?,
    banner: (@Composable () -> Unit)? = null,
    bottomPad: Dp = 96.dp,
    startSearch: Boolean = false,
    initialQuery: String = "",
    smart: List<bot.paradox.music.data.SmartPlaylist> = emptyList(),
    stats: Map<String, bot.paradox.music.data.TrackStats> = emptyMap(),
    smartActions: SmartActions = SmartActions(),
    now: Long = System.currentTimeMillis(),
) {
    val p = LocalPal.current
    var searching by remember { mutableStateOf(startSearch) }
    var query by remember { mutableStateOf(initialQuery) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Spacer(Modifier.height(top + 8.dp))
        if (searching) SearchBar(query, { query = it }) { searching = false; query = "" }
        else {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Library", style = ts(30f, 800, p.text, -0.02f))
                    Text(scanning ?: (songs(index.tracks.size) + " · " + (if (index.albums.size == 1) "1 album" else "${index.albums.size} albums")),
                        style = ts(13.5f, 600, p.muted))
                }
                RoundIconButton(Icons.Rounded.Search, "Search", size = 46.dp) { searching = true }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Rounded.Settings, "Settings", size = 46.dp, onClick = onSettings)
            }
            Spacer(Modifier.height(16.dp))
            Segmented(listOf("Songs", "Albums", "Artists", "Playlists"), tab, Modifier.padding(horizontal = 18.dp), onTab)
        }
        Spacer(Modifier.height(6.dp))
        val pad = PaddingValues(start = 14.dp, end = 10.dp, top = 6.dp, bottom = bottomPad)
        if (searching) {
            SearchResults(index, query, currentId, a, pad)
            return@Column
        }
        when (tab) {
            0 -> {
                val list = remember(index, sorts.song) { index.songs(sorts.song) }
                val ids = remember(list) { list.map { it.id } }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                    banner?.let { b -> item { Box(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)) { b() } } }
                    item { ToolRow(onShuffle = { a.shuffle(ids) }, sortLabel = sorts.song.label, options = SongSort.entries.map { it.label }) { onSorts(sorts.copy(song = SongSort.entries[it])) } }
                    if (list.isEmpty()) item { Empty("No songs yet", "Add a folder in Settings, or rescan.") }
                    items(list, key = { it.id }) { t -> SongRow(t, t.id == currentId, a, { a.play(ids, ids.indexOf(t.id)) }) }
                }
            }
            1 -> {
                val list = remember(index, sorts.album) { index.albums(sorts.album) }
                LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = bottomPad),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ToolRow(onShuffle = { a.shuffle(index.tracks.map { it.id }) }, sortLabel = sorts.album.label, options = AlbumSort.entries.map { it.label }, padEnd = 0.dp) { onSorts(sorts.copy(album = AlbumSort.entries[it])) }
                    }
                    items(list, key = { it.key }) { al -> AlbumTile(al) { a.openAlbum(al.key) } }
                }
            }
            2 -> {
                val list = remember(index, sorts.artist) { index.artists(sorts.artist) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                    item { ToolRow(onShuffle = { a.shuffle(index.tracks.map { it.id }) }, sortLabel = sorts.artist.label, options = ArtistSort.entries.map { it.label }) { onSorts(sorts.copy(artist = ArtistSort.entries[it])) } }
                    items(list, key = { it.name }) { ar -> ArtistRow(ar) { a.openArtist(ar.name) } }
                }
            }
            else -> {
              val (fixed, genres, decades) = rememberSmartEntries(index, stats, now)
              val saved = rememberSavedCounts(smart, index, stats, now)
              LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                smartSection(fixed, genres, decades, saved, smartActions)
                item(key = "h-playlists") { SectionLabel("Playlists", Modifier.padding(start = 4.dp)) }
                item {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onNewPlaylist).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(p.primary), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = p.onPrimary) }
                        Spacer(Modifier.width(14.dp))
                        Text("New playlist", style = ts(15.5f, 750, p.text))
                    }
                }
                if (playlists.isEmpty()) item { Empty("No playlists", "Make one, then add songs from any song's ⋮ menu.") }
                items(playlists, key = { it.id }) { pl ->
                    PlaylistRow(pl, pl.trackIds.firstNotNullOfOrNull { index.byId[it]?.art }, { a.openPlaylist(pl.id) }, listOf(
                        MenuItem("Rename", Icons.Rounded.Edit) { onRenamePlaylist(pl) },
                        MenuItem("Delete", Icons.Rounded.Delete) { onDeletePlaylist(pl) },
                    ))
                }
            }
            }
        }
    }
}

@Composable
fun ToolRow(onShuffle: () -> Unit, sortLabel: String, options: List<String>, padEnd: Dp = 8.dp, onSort: (Int) -> Unit) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = padEnd, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton("Shuffle all", primary = true, icon = Icons.Rounded.Shuffle, onClick = onShuffle, modifier = Modifier.heightIn(max = 46.dp))
        Spacer(Modifier.weight(1f))
        Box {
            Row(Modifier.clip(RoundedCornerShape(20.dp)).clickable { open = true }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SwapVert, null, tint = p.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(sortLabel, style = ts(13.5f, 750, p.muted))
            }
            DropdownMenu(open, { open = false }, shape = RoundedCornerShape(18.dp), containerColor = p.surface) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem(text = { Text(o, style = ts(15f, if (o == sortLabel) 850 else 650, if (o == sortLabel) p.text else p.muted)) }, onClick = { open = false; onSort(i) })
                }
            }
        }
    }
}

@Composable
fun Empty(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        H(title, 18f, 800)
        Spacer(Modifier.height(6.dp))
        Body(body, center = true)
    }
}

@Composable
private fun SearchBar(q: String, onQ: (String) -> Unit, onClose: () -> Unit) {
    val p = LocalPal.current
    val focus = remember { FocusRequester() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BareIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onClose)
        Row(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(p.surface2).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = p.faint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Songs, albums, artists", style = ts(15.5f, 600, p.faint))
                BasicTextField(q, onQ, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true, textStyle = ts(15.5f, 650, p.text),
                    cursorBrush = SolidColor(p.text), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
            }
            if (q.isNotEmpty()) Box(Modifier.size(28.dp).clip(CircleShape).clickable { onQ("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Clear", tint = p.muted, modifier = Modifier.size(18.dp))
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun SearchResults(index: LibraryIndex, q: String, currentId: String?, a: Actions, pad: PaddingValues) {
    val r = remember(index, q) { index.search(q) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
        if (q.isBlank()) { item { Empty("Search your library", "Type a song, album or artist.") }; return@LazyColumn }
        if (r.isEmpty) { item { Empty("Nothing found", "No song, album or artist matches “$q”.") }; return@LazyColumn }
        section(r.artists.isNotEmpty(), "Artists") { items(r.artists.take(5), key = { "ar" + it.name }) { ar -> ArtistRow(ar) { a.openArtist(ar.name) } } }
        section(r.albums.isNotEmpty(), "Albums") {
            items(r.albums.take(6), key = { "al" + it.key }) { al ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { a.openAlbum(al.key) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(al.art, 52.dp, RoundedCornerShape(14.dp)); Spacer(Modifier.width(14.dp))
                    Column { H(al.title, 15.5f); Body(al.artist, size = 13f, maxLines = 1) }
                }
            }
        }
        val ids = r.songs.map { it.id }
        section(r.songs.isNotEmpty(), "Songs") { items(r.songs, key = { "s" + it.id }) { t -> SongRow(t, t.id == currentId, a, { a.play(ids, ids.indexOf(t.id)) }) } }
    }
}

private fun LazyListScope.section(show: Boolean, title: String, body: LazyListScope.() -> Unit) {
    if (!show) return
    item(key = "h$title") { SectionLabel(title, Modifier.padding(start = 4.dp)) }
    body()
}
