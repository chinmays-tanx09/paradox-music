package bot.paradox.music.scan

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileInputStream

/** A document inside a folder the user granted through the system picker. */
class SafNode(
    private val cr: ContentResolver,
    private val tree: Uri,
    private val docId: String,
    override val name: String,
    override val isDir: Boolean,
    override val size: Long,
    override val modified: Long,
) : Node {
    val uri: Uri get() = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
    override val id: String get() = uri.toString()

    override fun children(): List<Node> {
        if (!isDir) return emptyList()
        val q = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val proj = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val out = ArrayList<Node>()
        cr.query(q, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val nm = c.getString(1) ?: continue
                val dir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                out.add(SafNode(cr, tree, id, nm, dir, if (c.isNull(3)) 0 else c.getLong(3), if (c.isNull(4)) 0 else c.getLong(4)))
            }
        }
        return out
    }

    override fun <T> open(block: (Source) -> T): T? = runCatching {
        cr.openFileDescriptor(uri, "r")?.use { pfd -> FileInputStream(pfd.fileDescriptor).use { fis -> block(FileChannelSource(fis.channel)) } }
    }.getOrNull()

    companion object {
        /** The root of a granted tree, or null when access was lost. */
        fun root(ctx: Context, tree: Uri): SafNode? = runCatching {
            val id = DocumentsContract.getTreeDocumentId(tree)
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            ctx.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) SafNode(ctx.contentResolver, tree, id, c.getString(0) ?: "Music", true, 0, 0) else null
            }
        }.getOrNull()

        /** "primary:Music/Bollywood" -> "Music/Bollywood"; a friendly label for a picked folder. */
        fun label(tree: Uri): String = runCatching {
            val id = DocumentsContract.getTreeDocumentId(tree)
            val vol = id.substringBefore(':'); val path = id.substringAfter(':', "")
            when {
                path.isEmpty() -> if (vol == "primary") "Internal storage" else "SD card"
                vol == "primary" -> path
                else -> "SD card/$path"
            }
        }.getOrDefault(tree.lastPathSegment ?: "Music")
    }
}

/** Saves covers downsized to at most 600 px as JPEG, named by the hash of the original bytes. */
class AndroidArtStore(private val dir: File) : ArtStore {
    override fun has(name: String) = File(dir, name).exists()
    override fun put(bytes: ByteArray): String? {
        val name = Scanner.hash(bytes) + ".jpg"
        val f = File(dir, name)
        if (f.exists()) return name
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (o.outWidth / (sample * 2) >= 600 && o.outHeight / (sample * 2) >= 600) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = 600f / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (out !== bmp) out.recycle()
        bmp.recycle()
        return if (tmp.renameTo(f)) name else null
    }
}

/** Android's own reader, used only when the built-in one cannot time or name a file. */
class RetrieverFallback(private val ctx: Context) : TagFallback {
    override fun read(node: Node): Tags? {
        val uri = (node as? SafNode)?.uri ?: return null
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            fun k(key: Int) = r.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            Tags(
                title = k(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = k(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = k(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = k(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                genre = k(MediaMetadataRetriever.METADATA_KEY_GENRE),
                year = TagReader.leadingInt(k(MediaMetadataRetriever.METADATA_KEY_YEAR)),
                track = TagReader.leadingInt(k(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
                disc = TagReader.leadingInt(k(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)),
                durationMs = k(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                picture = r.embeddedPicture,
            )
        } catch (_: Exception) { null } finally { runCatching { r.release() } }
    }
}
