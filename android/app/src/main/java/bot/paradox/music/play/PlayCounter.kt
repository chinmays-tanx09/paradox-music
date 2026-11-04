package bot.paradox.music.play

/**
 * Decides when a song counts as played: once it has actually been heard (playing time, not seek position)
 * for half its length or 30 seconds, whichever comes first. Each start of a song counts at most once;
 * a repeat-one loop is a new start. Pure: the caller passes the clock, so it is unit-tested.
 */
class PlayCounter(private val capMs: Long = 30_000, private val fraction: Double = 0.5) {
    var trackId: String? = null; private set
    private var durationMs = 0L
    private var heardMs = 0L
    private var playingSince: Long? = null
    private var counted = false

    /** Heard time needed for this song to count. */
    fun thresholdMs(): Long = if (durationMs > 0) minOf(capMs, (durationMs * fraction).toLong()).coerceAtLeast(1) else capMs

    fun heardMs(now: Long): Long = heardMs + (playingSince?.let { (now - it).coerceAtLeast(0) } ?: 0)

    /** A new song (or the same song again) started. Returns the previous song's id if it just crossed the line. */
    fun begin(id: String?, durationMs: Long, playing: Boolean, now: Long): String? {
        val done = check(now)
        trackId = id; this.durationMs = durationMs.coerceAtLeast(0); heardMs = 0; counted = false
        playingSince = if (playing && id != null) now else null
        return done
    }

    fun setDuration(ms: Long) { if (ms > 0) durationMs = ms }

    /** Playback started or paused. Returns the id to count, if the song just crossed the line. */
    fun setPlaying(playing: Boolean, now: Long): String? {
        val done = check(now)
        if (playing) { if (playingSince == null && trackId != null) playingSince = now }
        else { heardMs = heardMs(now); playingSince = null }
        return done
    }

    /** Call now and then while playing. Returns the id to count exactly once per start. */
    fun check(now: Long): String? {
        val id = trackId ?: return null
        if (counted || heardMs(now) < thresholdMs()) return null
        counted = true
        return id
    }

    /** Milliseconds of playing until this song counts, or null if it already has (or nothing is loaded). */
    fun msUntilCount(now: Long): Long? = if (trackId == null || counted) null else (thresholdMs() - heardMs(now)).coerceAtLeast(0)
}
