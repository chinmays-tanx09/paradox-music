package bot.paradox.music.scan

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Base64

/** A seekable byte source: a file channel on the phone, a plain file in tests. */
interface Source {
    val length: Long
    /** Reads up to [len] bytes at [pos]; returns the count read (0 at the end). */
    fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int
}

class ByteSource(private val bytes: ByteArray) : Source {
    override val length: Long get() = bytes.size.toLong()
    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (pos >= bytes.size) return 0
        val n = minOf(len.toLong(), bytes.size - pos).toInt()
        System.arraycopy(bytes, pos.toInt(), buf, off, n); return n
    }
}

class FileChannelSource(private val ch: java.nio.channels.FileChannel) : Source {
    override val length: Long = ch.size()
    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        var total = 0
        while (total < len) {
            val n = ch.read(java.nio.ByteBuffer.wrap(buf, off + total, len - total), pos + total)
            if (n <= 0) break
            total += n
        }
        return total
    }
}

/** What the tag reader found. Every field may be missing; the scanner fills the gaps. */
data class Tags(
    var title: String? = null,
    var artist: String? = null,
    var album: String? = null,
    var albumArtist: String? = null,
    var genre: String? = null,
    var year: Int? = null,
    var track: Int? = null,
    var disc: Int? = null,
    var durationMs: Long = 0,
    var picture: ByteArray? = null,
    var format: String = "",
) {
    /** Only set what is still empty, so a better source read first wins. */
    fun fillFrom(o: Tags) {
        if (title.isNullOrBlank()) title = o.title
        if (artist.isNullOrBlank()) artist = o.artist
        if (album.isNullOrBlank()) album = o.album
        if (albumArtist.isNullOrBlank()) albumArtist = o.albumArtist
        if (genre.isNullOrBlank()) genre = o.genre
        if (year == null) year = o.year
        if (track == null) track = o.track
        if (disc == null) disc = o.disc
        if (durationMs <= 0) durationMs = o.durationMs
        if (picture == null) picture = o.picture
    }
}

/**
 * A small, dependency-free tag reader for the formats Paradox Music plays:
 * MP3 (ID3v2.2/2.3/2.4 + ID3v1), MP4/M4A (iTunes ilst), FLAC, Ogg Vorbis/Opus, WAV (RIFF INFO + id3) and ADTS AAC.
 * It reads only the header regions it needs, never the whole file. Never throws.
 */
object TagReader {
    const val MAX_PICTURE = 12 * 1024 * 1024

    fun read(src: Source, ext: String): Tags = runCatching { readInner(src, ext.lowercase()) }.getOrElse { Tags() }

    private fun readInner(src: Source, ext: String): Tags {
        val head = bytes(src, 0, 12)
        if (head.size < 4) return Tags()
        val t = when {
            head.startsWith("fLaC") -> flac(src, 0)
            head.startsWith("OggS") -> ogg(src)
            head.size >= 12 && head.startsWith("RIFF") && String(head, 8, 4, Charsets.ISO_8859_1) == "WAVE" -> wav(src)
            head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> mp4(src)
            head.startsWith("ID3") -> {
                val (id3, end) = id3v2(src, 0)
                val more = bytes(src, end, 4)
                val rest = when {
                    more.startsWith("fLaC") -> flac(src, end)
                    ext == "aac" || (more.size >= 2 && (more[0].toInt() and 0xFF) == 0xFF && (more[1].toInt() and 0xF6) == 0xF0) -> adts(src, end)
                    else -> mp3(src, end)
                }
                id3.fillFrom(rest); id3.format = rest.format; id3
            }
            (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xF6) == 0xF0 -> adts(src, 0)
            else -> mp3(src, 0)
        }
        if (t.title.isNullOrBlank() || t.artist.isNullOrBlank()) id3v1(src)?.let { t.fillFrom(it) }
        clean(t)
        return t
    }

    private fun clean(t: Tags) {
        fun c(s: String?) = s?.replace('\u0000', ' ')?.trim()?.takeIf { it.isNotEmpty() }
        t.title = c(t.title); t.artist = c(t.artist); t.album = c(t.album); t.albumArtist = c(t.albumArtist); t.genre = c(t.genre)
        if (t.picture != null && t.picture!!.size < 16) t.picture = null
    }

