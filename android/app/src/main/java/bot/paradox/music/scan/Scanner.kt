package bot.paradox.music.scan

import bot.paradox.music.data.Track
import java.io.File
import java.security.MessageDigest

/** A file or folder in a music tree: SAF documents on the phone, java.io.File in tests. */
interface Node {
    val id: String
    val name: String
    val isDir: Boolean
    val size: Long
    val modified: Long
    fun children(): List<Node>
    /** Opens the file for tag reading; the caller closes what it returns. */
    fun <T> open(block: (Source) -> T): T?
}

class FileNode(val file: File) : Node {
    override val id: String get() = file.toURI().toString()
    override val name: String get() = file.name
    override val isDir: Boolean get() = file.isDirectory
    override val size: Long get() = file.length()
    override val modified: Long get() = file.lastModified()
    override fun children(): List<Node> = file.listFiles()?.map { FileNode(it) } ?: emptyList()
    override fun <T> open(block: (Source) -> T): T? = runCatching {
        java.io.RandomAccessFile(file, "r").use { raf -> block(FileChannelSource(raf.channel)) }
    }.getOrNull()
}

/** Where covers go. On the phone it downsizes to a JPEG; in tests it writes the bytes as they are. */
interface ArtStore {
    /** Stores [bytes] (any image) and returns the art name, or null if it is not a picture. */
    fun put(bytes: ByteArray): String?
    fun has(name: String): Boolean
}

class RawArtStore(private val dir: File) : ArtStore {
    override fun put(bytes: ByteArray): String? {
        dir.mkdirs()
        val name = Scanner.hash(bytes) + ".img"
        val f = File(dir, name)
        if (!f.exists()) f.writeBytes(bytes)
        return name
    }
    override fun has(name: String) = File(dir, name).exists()
}

/** Optional per-device fallback (Android's MediaMetadataRetriever) for files the built-in reader can't time. */
fun interface TagFallback { fun read(node: Node): Tags? }

data class ScanProgress(val foldersSeen: Int, val filesFound: Int, val tagsRead: Int)

class Scanner(
    private val art: ArtStore,
    private val fallback: TagFallback? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        val AUDIO_EXT = setOf("mp3", "m4a", "aac", "mp4", "flac", "wav", "ogg", "oga", "opus")
        private val COVER_NAMES = listOf("cover", "folder", "front", "album", "albumart", "albumartsmall")
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")

        fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
        fun isAudio(name: String) = !name.startsWith("._") && ext(name) in AUDIO_EXT

        fun hash(b: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(b).joinToString("") { "%02x".format(it) }.take(20)

        /**
         * Title and artist from a file name when a file has no tags:
         * "01 - Artist - Title.mp3", "Artist - Title.flac", "03. Title.m4a", "Title.wav".
         */
        fun fromFileName(fileName: String): Triple<String, String?, Int?> {
            var base = fileName.substringBeforeLast('.').replace('_', ' ').trim()
            var track: Int? = null
            Regex("^(\\d{1,3})(?:\\s*[-.)]\\s*|\\s+)(.+)$").find(base)?.let { m ->
                track = m.groupValues[1].toInt(); base = m.groupValues[2].trim()
            }
            val parts = base.split(Regex("\\s+[-–—]\\s+"), limit = 2)
            return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank())
                Triple(parts[1].trim(), parts[0].trim(), track) else Triple(base.ifBlank { fileName }, null, track)
        }
    }

    /**
     * Walks every root recursively and returns the library. Unchanged files (same size and date) reuse
     * their previous entry, so a rescan of a big library only reads new or edited files.
     * Hidden folders and folders holding a .nomedia file are skipped.
     */
    fun scan(roots: List<Pair<String, Node>>, previous: List<Track>, onProgress: (ScanProgress) -> Unit = {}, cancelled: () -> Boolean = { false }): List<Track> {
        val prev = previous.associateBy { it.id }
        val out = ArrayList<Track>()
        val seen = HashSet<String>()
        var folders = 0; var files = 0; var read = 0
        for ((rootId, root) in roots) {
            val stack = ArrayDeque<Pair<Node, String>>()
            stack.add(root to "")
            while (stack.isNotEmpty()) {
                if (cancelled()) return out
                val (dir, rel) = stack.removeLast()
                val kids = runCatching { dir.children() }.getOrElse { emptyList() }
                if (rel.isNotEmpty() && kids.any { !it.isDir && it.name.equals(".nomedia", true) }) continue
                folders++
                var folderArt: String? = null
                var folderArtLooked = false
                fun folderCover(): String? {
                    if (folderArtLooked) return folderArt
                    folderArtLooked = true
                    val img = kids.filter { !it.isDir && ext(it.name) in IMAGE_EXT && it.name.substringBeforeLast('.').lowercase() in COVER_NAMES && it.size in 1..(8L shl 20) }
                        .minByOrNull { COVER_NAMES.indexOf(it.name.substringBeforeLast('.').lowercase()) }
                    folderArt = img?.open { s -> ByteArray(s.length.toInt()).also { s.read(0, it, 0, it.size) } }?.let { art.put(it) }
                    return folderArt
                }
                for (k in kids.sortedBy { it.name.lowercase() }) {
                    if (k.name.startsWith(".")) continue
                    if (k.isDir) { stack.add(k to (if (rel.isEmpty()) k.name else "$rel/${k.name}")); continue }
                    if (!isAudio(k.name) || k.size <= 0) continue
                    if (!seen.add(k.id)) continue
                    files++
                    val old = prev[k.id]
                    if (old != null && old.size == k.size && old.modified == k.modified && (old.art == null || art.has(old.art))) {
                        out.add(if (old.art == null) old.copy(art = folderCover()) else old)
                    } else {
                        read++
                        out.add(readTrack(k, rel, rootId, old?.addedAt ?: now(), ::folderCover))
                    }
                    if (files % 25 == 0) onProgress(ScanProgress(folders, files, read))
                }
            }
        }
        onProgress(ScanProgress(folders, files, read))
        return out
    }

    fun readTrack(node: Node, folder: String, root: String, addedAt: Long, folderCover: () -> String? = { null }): Track {
        val tags = node.open { TagReader.read(it, ext(node.name)) } ?: Tags()
        if (tags.durationMs <= 0 || (tags.title == null && tags.artist == null)) fallback?.read(node)?.let { tags.fillFrom(it) }
        val (fnTitle, fnArtist, fnTrack) = fromFileName(node.name)
        val folderName = folder.substringAfterLast('/').ifBlank { null }
        val artName = tags.picture?.let { runCatching { art.put(it) }.getOrNull() } ?: folderCover()
        return Track(
            id = node.id,
            title = tags.title ?: fnTitle,
            artist = tags.artist ?: fnArtist ?: tags.albumArtist ?: Track.UNKNOWN_ARTIST,
            album = tags.album ?: folderName ?: Track.UNKNOWN_ALBUM,
            albumArtist = tags.albumArtist,
            genre = tags.genre, year = tags.year,
            track = tags.track ?: fnTrack, disc = tags.disc,
            durationMs = tags.durationMs,
            art = artName,
            folder = folder, fileName = node.name, size = node.size, modified = node.modified,
            addedAt = addedAt, root = root,
        )
    }
}
