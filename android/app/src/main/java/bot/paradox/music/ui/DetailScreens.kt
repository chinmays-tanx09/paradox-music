package bot.paradox.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bot.paradox.music.data.Album
import bot.paradox.music.data.Artist
import bot.paradox.music.data.Playlist
import bot.paradox.music.data.Track

@Composable
internal fun TopBack(onBack: () -> Unit, menu: List<MenuItem> = emptyList()) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(Modifier.fillMaxWidth().padding(top = top + 4.dp, start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BareIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
        Spacer(Modifier.weight(1f))
        if (menu.isNotEmpty()) RowMenu(menu, LocalPal.current.text)
    }
}

@Composable
internal fun Hero(art: String?, title: String, sub: String, meta: String, round: Boolean = false, artSize: Dp = 220.dp, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = if (round) CircleShape else RoundedCornerShape(28.dp)
        Box(Modifier.liftShadow(shape)) {
            if (icon != null && art == null) IconTile(icon, artSize, shape) else Cover(art, artSize, shape)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = ts(24f, 800, p.text, -0.015f), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Text(sub, style = ts(15.5f, 650, p.muted), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(meta, style = ts(13f, 650, p.faint), textAlign = TextAlign.Center)
    }
}

@Composable
internal fun PlayShuffle(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Play", Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, onClick = onPlay)
        PillButton("Shuffle", Modifier.weight(1f), primary = false, icon = Icons.Rounded.Shuffle, onClick = onShuffle)
    }
}

internal fun groupMenu(ids: List<String>, a: Actions) = listOf(
    MenuItem("Play next", Icons.Rounded.PlaylistPlay) { a.playNext(ids); a.toast("Plays next") },
    MenuItem("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) { a.addToQueue(ids); a.toast("Added to queue") },
    MenuItem("Add to playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) { a.addToPlaylist(ids) },
)

@Composable
fun AlbumScreen(al: Album, currentId: String?, a: Actions, onBack: () -> Unit, bottomPad: Dp = 96.dp) {
    val ids = al.tracks.map { it.id }
    val multiDisc = al.tracks.mapNotNull { it.disc }.distinct().size > 1
    LazyColumn(Modifier.fillMaxSize().background(LocalPal.current.bg), contentPadding = PaddingValues(bottom = bottomPad)) {
        item { TopBack(onBack, groupMenu(ids, a)) }
        item { Hero(al.art, al.title, al.artist, listOfNotNull(al.year?.toString(), songs(al.tracks.size), totalTime(al.tracks)).joinToString(" · ")) }
        item { PlayShuffle({ a.play(ids, 0) }, { a.shuffle(ids) }) }
        var lastDisc: Int? = null
        al.tracks.forEachIndexed { i, t ->
            if (multiDisc && t.disc != lastDisc) { lastDisc = t.disc; item(key = "disc${t.disc}") { SectionLabel("Disc ${t.disc ?: 1}", Modifier.padding(start = 20.dp)) } }
            item(key = t.id) {
                Box(Modifier.padding(start = 10.dp, end = 10.dp)) {
                    SongRow(t, t.id == currentId, a, { a.play(ids, i) }, number = t.track ?: (i + 1), subtitle = if (t.artist != al.artist) t.artist else null)
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(ar: Artist, currentId: String?, a: Actions, onBack: () -> Unit, bottomPad: Dp = 96.dp) {
    val ids = ar.tracks.map { it.id }
    LazyColumn(Modifier.fillMaxSize().background(LocalPal.current.bg), contentPadding = PaddingValues(bottom = bottomPad)) {
        item { TopBack(onBack, groupMenu(ids, a) + MenuItem("Make a mix", MixIcon) { a.mixArtist(ar.name) }) }
        item { Hero(ar.art, ar.name, "", countLine(ar.tracks.size, ar.albums.size), round = true, artSize = 180.dp) }
        item { PlayShuffle({ a.play(ids, 0) }, { a.shuffle(ids) }) }
        if (ar.albums.isNotEmpty()) {
            item { SectionLabel("Albums", Modifier.padding(start = 18.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(ar.albums, key = { it.key }) { al -> AlbumTile(al, Modifier.width(150.dp)) { a.openAlbum(al.key) } }
                }
            }
        }
        item { SectionLabel("Songs", Modifier.padding(start = 18.dp)) }
        itemsIndexed(ar.tracks, key = { _, t -> t.id }) { i, t ->
            Box(Modifier.padding(horizontal = 10.dp)) { SongRow(t, t.id == currentId, a, { a.play(ids, i) }, subtitle = t.album) }
        }
    }
}

@Composable
fun PlaylistScreen(pl: Playlist, tracks: List<Track?>, currentId: String?, a: Actions, onBack: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onRemoveAt: (Int) -> Unit, bottomPad: Dp = 96.dp) {
    val ok = tracks.filterNotNull()
    val ids = ok.map { it.id }
    LazyColumn(Modifier.fillMaxSize().background(LocalPal.current.bg), contentPadding = PaddingValues(bottom = bottomPad)) {
        item { TopBack(onBack, groupMenu(ids, a) + listOf(MenuItem("Rename", Icons.Rounded.Edit, onRename), MenuItem("Delete playlist", Icons.Rounded.Delete, onDelete))) }
        item { Hero(ok.firstNotNullOfOrNull { it.art }, pl.name, "Playlist", songs(ok.size) + if (ok.isNotEmpty()) " · " + totalTime(ok) else "") }
        if (ok.isEmpty()) item { Empty("Empty playlist", "Add songs from any song's ⋮ menu.") }
        else item { PlayShuffle({ a.play(ids, 0) }, { a.shuffle(ids) }) }
        itemsIndexed(tracks, key = { i, t -> "$i-${t?.id}" }) { i, t ->
            if (t != null) Box(Modifier.padding(horizontal = 10.dp)) {
                SongRow(t, t.id == currentId, a, { a.play(ids, ids.indexOf(t.id)) }, extraMenu = listOf(MenuItem("Remove from playlist", RemoveIcon) { onRemoveAt(i) }))
            }
        }
    }
}

/** A soft drop shadow behind covers, like College WiFi's lifted surfaces (light theme only). */
@Composable
fun Modifier.liftShadow(shape: androidx.compose.ui.graphics.Shape, elevation: Dp = 18.dp): Modifier {
    val p = LocalPal.current
    return if (p.dark) this else this.shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.22f))
}
