package bot.paradox.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bot.paradox.music.data.Album
import bot.paradox.music.data.Artist
import bot.paradox.music.data.Playlist
import bot.paradox.music.data.Track
import bot.paradox.music.data.formatTime

/** Everything a list row can ask for. Screens get one of these; tests pass the no-op default. */
class Actions(
    val play: (ids: List<String>, start: Int) -> Unit = { _, _ -> },
    val shuffle: (ids: List<String>) -> Unit = {},
    val playNext: (ids: List<String>) -> Unit = {},
    val addToQueue: (ids: List<String>) -> Unit = {},
    val addToPlaylist: (ids: List<String>) -> Unit = {},
    val openAlbum: (key: String) -> Unit = {},
    val openArtist: (name: String) -> Unit = {},
    val openPlaylist: (id: Long) -> Unit = {},
    val toast: (String) -> Unit = {},
    val toggleLike: (id: String) -> Unit = {},
    val mixSong: (Track) -> Unit = {},
    val mixArtist: (name: String) -> Unit = {},
)

/** Ids of liked songs, provided once at the top so every row and menu shows the heart without extra plumbing. */
val LocalLikes = compositionLocalOf<Set<String>> { emptySet() }

val MixIcon = Icons.Rounded.Radio

data class MenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun RowMenu(items: List<MenuItem>, tint: androidx.compose.ui.graphics.Color = LocalPal.current.faint) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    Box {
        BareIcon(Icons.Rounded.MoreVert, "More", tint = tint, size = 40.dp, iconSize = 22.dp) { open = true }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(18.dp), containerColor = p.surface) {
            items.forEach { m ->
                DropdownMenuItem(text = { Text(m.label, style = ts(15f, 650, p.text)) }, onClick = { open = false; m.onClick() },
                    leadingIcon = { Icon(m.icon, null, tint = p.muted, modifier = Modifier.size(20.dp)) })
            }
        }
    }
}

fun songMenu(t: Track, a: Actions, extra: List<MenuItem> = emptyList(), liked: Boolean = false) = listOf(
    MenuItem("Play next", Icons.Rounded.PlaylistPlay) { a.playNext(listOf(t.id)); a.toast("Plays next") },
    MenuItem("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) { a.addToQueue(listOf(t.id)); a.toast("Added to queue") },
    MenuItem("Add to playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) { a.addToPlaylist(listOf(t.id)) },
    if (liked) MenuItem("Unlike", Icons.Rounded.Favorite) { a.toggleLike(t.id) } else MenuItem("Like", Icons.Rounded.FavoriteBorder) { a.toggleLike(t.id) },
    MenuItem("Make a mix", MixIcon) { a.mixSong(t) },
    MenuItem("Go to album", Icons.Rounded.Album) { a.openAlbum(t.albumKey) },
    MenuItem("Go to artist", Icons.Rounded.Person) { a.openArtist(t.artist) },
) + extra

/** A song: cover (or track number), title, artist · album, length, and the ⋮ menu. */
@Composable
fun SongRow(t: Track, playing: Boolean, a: Actions, onClick: () -> Unit, number: Int? = null, subtitle: String? = null, extraMenu: List<MenuItem> = emptyList(), trailing: (@Composable () -> Unit)? = null) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(start = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Box(Modifier.size(width = 36.dp, height = 44.dp), contentAlignment = Alignment.Center) {
                if (playing) Icon(Icons.Rounded.GraphicEq, "Playing", tint = p.text, modifier = Modifier.size(20.dp))
                else Text("$number", style = ts(15f, 750, p.faint), textAlign = TextAlign.Center)
            }
        } else Box {
            Cover(t.art, 52.dp, RoundedCornerShape(14.dp))
            if (playing) Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(p.scrim), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.GraphicEq, "Playing", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = ts(15.5f, if (playing) 850 else 750, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(subtitle ?: "${t.artist} · ${t.album}", style = ts(13f, 600, p.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val liked = t.id in LocalLikes.current
        if (liked && trailing == null) Box(Modifier.padding(start = 6.dp).size(30.dp).clip(CircleShape).clickable { a.toggleLike(t.id) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Favorite, "Liked", tint = p.text, modifier = Modifier.size(16.dp))
        }
        if (t.durationMs > 0) Text(formatTime(t.durationMs), style = ts(12.5f, 650, p.faint), modifier = Modifier.padding(start = if (liked && trailing == null) 2.dp else 8.dp))
        if (trailing != null) trailing() else RowMenu(songMenu(t, a, extraMenu, liked))
    }
}

@Composable
fun AlbumTile(al: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Column(modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) { Cover(al.art, 180.dp, RoundedCornerShape(22.dp), fill = true) }
        Spacer(Modifier.height(10.dp))
        Text(al.title, style = ts(15f, 750, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(al.artist, style = ts(13f, 600, p.muted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
fun ArtistRow(ar: Artist, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ar.art != null) Cover(ar.art, 52.dp, CircleShape)
        else Box(Modifier.size(52.dp).clip(CircleShape).background(p.surface2), contentAlignment = Alignment.Center) {
            Text(ar.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪", style = ts(19f, 850, p.muted))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(ar.name, style = ts(15.5f, 750, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(countLine(ar.tracks.size, ar.albums.size), style = ts(13f, 600, p.muted))
        }
    }
}

@Composable
fun PlaylistRow(pl: Playlist, art: String?, onClick: () -> Unit, menu: List<MenuItem>) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(art, 52.dp, RoundedCornerShape(14.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(pl.name, style = ts(15.5f, 750, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(songs(pl.trackIds.size), style = ts(13f, 600, p.muted))
        }
        RowMenu(menu)
    }
}

/** A queue row: tap to jump, ✕ to remove. */
@Composable
fun QueueRow(t: Track, current: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    SongRow(t, current, Actions(), onClick, subtitle = t.artist, trailing = {
        if (onRemove != null) BareIcon(Icons.Rounded.Close, "Remove", tint = LocalPal.current.faint, size = 40.dp, iconSize = 20.dp, onClick = onRemove)
        else Spacer(Modifier.width(40.dp))
    })
}

val RemoveIcon = Icons.Rounded.RemoveCircleOutline

fun songs(n: Int) = if (n == 1) "1 song" else "$n songs"
fun countLine(songs: Int, albums: Int) = songs(songs) + " · " + (if (albums == 1) "1 album" else "$albums albums")
fun totalTime(ts: List<Track>): String {
    val m = ts.sumOf { it.durationMs } / 60000
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}
