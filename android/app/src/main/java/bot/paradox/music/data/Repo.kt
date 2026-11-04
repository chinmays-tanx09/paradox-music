package bot.paradox.music.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import bot.paradox.music.scan.AndroidArtStore
import bot.paradox.music.scan.RetrieverFallback
import bot.paradox.music.scan.SafNode
import bot.paradox.music.scan.ScanProgress
import bot.paradox.music.scan.Scanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The saved play queue, so the last song and position come back after a restart. */
data class QueueSnapshot(val mediaIds: List<String>, val index: Int, val positionMs: Long, val shuffled: Boolean, val original: List<String>, val repeat: Int)

data class ScanState(val progress: ScanProgress?, val running: Boolean)

/** Library, playlists and the saved queue, all stored as small JSON files in app-private storage. */
class Repo(private val ctx: Context, val prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir = ctx.filesDir
    val artDir = File(dir, "art")
    private val libFile = File(dir, "library.json")
    private val plFile = File(dir, "playlists.json")
    private val queueFile = File(dir, "queue.json")
    private val statsFile = File(dir, "stats.json")
    private val smartFile = File(dir, "smart.json")

    private val _index = MutableStateFlow(LibraryIndex(emptyList()))
    val index: StateFlow<LibraryIndex> = _index
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded
    private val _scan = MutableStateFlow(ScanState(null, false))
    val scan: StateFlow<ScanState> = _scan
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists
    private val _stats = MutableStateFlow<Map<String, TrackStats>>(emptyMap())
    /** Play counts, last played and likes, by track id. Local only. */
    val stats: StateFlow<Map<String, TrackStats>> = _stats
    private val _smart = MutableStateFlow<List<SmartPlaylist>>(emptyList())
    val smart: StateFlow<List<SmartPlaylist>> = _smart
    private val _folders = MutableStateFlow(prefs.folders)
    val folders: StateFlow<List<MusicFolder>> = _folders
    /** Folders whose access was lost (the folder moved, or the grant was revoked). */
    private val _lost = MutableStateFlow<Set<String>>(emptySet())
    val lost: StateFlow<Set<String>> = _lost

    private var scanJob: Job? = null
    private val lock = Any()

    fun artFile(name: String?): File? = name?.let { File(artDir, it) }

    /** Loads library + playlists from disk once. Safe to call from any thread. */
    fun ensureLoaded() {
        synchronized(lock) {
            if (_loaded.value) return
            val tracks = if (libFile.exists()) Track.listFromJson(libFile.readText()) else emptyList()
            _index.value = LibraryIndex(tracks)
            _playlists.value = if (plFile.exists()) Playlists.fromJson(plFile.readText()) else emptyList()
            _stats.value = if (statsFile.exists()) Stats.fromJson(statsFile.readText()) else emptyMap()
            _smart.value = if (smartFile.exists()) SmartEngine.fromJson(smartFile.readText()) else emptyList()
            _loaded.value = true
        }
    }

    fun loadAsync(then: () -> Unit = {}) = scope.launch { ensureLoaded(); then() }

    // ------------------------------------------------------------------ folders
    fun takePermission(uri: Uri) = runCatching {
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.isSuccess

    private fun releasePermission(uri: String) = runCatching {
        ctx.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun folderFor(uri: Uri) = MusicFolder(uri.toString(), SafNode.label(uri))

    private fun setFolders(l: List<MusicFolder>) { prefs.folders = l; _folders.value = l }

    fun addFolder(uri: Uri): Boolean {
        if (!takePermission(uri)) return false
        if (_folders.value.none { it.uri == uri.toString() }) setFolders(_folders.value + folderFor(uri))
        return true
    }

    /** "Change folder": the new folder replaces all the others. */
    fun replaceFolders(uri: Uri): Boolean {
        if (!takePermission(uri)) return false
        _folders.value.filter { it.uri != uri.toString() }.forEach { releasePermission(it.uri) }
        setFolders(listOf(folderFor(uri)))
        return true
    }

    fun removeFolder(f: MusicFolder) {
        releasePermission(f.uri)
        setFolders(_folders.value.filter { it.uri != f.uri })
        rescan()
    }

    // ------------------------------------------------------------------ scanning
    fun rescan(onDone: () -> Unit = {}) {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            ensureLoaded()
            _scan.value = ScanState(null, true)
            try {
                val roots = ArrayList<Pair<String, bot.paradox.music.scan.Node>>()
                val lost = HashSet<String>()
                for (f in _folders.value) {
                    val r = SafNode.root(ctx, Uri.parse(f.uri))
                    if (r == null) lost.add(f.uri) else roots.add(f.uri to r)
                }
                _lost.value = lost
                val previous = _index.value.tracks
                val scanner = Scanner(AndroidArtStore(artDir), RetrieverFallback(ctx))
                var tracks = scanner.scan(roots, previous, onProgress = { _scan.value = ScanState(it, true) })
                // Keep songs from folders we temporarily cannot reach, instead of wiping them.
                if (lost.isNotEmpty()) tracks = tracks + previous.filter { it.root in lost }
                save(libFile, Track.listToJson(tracks))
                _index.value = LibraryIndex(tracks)
                prefs.lastScan = System.currentTimeMillis()
                cleanArt(tracks)
            } finally {
                _scan.value = ScanState(_scan.value.progress, false)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    private fun cleanArt(tracks: List<Track>) {
        val used = tracks.mapNotNull { it.art }.toSet()
        artDir.listFiles()?.forEach { if (it.name !in used) it.delete() }
    }

    // ------------------------------------------------------------------ playlists
    private fun setPlaylists(l: List<Playlist>) { _playlists.value = l; scope.launch { save(plFile, Playlists.toJson(l)) } }
    fun createPlaylist(name: String, songs: List<String> = emptyList()): Playlist {
        val (all, p) = Playlists.create(_playlists.value, name, System.currentTimeMillis())
        setPlaylists(if (songs.isEmpty()) all else Playlists.add(all, p.id, songs))
        return p
    }
    fun renamePlaylist(id: Long, name: String) = setPlaylists(Playlists.rename(_playlists.value, id, name))
    fun deletePlaylist(id: Long) = setPlaylists(Playlists.delete(_playlists.value, id))
    fun addToPlaylist(id: Long, songs: List<String>) = setPlaylists(Playlists.add(_playlists.value, id, songs))
    fun removeFromPlaylist(id: Long, index: Int) = setPlaylists(Playlists.removeAt(_playlists.value, id, index))
    fun movePlaylistSong(id: Long, from: Int, to: Int) = setPlaylists(Playlists.move(_playlists.value, id, from, to))

    // ------------------------------------------------------------------ play counts and likes
    private fun setStats(f: (Map<String, TrackStats>) -> Map<String, TrackStats>) {
        synchronized(_stats) { _stats.value = f(_stats.value) }
        scope.launch { synchronized(statsFile) { save(statsFile, Stats.toJson(_stats.value)) } }
    }
    fun recordPlay(id: String, now: Long = System.currentTimeMillis()) { ensureLoaded(); setStats { Stats.recordPlay(it, id, now) } }
    fun isLiked(id: String) = _stats.value[id]?.liked == true
    fun setLiked(id: String, liked: Boolean) = setStats { Stats.setLiked(it, id, liked, System.currentTimeMillis()) }
    fun toggleLike(id: String): Boolean { val n = !isLiked(id); setLiked(id, n); return n }

    // ------------------------------------------------------------------ smart playlists
    private fun setSmart(l: List<SmartPlaylist>) { _smart.value = l; scope.launch { synchronized(smartFile) { save(smartFile, SmartEngine.toJson(_smart.value)) } } }
    fun createSmart(name: String, rule: SmartRule): SmartPlaylist {
        val (all, p) = SmartEngine.create(_smart.value, name, rule, System.currentTimeMillis())
        setSmart(all); return p
    }
    fun updateSmart(id: Long, name: String, rule: SmartRule) = setSmart(SmartEngine.update(_smart.value, id, name, rule))
    fun deleteSmart(id: Long) = setSmart(SmartEngine.delete(_smart.value, id))

    // ------------------------------------------------------------------ queue snapshot
    fun saveQueue(s: QueueSnapshot) {
        val o = JSONObject().put("ids", JSONArray(s.mediaIds)).put("i", s.index).put("pos", s.positionMs)
            .put("sh", s.shuffled).put("orig", JSONArray(s.original)).put("rep", s.repeat)
        save(queueFile, o.toString())
    }

    fun loadQueue(): QueueSnapshot? = runCatching {
        if (!queueFile.exists()) return null
        val o = JSONObject(queueFile.readText())
        fun arr(k: String) = o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }
        QueueSnapshot(arr("ids"), o.optInt("i"), o.optLong("pos"), o.optBoolean("sh"), arr("orig"), o.optInt("rep"))
    }.getOrNull()

    private fun save(f: File, text: String) = synchronized(f.absolutePath.intern()) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(f)
    }
}