    // ---------------------------------------------------------------- helpers
    private fun bytes(src: Source, pos: Long, len: Int): ByteArray {
        if (pos < 0 || pos >= src.length || len <= 0) return ByteArray(0)
        val n = minOf(len.toLong(), src.length - pos).toInt()
        val b = ByteArray(n)
        val r = src.read(pos, b, 0, n)
        return if (r == n) b else b.copyOf(maxOf(r, 0))
    }
    private fun ByteArray.startsWith(s: String) = size >= s.length && s.indices.all { this[it] == s[it].code.toByte() }
    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun be16(b: ByteArray, i: Int) = (u8(b, i) shl 8) or u8(b, i + 1)
    private fun be24(b: ByteArray, i: Int) = (u8(b, i) shl 16) or (u8(b, i + 1) shl 8) or u8(b, i + 2)
    private fun be32(b: ByteArray, i: Int): Long = (be16(b, i).toLong() shl 16) or be16(b, i + 2).toLong()
    private fun be64(b: ByteArray, i: Int): Long = (be32(b, i) shl 32) or be32(b, i + 4)
    private fun le16(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)
    private fun le32(b: ByteArray, i: Int): Long = le16(b, i).toLong() or (le16(b, i + 2).toLong() shl 16)
    private fun le64(b: ByteArray, i: Int): Long = le32(b, i) or (le32(b, i + 4) shl 32)
    private fun syncsafe(b: ByteArray, i: Int) = ((u8(b, i) and 0x7F) shl 21) or ((u8(b, i + 1) and 0x7F) shl 14) or ((u8(b, i + 2) and 0x7F) shl 7) or (u8(b, i + 3) and 0x7F)

    /** "3/12" -> 3, "2005-03-01" -> 2005 */
    fun leadingInt(s: String?): Int? = s?.trim()?.takeWhile { it.isDigit() }?.takeIf { it.isNotEmpty() && it.length <= 9 }?.toInt()

    // ---------------------------------------------------------------- ID3v2
    /** Returns the tags and the offset just past the tag (0 + header + size + footer). */
    fun id3v2(src: Source, at: Long): Pair<Tags, Long> {
        val t = Tags()
        val h = bytes(src, at, 10)
        if (h.size < 10 || !h.startsWith("ID3")) return t to at
        val ver = u8(h, 3)
        val flags = u8(h, 5)
        val size = syncsafe(h, 6)
        val end = at + 10 + size + (if (flags and 0x10 != 0) 10 else 0)
        if (ver !in 2..4) return t to end
        var body = bytes(src, at + 10, size)
        if (ver <= 3 && flags and 0x80 != 0) body = unsync(body)
        var p = 0
        if (flags and 0x40 != 0 && ver >= 3 && body.size >= 4) {
            p = if (ver == 3) (be32(body, 0) + 4).toInt() else syncsafe(body, 0)
        }
        var bestPicType = Int.MAX_VALUE
        while (true) {
            val hdr = if (ver == 2) 6 else 10
            if (p + hdr > body.size) break
            if (body[p].toInt() == 0) break
            val id = String(body, p, if (ver == 2) 3 else 4, Charsets.ISO_8859_1)
            val fsize = when (ver) { 2 -> be24(body, p + 3); 3 -> be32(body, p + 4).toInt(); else -> syncsafe(body, p + 4) }
            val fflags = if (ver == 2) 0 else be16(body, p + 8)
            p += hdr
            if (fsize <= 0 || p + fsize > body.size) break
            var data = body.copyOfRange(p, p + fsize)
            p += fsize
            if (ver == 4) {
                if (fflags and 0x0001 != 0 && data.size >= 4) data = data.copyOfRange(4, data.size) // data length indicator
                if (fflags and 0x0002 != 0) data = unsync(data)
                if (fflags and 0x000C != 0) continue // compressed or encrypted: skip
            } else if (ver == 3 && fflags and 0x00C0 != 0) continue
            when (id) {
                "TIT2", "TT2" -> t.title = text(data)
                "TPE1", "TP1" -> t.artist = text(data)
                "TALB", "TAL" -> t.album = text(data)
                "TPE2", "TP2" -> t.albumArtist = text(data)
                "TCON", "TCO" -> t.genre = genre(text(data))
                "TRCK", "TRK" -> t.track = leadingInt(text(data))
                "TPOS", "TPA" -> t.disc = leadingInt(text(data))
                "TYER", "TYE", "TDRC", "TDOR" -> if (t.year == null) t.year = leadingInt(text(data))?.takeIf { it in 1000..9999 }
                "APIC", "PIC" -> {
                    val pic = apic(data, ver == 2)
                    if (pic != null && (pic.first == 3 && bestPicType != 3 || t.picture == null)) {
                        t.picture = pic.second; bestPicType = pic.first
                    }
                }
            }
        }
        return t to end
    }

