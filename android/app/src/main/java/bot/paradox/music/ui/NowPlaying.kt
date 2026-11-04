package bot.paradox.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bot.paradox.music.data.Track
import bot.paradox.music.data.formatTime
import bot.paradox.music.play.Repeat

class PlayerControls(
    val toggle: () -> Unit = {}, val next: () -> Unit = {}, val previous: () -> Unit = {},
    val seek: (Long) -> Unit = {}, val shuffle: () -> Unit = {}, val repeat: () -> Unit = {},
    val jump: (Int) -> Unit = {}, val remove: (Int) -> Unit = {}, val clearUpNext: () -> Unit = {},
)

/** The bar above the bottom edge: cover, title, artist, play/pause, next, and a hairline of progress. */
@Composable
fun MiniPlayer(t: Track, playing: Boolean, progress: Float, c: PlayerControls, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().liftShadow(shape, 14.dp).clip(shape).background(if (p.dark) p.surface2 else p.surface)
            .clickable(onClick = onOpen)
            .pointerInput(Unit) { detectVerticalDragGestures { _, d -> if (d < -12) onOpen() } }
    ) {
        Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(t.art, 46.dp, RoundedCornerShape(13.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = ts(15f, 800, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, style = ts(12.5f, 650, p.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BareIcon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", size = 46.dp, iconSize = 30.dp, onClick = c.toggle)
            BareIcon(Icons.Rounded.SkipNext, "Next", size = 46.dp, iconSize = 28.dp, onClick = c.next)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(p.hairline)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(p.text))
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** Full-screen now playing: big cover, seek bar with times, shuffle / previous / play / next / repeat, and the queue. */
@Composable
fun NowPlayingScreen(
    t: Track, playing: Boolean, positionMs: Long, durationMs: Long, shuffled: Boolean, repeat: Repeat,
    upNext: Track?, from: String, c: PlayerControls, onClose: () -> Unit, onQueue: () -> Unit,
    liked: Boolean = false, onLike: (() -> Unit)? = null,
) {
    val p = LocalPal.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = durationMs.coerceAtLeast(1)
    val frac = dragging ?: (positionMs.toFloat() / dur).coerceIn(0f, 1f)
    Column(
        Modifier.fillMaxSize().background(p.bg).padding(top = bars.calculateTopPadding(), bottom = bars.calculateBottomPadding())
            .pointerInput(Unit) { detectVerticalDragGestures { _, d -> if (d > 30) onClose() } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BareIcon(Icons.Rounded.KeyboardArrowDown, "Close", iconSize = 30.dp, onClick = onClose)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PLAYING FROM", style = ts(11f, 800, p.faint, 0.12f))
                Text(from, style = ts(13.5f, 750, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BareIcon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", onClick = onQueue)
        }
        Spacer(Modifier.weight(0.5f))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 30.dp)) {
            val side = minOf(maxWidth, 420.dp)
            val shape = RoundedCornerShape(30.dp)
            Box(Modifier.align(Alignment.Center).size(side).liftShadow(shape, 28.dp)) { Cover(t.art, side, shape) }
        }
        Spacer(Modifier.weight(0.6f))
        Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = ts(24f, 800, p.text, -0.015f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(t.artist + " · " + t.album, style = ts(15.5f, 650, p.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onLike != null) {
                    Spacer(Modifier.width(8.dp))
                    BareIcon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Unlike" else "Like",
                        tint = if (liked) p.text else p.muted, size = 48.dp, iconSize = 28.dp, onClick = onLike)
                }
            }
            Spacer(Modifier.height(14.dp))
            SeekBar(frac, onDrag = { dragging = it }, onDone = { dragging?.let { c.seek((it * dur).toLong()) }; dragging = null })
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime((frac * dur).toLong()), style = ts(12.5f, 700, p.faint))
                Spacer(Modifier.weight(1f))
                Text(if (durationMs > 0) "-" + formatTime(durationMs - (frac * dur).toLong()) else "--:--", style = ts(12.5f, 700, p.faint))
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Toggle(Icons.Rounded.Shuffle, "Shuffle", shuffled, c.shuffle)
            BareIcon(Icons.Rounded.SkipPrevious, "Previous", size = 60.dp, iconSize = 40.dp, onClick = c.previous)
            Box(Modifier.size(78.dp).clip(CircleShape).background(p.primary).clickable(onClick = c.toggle), contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = p.onPrimary, modifier = Modifier.size(42.dp))
            }
            BareIcon(Icons.Rounded.SkipNext, "Next", size = 60.dp, iconSize = 40.dp, onClick = c.next)
            Toggle(if (repeat == Repeat.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                when (repeat) { Repeat.OFF -> "Repeat off"; Repeat.ALL -> "Repeat all"; Repeat.ONE -> "Repeat one" }, repeat != Repeat.OFF, c.repeat)
        }
        Spacer(Modifier.weight(0.5f))
        Row(
            Modifier.padding(horizontal = 22.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface2).clickable(onClick = onQueue)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("UP NEXT", style = ts(11f, 800, p.faint, 0.12f))
            Spacer(Modifier.width(12.dp))
            Text(upNext?.let { it.title + " · " + it.artist } ?: when (repeat) { Repeat.OFF -> "End of queue"; Repeat.ONE -> "This song again"; Repeat.ALL -> "Starts over" }, style = ts(14f, 700, p.text),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** A thin seek bar in the College WiFi style: 4 dp track, round thumb that grows while dragging. Tap or drag to seek. */
@Composable
fun SeekBar(fraction: Float, onDrag: (Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    var active by remember { mutableStateOf(false) }
    Canvas(
        modifier.fillMaxWidth().height(30.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { v -> onDrag(v.coerceIn(0f, 1f)); onDone(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = { o -> active = true; onDrag((o.x / size.width).coerceIn(0f, 1f)); tryAwaitRelease(); active = false; onDone() })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> active = true; onDrag((o.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { active = false; onDone() }, onDragCancel = { active = false; onDone() },
                ) { change, _ -> onDrag((change.position.x / size.width).coerceIn(0f, 1f)) }
            },
    ) {
        val y = size.height / 2
        val r = (if (active) 9 else 7).dp.toPx()
        val sw = 4.dp.toPx()
        val x = (size.width - 2 * r) * fraction.coerceIn(0f, 1f) + r
        drawLine(p.hairline, Offset(r, y), Offset(size.width - r, y), sw, StrokeCap.Round)
        drawLine(p.text, Offset(r, y), Offset(x, y), sw, StrokeCap.Round)
        drawCircle(p.text, r, Offset(x, y))
    }
}

/** A shuffle/repeat button: lit with a dot under it when on. */
@Composable
private fun Toggle(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Column(Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, desc, tint = if (on) p.text else p.faint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (on) p.text else Color.Transparent))
    }
}

/** The queue: what is playing, then up next. Tap to jump, ✕ to remove. */
@Composable
fun QueueScreen(tracks: List<Track?>, index: Int, c: PlayerControls, onClose: () -> Unit) {
    val p = LocalPal.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    Column(Modifier.fillMaxSize().background(p.bg).padding(top = bars.calculateTopPadding())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BareIcon(Icons.Rounded.KeyboardArrowDown, "Close", iconSize = 30.dp, onClick = onClose)
            Text("Queue", style = ts(22f, 800, p.text), modifier = Modifier.weight(1f).padding(start = 4.dp))
            if (index + 1 < tracks.size) Text("Clear", style = ts(14.5f, 750, p.muted), modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = c.clearUpNext).padding(12.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 10.dp, bottom = bars.calculateBottomPadding() + 16.dp)) {
            itemsIndexed(tracks, key = { i, t -> "$i${t?.id}" }) { i, t ->
                if (i == index) SectionLabel("Now playing", Modifier.padding(start = 6.dp))
                if (i == index + 1) SectionLabel("Up next", Modifier.padding(start = 6.dp))
                if (i < index && i == 0) SectionLabel("Played", Modifier.padding(start = 6.dp))
                if (t != null) {
                    Box(Modifier.background(Color.Transparent).then(if (i < index) Modifier.padding(0.dp) else Modifier)) {
                        QueueRow(t, i == index, { c.jump(i) }, if (i == index) null else ({ c.remove(i) }))
                    }
                }
            }
            if (tracks.isEmpty()) item { Empty("Nothing queued", "Play a song, or use Play next / Add to queue.") }
        }
    }
}
