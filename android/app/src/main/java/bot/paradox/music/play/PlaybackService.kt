package bot.paradox.music.play

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import bot.paradox.music.MainActivity
import bot.paradox.music.MusicApp
import bot.paradox.music.R
import bot.paradox.music.data.QueueSnapshot
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

/**
 * Plays in the background with a media notification, lock-screen and Bluetooth/headset controls.
 * ExoPlayer handles audio focus (pauses for calls, ducks for navigation) and pauses when headphones are unplugged.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val app get() = application as MusicApp
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var errorsInARow = 0
    private val counter = PlayCounter()
    private fun clock() = android.os.SystemClock.elapsedRealtime()

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(listener)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(open).setCallback(callback).build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_music) })
        restore()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Restores the last queue and position, paused, so the mini player shows where you left off. */
    private fun restore() {
        io.execute {
            app.repo.ensureLoaded()
            val snap = app.repo.loadQueue() ?: return@execute
            val items = resolveSnapshot(snap) ?: return@execute
            main.post {
                if (player.mediaItemCount != 0) return@post
                app.hub.adopt(snap)
                player.repeatMode = snap.repeat
                player.setMediaItems(items.first, items.second, snap.positionMs)
                player.prepare()
            }
        }
    }

    private fun resolveSnapshot(snap: QueueSnapshot): Pair<List<MediaItem>, Int>? {
        val items = ArrayList<MediaItem>(); var index = 0
        snap.mediaIds.forEachIndexed { i, id ->
            val item = QEntry.parse(id)?.let { app.hub.mediaItem(it) } ?: return@forEachIndexed
            if (i == snap.index) index = items.size
            items.add(item)
        }
        return if (items.isEmpty()) null else items to index
    }

    private val callback = object : MediaSession.Callback {
        /** Items from a controller arrive without their file URI; look each one up again in the library. */
        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
            app.repo.ensureLoaded()
            val out = mediaItems.mapNotNull { m ->
                if (m.localConfiguration != null) m
                else QEntry.parse(m.mediaId)?.let { app.hub.mediaItem(it) }
                    ?: m.requestMetadata.mediaUri?.let { uri -> m.buildUpon().setUri(uri).build() }
            }.toMutableList()
            return Futures.immediateFuture(out)
        }

        /** A headset / Bluetooth "play" after the app was closed: pick up the saved queue. */
        override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            app.repo.ensureLoaded()
            val snap = app.repo.loadQueue()
            val r = snap?.let { resolveSnapshot(it) }
            return if (snap != null && r != null) {
                app.hub.adopt(snap)
                Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(r.first, r.second, snap.positionMs))
            } else Futures.immediateFailedFuture(UnsupportedOperationException("nothing to resume"))
        }
    }

    private val saver = object : Runnable {
        override fun run() {
            save()
            if (player.isPlaying) main.postDelayed(this, 5000)
        }
    }

    /** Counts a play locally (play count + last played) once a song has been heard for half its length or 30 s. */
    private fun counted(id: String?) { if (id != null) io.execute { runCatching { app.repo.recordPlay(id) } } }

    private fun currentTrackId(): String? = player.currentMediaItem?.mediaId?.let { QEntry.parse(it)?.trackId }

    private fun libraryDuration(id: String?): Long = id?.let { app.repo.index.value.byId[it]?.durationMs } ?: 0

    private val counterTick = object : Runnable {
        override fun run() {
            if (player.duration > 0) counter.setDuration(player.duration)
            counted(counter.check(clock()))
            val wait = counter.msUntilCount(clock())
            if (player.isPlaying && wait != null) main.postDelayed(this, (wait + 50).coerceIn(500, 5000))
        }
    }

    private fun startCounterTick() { main.removeCallbacks(counterTick); main.postDelayed(counterTick, 500) }

    private val listener = object : Player.Listener {
        /** Every start of a song, including a repeat-one loop, is a new chance to count. */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.let { QEntry.parse(it)?.trackId }
            counted(counter.begin(id, libraryDuration(id), player.isPlaying, clock()))
            startCounterTick()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (counter.trackId == null && isPlaying) { val id = currentTrackId(); counter.begin(id, libraryDuration(id), false, clock()) }
            counted(counter.setPlaying(isPlaying, clock()))
            if (isPlaying) startCounterTick() else main.removeCallbacks(counterTick)
        }

        override fun onEvents(p: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)) {
                main.removeCallbacks(saver); main.postDelayed(saver, 400)
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && p.isPlaying) errorsInARow = 0
        }

        /** A missing or unreadable file: skip to the next one instead of stopping the queue. */
        override fun onPlayerError(error: PlaybackException) {
            errorsInARow++
            if (errorsInARow <= player.mediaItemCount && player.hasNextMediaItem()) {
                player.seekToNextMediaItem(); player.prepare(); player.play()
            }
        }
    }

    private fun save() {
        if (player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val snap = QueueSnapshot(ids, player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0),
            app.hub.shuffled, app.hub.original.map { it.mediaId }, player.repeatMode)
        io.execute { runCatching { app.repo.saveQueue(snap) } }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        save()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        save()
        main.removeCallbacksAndMessages(null)
        session?.run { player.release(); release() }
        session = null
        io.shutdown()
        super.onDestroy()
    }
}