    private fun unsync(b: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            o.write(b[i].toInt())
            if ((b[i].toInt() and 0xFF) == 0xFF && i + 1 < b.size && b[i + 1].toInt() == 0) i++
            i++
        }
        return o.toByteArray()
    }

    private fun charset(enc: Int): Charset = when (enc) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }

    private fun text(d: ByteArray): String? {
        if (d.isEmpty()) return null
        val enc = u8(d, 0)
        val s = String(d, 1, d.size - 1, charset(enc))
        // ID3v2.4 lists several values separated by NUL; show them joined.
        return s.split('\u0000').map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ").ifEmpty { null }
    }

    private fun genre(s: String?): String? {
        if (s == null) return null
        val m = Regex("^\\((\\d+)\\)(.*)$").find(s) ?: return if (s.all { it.isDigit() }) GENRES.getOrNull(s.toInt()) ?: s else s
        return m.groupValues[2].ifBlank { GENRES.getOrNull(m.groupValues[1].toInt()) ?: s }
    }

    /** Index just past a NUL terminator for the given text encoding (two zero bytes for UTF-16). */
    private fun skipString(d: ByteArray, from: Int, enc: Int): Int {
        var i = from
        if (enc == 1 || enc == 2) {
            while (i + 1 < d.size) { if (d[i].toInt() == 0 && d[i + 1].toInt() == 0) return i + 2; i += 2 }
            return d.size
        }
        while (i < d.size) { if (d[i].toInt() == 0) return i + 1; i++ }
        return d.size
    }

    private fun apic(d: ByteArray, v22: Boolean): Pair<Int, ByteArray>? {
        if (d.size < 4) return null
        val enc = u8(d, 0)
        var i = 1
        i = if (v22) i + 3 else skipString(d, i, 0)
        if (i >= d.size) return null
        val type = u8(d, i); i++
        i = skipString(d, i, enc)
        if (i >= d.size) return null
        val img = d.copyOfRange(i, d.size)
        return if (img.size <= MAX_PICTURE) type to img else null
    }

    fun id3v1(src: Source): Tags? {
        if (src.length < 128) return null
        val b = bytes(src, src.length - 128, 128)
        if (!b.startsWith("TAG")) return null
        fun f(o: Int, n: Int) = String(b, o, n, Charsets.ISO_8859_1).substringBefore('\u0000').trim().ifEmpty { null }
        return Tags(title = f(3, 30), artist = f(33, 30), album = f(63, 30), year = leadingInt(f(93, 4)),
            track = if (b[125].toInt() == 0 && b[126].toInt() != 0) u8(b, 126) else null,
            genre = GENRES.getOrNull(u8(b, 127)))
    }

    // ---------------------------------------------------------------- MP3 duration
    private val BITRATES_V1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val BITRATES_V2_L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    private val BITRATES_V1_L2 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
    private val BITRATES_V1_L1 = intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
    private val BITRATES_V2_L1 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256)

    fun mp3(src: Source, start: Long): Tags {
        val t = Tags(format = "mp3")
        val win = bytes(src, start, 64 * 1024)
        var i = 0
        while (i + 4 <= win.size) {
            if (u8(win, i) == 0xFF && (u8(win, i + 1) and 0xE0) == 0xE0) {
                val h1 = u8(win, i + 1); val h2 = u8(win, i + 2); val h3 = u8(win, i + 3)
                val verBits = (h1 shr 3) and 3   // 3 = MPEG1, 2 = MPEG2, 0 = MPEG2.5
                val layerBits = (h1 shr 1) and 3 // 1 = L3, 2 = L2, 3 = L1
                val brIdx = (h2 shr 4) and 0xF
                val srIdx = (h2 shr 2) and 3
                if (verBits != 1 && layerBits != 0 && brIdx in 1..14 && srIdx != 3) {
                    val mpeg1 = verBits == 3
                    val sr = intArrayOf(44100, 48000, 32000)[srIdx] / (if (mpeg1) 1 else if (verBits == 2) 2 else 4)
                    val kbps = when (layerBits) {
                        1 -> if (mpeg1) BITRATES_V1_L3[brIdx] else BITRATES_V2_L3[brIdx]
                        2 -> if (mpeg1) BITRATES_V1_L2[brIdx] else BITRATES_V2_L3[brIdx]
                        else -> if (mpeg1) BITRATES_V1_L1[brIdx] else BITRATES_V2_L1[brIdx]
                    }
                    val spf = when (layerBits) { 3 -> 384; 2 -> 1152; else -> if (mpeg1) 1152 else 576 }
                    val mono = (h3 shr 6) == 3
                    val side = if (mpeg1) (if (mono) 17 else 32) else (if (mono) 9 else 17)
                    val x = i + 4 + side
                    if (x + 12 <= win.size && (String(win, x, 4, Charsets.ISO_8859_1).let { it == "Xing" || it == "Info" })) {
                        val fl = be32(win, x + 4)
                        if (fl and 1L != 0L) { val frames = be32(win, x + 8); if (frames > 0) { t.durationMs = frames * spf * 1000 / sr; return t } }
                    }
                    val v = i + 4 + 32
                    if (v + 18 <= win.size && String(win, v, 4, Charsets.ISO_8859_1) == "VBRI") {
                        val frames = be32(win, v + 14); if (frames > 0) { t.durationMs = frames * spf * 1000 / sr; return t }
                    }
                    var audio = src.length - (start + i)
                    if (src.length >= 128 && bytes(src, src.length - 128, 3).startsWith("TAG")) audio -= 128
                    if (kbps > 0) t.durationMs = audio * 8 / kbps
                    return t
                }
            }
            i++
        }
        return t
    }

    // ---------------------------------------------------------------- ADTS AAC
    fun adts(src: Source, start: Long): Tags {
        val t = Tags(format = "aac")
        val win = bytes(src, start, 256 * 1024)
        val rates = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)
        var i = 0; var frames = 0; var bytesSeen = 0L; var sr = 0
        while (i + 7 <= win.size) {
            if (u8(win, i) != 0xFF || (u8(win, i + 1) and 0xF6) != 0xF0) { if (frames > 0) break; i++; continue }
            val srIdx = (u8(win, i + 2) shr 2) and 0xF
            val len = ((u8(win, i + 3) and 3) shl 11) or (u8(win, i + 4) shl 3) or (u8(win, i + 5) shr 5)
            if (srIdx >= rates.size || len < 7) { if (frames > 0) break; i++; continue }
            sr = rates[srIdx]; frames++; bytesSeen += len; i += len
            if (frames >= 400) break
        }
        if (frames > 0 && sr > 0) {
            val secs = frames * 1024.0 / sr
            val bytesPerSec = bytesSeen / secs
            t.durationMs = ((src.length - start) / bytesPerSec * 1000).toLong()
        }
        return t
    }

    // ---------------------------------------------------------------- FLAC
    fun flac(src: Source, at: Long): Tags {
        val t = Tags(format = "flac")
        var p = at + 4
        var bestPicType = Int.MAX_VALUE
        while (p + 4 <= src.length) {
            val h = bytes(src, p, 4)
            if (h.size < 4) break
            val last = u8(h, 0) and 0x80 != 0
            val type = u8(h, 0) and 0x7F
            val len = be24(h, 1)
            p += 4
            when (type) {
                0 -> { val b = bytes(src, p, 34); if (b.size >= 18) {
                    val sr = (u8(b, 10) shl 12) or (u8(b, 11) shl 4) or (u8(b, 12) shr 4)
                    val total = ((u8(b, 13) and 0x0F).toLong() shl 32) or be32(b, 14)
                    if (sr > 0) t.durationMs = total * 1000 / sr
                } }
                4 -> vorbisComments(bytes(src, p, len), 0, t)
                6 -> if (len <= MAX_PICTURE + 4096) flacPicture(bytes(src, p, len))?.let { (ty, img) ->
                    if (t.picture == null || (ty == 3 && bestPicType != 3)) { t.picture = img; bestPicType = ty }
                }
            }
            p += len
            if (last) break
        }
        return t
    }

    /** A FLAC PICTURE block body (also what Ogg's METADATA_BLOCK_PICTURE carries, base64). */
    fun flacPicture(b: ByteArray): Pair<Int, ByteArray>? {
        if (b.size < 32) return null
        var i = 0
        val type = be32(b, i).toInt(); i += 4
        val mimeLen = be32(b, i).toInt(); i += 4 + mimeLen
        if (i + 4 > b.size) return null
        val descLen = be32(b, i).toInt(); i += 4 + descLen
        i += 16
        if (i + 4 > b.size) return null
        val dataLen = be32(b, i).toInt(); i += 4
        if (dataLen <= 0 || i + dataLen > b.size) return null
        return type to b.copyOfRange(i, i + dataLen)
    }

    /** Vorbis comment list (little-endian), used by FLAC, Ogg Vorbis and OpusTags. */
    fun vorbisComments(b: ByteArray, start: Int, t: Tags) {
        var i = start
        if (i + 4 > b.size) return
        val vendor = le32(b, i).toInt(); i += 4 + vendor
        if (vendor < 0 || i + 4 > b.size) return
        val n = le32(b, i).toInt(); i += 4
        var bestPicType = Int.MAX_VALUE
        for (k in 0 until n) {
            if (i + 4 > b.size) break
            val len = le32(b, i).toInt(); i += 4
            if (len < 0 || i + len > b.size) break
            val s = String(b, i, len, Charsets.UTF_8); i += len
            val eq = s.indexOf('='); if (eq <= 0) continue
            val key = s.substring(0, eq).uppercase(); val v = s.substring(eq + 1)
            when (key) {
                "TITLE" -> if (t.title == null) t.title = v
                "ARTIST" -> t.artist = if (t.artist == null) v else t.artist + ", " + v
                "ALBUM" -> if (t.album == null) t.album = v
                "ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST" -> if (t.albumArtist == null) t.albumArtist = v
                "GENRE" -> if (t.genre == null) t.genre = v
                "TRACKNUMBER" -> if (t.track == null) t.track = leadingInt(v)
                "DISCNUMBER" -> if (t.disc == null) t.disc = leadingInt(v)
                "DATE", "YEAR", "ORIGINALDATE" -> if (t.year == null) t.year = leadingInt(v)?.takeIf { it in 1000..9999 }
                "METADATA_BLOCK_PICTURE" -> runCatching { flacPicture(Base64.getMimeDecoder().decode(v)) }.getOrNull()?.let { (ty, img) ->
                    if (t.picture == null || (ty == 3 && bestPicType != 3)) { t.picture = img; bestPicType = ty }
                }
                "COVERART" -> if (t.picture == null) t.picture = runCatching { Base64.getMimeDecoder().decode(v) }.getOrNull()
            }
        }
    }

    // ---------------------------------------------------------------- Ogg (Vorbis / Opus)
    fun ogg(src: Source): Tags {
        val t = Tags(format = "ogg")
        val packets = ArrayList<ByteArray>()
        val cur = ByteArrayOutputStream()
        var p = 0L
        var serial = -1L
        var pages = 0
        val cap = MAX_PICTURE * 2
        loop@ while (packets.size < 2 && pages < 4000) {
            val h = bytes(src, p, 27)
            if (h.size < 27 || !h.startsWith("OggS")) break
            val nseg = u8(h, 26)
            val segs = bytes(src, p + 27, nseg)
            if (serial == -1L) serial = le32(h, 14)
            var q = p + 27 + nseg
            pages++
            for (s in segs) {
                val l = s.toInt() and 0xFF
                if (le32(h, 14) == serial) cur.write(bytes(src, q, l))
                q += l
                if (l < 255 && le32(h, 14) == serial) { packets.add(cur.toByteArray()); cur.reset(); if (packets.size >= 2) break@loop }
                if (cur.size() > cap) break@loop
            }
            p = q
        }
        if (packets.isEmpty()) return t
        val first = packets[0]
        var rate = 0; var preSkip = 0
        when {
            first.startsWith("OpusHead") && first.size >= 12 -> { t.format = "opus"; rate = 48000; preSkip = le16(first, 10) }
            first.size >= 16 && first[0].toInt() == 1 && String(first, 1, 6, Charsets.ISO_8859_1) == "vorbis" -> { rate = le32(first, 12).toInt() }
        }
        if (packets.size >= 2) {
            val c = packets[1]
            when {
                c.startsWith("OpusTags") -> vorbisComments(c, 8, t)
                c.size > 7 && c[0].toInt() == 3 && String(c, 1, 6, Charsets.ISO_8859_1) == "vorbis" -> vorbisComments(c, 7, t)
            }
        }
        // Duration: the granule position of the last page of this stream.
        if (rate > 0) {
            val tailLen = minOf(src.length, 128L * 1024).toInt()
            val tail = bytes(src, src.length - tailLen, tailLen)
            var i = tail.size - 27
            while (i >= 0) {
                if (tail[i] == 'O'.code.toByte() && tail[i + 1] == 'g'.code.toByte() && tail[i + 2] == 'g'.code.toByte() && tail[i + 3] == 'S'.code.toByte()
                    && le32(tail, i + 14) == serial) {
                    val g = le64(tail, i + 6)
                    if (g > 0) { t.durationMs = maxOf(0L, g - preSkip) * 1000 / rate; break }
                }
                i--
            }
        }
        return t
    }

    // ---------------------------------------------------------------- WAV
    fun wav(src: Source): Tags {
        val t = Tags(format = "wav")
        var p = 12L
        var byteRate = 0L
        var dataSize = 0L
        while (p + 8 <= src.length) {
            val h = bytes(src, p, 8)
            if (h.size < 8) break
            val id = String(h, 0, 4, Charsets.ISO_8859_1)
            var size = le32(h, 4)
            p += 8
            if (id == "data" && (size == 0L || size == 0xFFFFFFFFL || p + size > src.length)) size = src.length - p
            when (id) {
                "fmt " -> { val f = bytes(src, p, 16); if (f.size >= 12) byteRate = le32(f, 8) }
                "data" -> dataSize = size
                "LIST" -> { val l = bytes(src, p, minOf(size, 1L shl 20).toInt()); if (l.startsWith("INFO")) riffInfo(l, t) }
                "id3 ", "ID3 " -> {
                    val inner = bytes(src, p, minOf(size, MAX_PICTURE.toLong() + 65536).toInt())
                    val (id3, _) = id3v2(ByteSource(inner), 0)
                    id3.fillFrom(t); id3.durationMs = 0
                    t.title = id3.title; t.artist = id3.artist; t.album = id3.album; t.albumArtist = id3.albumArtist
                    t.genre = id3.genre; t.year = id3.year; t.track = id3.track; t.disc = id3.disc; t.picture = id3.picture
                }
            }
            p += size + (size and 1)
        }
        if (byteRate > 0) t.durationMs = dataSize * 1000 / byteRate
        return t
    }

    private fun riffInfo(l: ByteArray, t: Tags) {
        var i = 4
        while (i + 8 <= l.size) {
            val id = String(l, i, 4, Charsets.ISO_8859_1)
            val size = le32(l, i + 4).toInt()
            i += 8
            if (size < 0 || i + size > l.size) break
            val v = String(l, i, size, Charsets.UTF_8).substringBefore('\u0000').trim()
            when (id) {
                "INAM" -> if (t.title == null) t.title = v
                "IART" -> if (t.artist == null) t.artist = v
                "IPRD" -> if (t.album == null) t.album = v
                "IGNR" -> if (t.genre == null) t.genre = v
                "ICRD" -> if (t.year == null) t.year = leadingInt(v)
                "ITRK", "IPRT" -> if (t.track == null) t.track = leadingInt(v)
            }
            i += size + (size and 1)
        }
    }

    // ---------------------------------------------------------------- MP4 / M4A
    fun mp4(src: Source): Tags {
        val t = Tags(format = "m4a")
        atoms(src, 0, src.length) { type, body, size ->
            if (type == "moov") {
                atoms(src, body, size) { ct, cb, cs ->
                    when (ct) {
                        "mvhd" -> { val b = bytes(src, cb, 32); if (b.size >= 28) {
                            val v1 = u8(b, 0) == 1
                            val scale = if (v1) be32(b, 20) else be32(b, 12)
                            val dur = if (v1) be64(b, 24) else be32(b, 16)
                            if (scale > 0 && dur != 0xFFFFFFFFL) t.durationMs = dur * 1000 / scale
                        } }
                        "udta" -> atoms(src, cb, cs) { ut, ub, us -> if (ut == "meta") meta(src, ub, us, t) }
                        "meta" -> meta(src, cb, cs, t)
                    }
                }
            }
        }
        return t
    }

    private fun meta(src: Source, body: Long, size: Long, t: Tags) {
        // ISO "meta" is a full box (4 bytes version/flags); QuickTime's is not. Peek to tell.
        val peek = bytes(src, body + 4, 4)
        val start = if (peek.size == 4 && String(peek, Charsets.ISO_8859_1) in setOf("hdlr", "ilst", "keys", "free")) body else body + 4
        atoms(src, start, size - (start - body)) { mt, mb, ms -> if (mt == "ilst") ilst(src, mb, ms, t) }
    }

    private fun ilst(src: Source, body: Long, size: Long, t: Tags) {
        atoms(src, body, size) { key, ib, isz ->
            atoms(src, ib, isz) { dt, db, ds ->
                if (dt != "data" || ds < 8) return@atoms
                val n = ds - 8
                if (key == "covr") {
                    if (t.picture == null && n in 16..MAX_PICTURE.toLong()) t.picture = bytes(src, db + 8, n.toInt())
                    return@atoms
                }
                if (n > 64 * 1024) return@atoms
                val v = bytes(src, db + 8, n.toInt())
                fun s() = String(v, Charsets.UTF_8)
                when (key) {
                    "\u00A9nam" -> t.title = s()
                    "\u00A9ART" -> t.artist = s()
                    "\u00A9alb" -> t.album = s()
                    "aART" -> t.albumArtist = s()
                    "\u00A9gen" -> t.genre = s()
                    "gnre" -> if (v.size >= 2) t.genre = GENRES.getOrNull(be16(v, 0) - 1)
                    "\u00A9day" -> t.year = leadingInt(s())?.takeIf { it in 1000..9999 }
                    "trkn" -> if (v.size >= 4) t.track = be16(v, 2).takeIf { it > 0 }
                    "disk" -> if (v.size >= 4) t.disc = be16(v, 2).takeIf { it > 0 }
                }
            }
        }
    }

    /** Walks sibling boxes in [start, start+len). The callback gets (type, bodyStart, bodySize). */
    private fun atoms(src: Source, start: Long, len: Long, f: (String, Long, Long) -> Unit) {
        var p = start
        val end = minOf(start + len, src.length)
        var guard = 0
        while (p + 8 <= end && guard++ < 10000) {
            val h = bytes(src, p, 16)
            if (h.size < 8) break
            var size = be32(h, 0)
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            var hdr = 8
            if (size == 1L && h.size >= 16) { size = be64(h, 8); hdr = 16 } else if (size == 0L) size = end - p
            if (size < hdr) break
            f(type, p + hdr, minOf(size, end - p) - hdr)
            p += size
        }
    }

    val GENRES = listOf("Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies",
        "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack",
        "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game",
        "Sound Clip", "Gospel", "Noise", "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop",
        "Instrumental Rock", "Ethnic", "Gothic", "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream",
        "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret",
        "New Wave", "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro",
        "Musical", "Rock & Roll", "Hard Rock")
}
