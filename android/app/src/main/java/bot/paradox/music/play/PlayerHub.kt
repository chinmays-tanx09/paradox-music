package bot.paradox.music.play

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import bot.paradox.music.data.QueueSnapshot
import bot.paradox.music.data.Repo
import bot.paradox.music.data.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the UI shows about playback. */
data class PlayerState(
    val queue: Queue = Queue(),
    val playing: Boolean = false,
    val durationMs: Long = 0,
    val repeat: Repeat = Repeat.OFF,
    val connected: Boolean = false,
) {
    val currentTrackId: String? get() = queue.current?.trackId
}

/**
 * The app's one handle on the player. It talks to [PlaybackService] through a MediaController,
 * and keeps the queue logic (shuffle order, play next) on this side so "Up next" is always exact.
 */
class PlayerHub(private val ctx: Context, private val repo: Repo) {
    private val logic = QueueLogic()
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    /** Shuffle flag + the order to return to; shared with the service so it is saved with the queue. */
    @Volatile var shuffled = false; private set
    @Volatile var original: List<QEntry> = emptyList(); private set

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    // ------------------------------------------------------------------ connection
    fun connect() {
        if (future != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: run { future = null; return@addListener }
            controller = c
            c.addListener(listener)
            sync()
            pending.toList().forEach { it(c) }; pending.clear()
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun disconnect() {
        future?.let { MediaController.releaseFuture(it) }
        future = null; controller = null
        _state.value = _state.value.copy(connected = false)
    }

    private fun with(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else { pending.add(block); connect() }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
    }

    /** Rebuilds [state] from the controller (the player is the truth for what is loaded and where). */
    fun sync() {
        val c = controller ?: return
        val entries = (0 until c.mediaItemCount).mapNotNull { QEntry.parse(c.getMediaItemAt(it).mediaId) }
        entries.maxOfOrNull { it.uid }?.let { logic.seedUid(it) }
        val alive = entries.toSet()
        val orig = original.filter { it in alive }.let { o -> o + entries.filter { it !in o.toSet() } }
        original = orig
        val q = Queue(entries, c.currentMediaItemIndex.coerceIn(0, maxOf(0, entries.lastIndex)), shuffled, orig)
        _state.value = PlayerState(
            queue = q, playing = c.isPlaying,
            durationMs = c.duration.takeIf { it > 0 } ?: repo.index.value.byId[q.current?.trackId]?.durationMs ?: 0,
            repeat = when (c.repeatMode) { Player.REPEAT_MODE_ALL -> Repeat.ALL; Player.REPEAT_MODE_ONE -> Repeat.ONE; else -> Repeat.OFF },
            connected = true,
        )
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0
    fun bufferedMs(): Long = controller?.bufferedPosition ?: 0

    /** Called by the service when it restores a saved queue. */
    fun adopt(s: QueueSnapshot) {
        shuffled = s.shuffled
        original = s.original.mapNotNull { QEntry.parse(it) }
    }

    // ------------------------------------------------------------------ media items
    fun mediaItem(e: QEntry): MediaItem? {
        val t = repo.index.value.byId[e.trackId] ?: return null
        return buildItem(e.mediaId, t, repo.artFile(t.art)?.takeIf { it.exists() }?.let { Uri.fromFile(it) })
    }

    // ------------------------------------------------------------------ actions
    fun playTracks(ids: List<String>, start: Int = 0, shuffle: Boolean = shuffled) {
        if (ids.isEmpty()) return
        apply(logic.playList(ids, start, shuffle), restart = true)
    }

    fun shuffleAll(ids: List<String>) { if (ids.isNotEmpty()) apply(logic.shuffleAll(ids), restart = true) }

    private fun apply(q: Queue, restart: Boolean) {
        shuffled = q.shuffled; original = q.original
        val items = q.entries.mapNotNull { mediaItem(it) }
        if (items.isEmpty()) return
        with { c ->
            if (restart) {
                c.setMediaItems(items, q.index.coerceAtMost(items.lastIndex), 0)
                c.prepare(); c.play()
            }
        }
    }

    fun toggle() = with { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }
    fun next() = with { it.seekToNext() }
    fun previous() = with { it.seekToPrevious() }
    fun seekTo(ms: Long) = with { it.seekTo(ms) }
    fun jumpTo(index: Int) = with { c -> if (index in 0 until c.mediaItemCount) { c.seekTo(index, 0); c.play() } }

    fun cycleRepeat() = with { c ->
        val r = _state.value.repeat.next()
        c.repeatMode = when (r) { Repeat.OFF -> Player.REPEAT_MODE_OFF; Repeat.ALL -> Player.REPEAT_MODE_ALL; Repeat.ONE -> Player.REPEAT_MODE_ONE }
    }

    /** Reorders what is ahead without interrupting the current song. */
    fun toggleShuffle() = with { c ->
        val cur = _state.value.queue
        val q = logic.setShuffle(cur, !cur.shuffled)
        shuffled = q.shuffled; original = q.original
        val ci = c.currentMediaItemIndex
        if (c.mediaItemCount > 0 && q.current == cur.current) {
            if (ci + 1 < c.mediaItemCount) c.removeMediaItems(ci + 1, c.mediaItemCount)
            if (ci > 0) c.removeMediaItems(0, ci)
            val before = q.entries.take(q.index).mapNotNull { mediaItem(it) }
            val after = q.entries.drop(q.index + 1).mapNotNull { mediaItem(it) }
            if (before.isNotEmpty()) c.addMediaItems(0, before)
            if (after.isNotEmpty()) c.addMediaItems(after)
        }
        sync()
    }

    fun playNext(ids: List<String>) = with { c ->
        val cur = _state.value.queue
        if (c.mediaItemCount == 0) { playTracks(ids); return@with }
        val q = logic.playNext(cur, ids)
        original = q.original
        val items = q.entries.subList(cur.index + 1, cur.index + 1 + ids.size).mapNotNull { mediaItem(it) }
        c.addMediaItems(c.currentMediaItemIndex + 1, items)
    }

    fun addToQueue(ids: List<String>) = with { c ->
        if (c.mediaItemCount == 0) { playTracks(ids); return@with }
        val q = logic.addToQueue(_state.value.queue, ids)
        original = q.original
        c.addMediaItems(q.entries.takeLast(ids.size).mapNotNull { mediaItem(it) })
    }

    fun removeAt(index: Int) = with { c -> if (index in 0 until c.mediaItemCount) c.removeMediaItem(index) }
    fun move(from: Int, to: Int) = with { c -> if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to) }
    fun clearUpNext() = with { c -> val i = c.currentMediaItemIndex; if (i + 1 < c.mediaItemCount) c.removeMediaItems(i + 1, c.mediaItemCount) }

    companion object {
        fun buildItem(mediaId: String, t: Track, art: Uri?): MediaItem {
            val md = MediaMetadata.Builder()
                .setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).setAlbumArtist(t.albumArtist)
                .setGenre(t.genre).setTrackNumber(t.track).setDiscNumber(t.disc).setReleaseYear(t.year)
                .setArtworkUri(art).setIsPlayable(true).setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply { putLong("duration", t.durationMs) })
                .build()
            val uri = Uri.parse(t.id)
            return MediaItem.Builder().setMediaId(mediaId).setUri(uri).setMediaMetadata(md)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build()).build()
        }
    }
}
