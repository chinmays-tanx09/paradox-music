package bot.paradox.music.scan

import bot.paradox.music.TestFiles
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TagReaderTest {
    private fun read(name: String): Tags {
        val f = TestFiles.audio(name)
        return java.io.RandomAccessFile(f, "r").use { TagReader.read(FileChannelSource(it.channel), Scanner.ext(name)) }
    }
    private fun assertDuration(t: Tags, expectMs: Long) =
        assertTrue("duration ${t.durationMs} vs $expectMs", kotlin.math.abs(t.durationMs - expectMs) <= expectMs * 0.12)
    private fun isJpeg(b: ByteArray?) = b != null && b.size > 100 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()
    private fun isPng(b: ByteArray?) = b != null && b.size > 100 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte()

    @Test fun mp3Id3v24WithCover() {
        val t = read("tagged_v24.mp3")
        assertEquals("Mitti Di Khushboo", t.title); assertEquals("Ayushmann Khurrana", t.artist)
        assertEquals("Rooh", t.album); assertEquals("Rochak Kohli", t.albumArtist)
        assertEquals(3, t.track); assertEquals(1, t.disc); assertEquals(2019, t.year); assertEquals("Pop", t.genre)
        assertTrue(isJpeg(t.picture)); assertDuration(t, 3050)
    }

    @Test fun mp3Id3v23UnicodeAndPng() {
        val t = read("tagged_v23.mp3")
        assertEquals("ಕನ್ನಡ ಹಾಡು", t.title); assertEquals("ಗಾಯಕ", t.artist); assertEquals("हिंदी एल्बम", t.album)
        assertEquals(7, t.track); assertEquals(2021, t.year)
        assertTrue(isPng(t.picture)); assertDuration(t, 3050)
    }

    @Test fun m4aIlstWithCover() {
        val t = read("song.m4a")
        assertEquals("Kesariya", t.title); assertEquals("Arijit Singh", t.artist); assertEquals("Brahmastra", t.album)
        assertEquals("Pritam", t.albumArtist); assertEquals(2, t.track); assertEquals(1, t.disc); assertEquals(2022, t.year)
        assertTrue(isJpeg(t.picture)); assertDuration(t, 3000)
    }

    @Test fun flacVorbisCommentAndPicture() {
        val t = read("song.flac")
        assertEquals("Flac Song", t.title); assertEquals("Lossless Artist", t.artist); assertEquals("Hi-Res", t.album)
        assertEquals("Various", t.albumArtist); assertEquals(5, t.track); assertEquals(2018, t.year)
        assertTrue(isJpeg(t.picture)); assertDuration(t, 2000)
    }

    @Test fun oggVorbisWithMetadataBlockPicture() {
        val t = read("song.ogg")
        assertEquals("Vorbis Song", t.title); assertEquals("Ogg Artist", t.artist); assertEquals("Open Formats", t.album)
        assertEquals(4, t.track); assertEquals(2017, t.year)
        assertTrue(isJpeg(t.picture)); assertDuration(t, 3000)
    }

    @Test fun opus() {
        val t = read("song.opus")
        assertEquals("opus", t.format); assertEquals("Opus Song", t.title); assertEquals("Opus Artist", t.artist); assertEquals(6, t.track)
        assertDuration(t, 3000)
    }

    @Test fun wavRiffInfo() {
        val t = read("song.wav")
        assertEquals("Wave Song", t.title); assertEquals("Wav Artist", t.artist); assertEquals("PCM", t.album); assertEquals(2015, t.year)
        assertDuration(t, 2000)
    }

    @Test fun adtsAacDuration() { assertDuration(read("song.aac"), 3060) }

    @Test fun untaggedMp3HasNoTagsButADuration() {
        val t = read("07 - Arijit Singh - Tum Hi Ho.mp3")
        assertNull(t.title); assertNull(t.artist); assertNull(t.picture); assertDuration(t, 3050)
    }

    @Test fun garbageNeverThrows() {
        for (bytes in listOf(ByteArray(0), ByteArray(3) { 1 }, "ID3\u0004\u0000\u0000\u007f\u007f\u007f\u007fTIT2".toByteArray(), "fLaC\u0084\u00ff\u00ff\u00ff".toByteArray(),
                "OggS".toByteArray() + ByteArray(40), ByteArray(4000) { (it * 31).toByte() })) {
            for (ext in listOf("mp3", "flac", "ogg", "m4a", "wav")) TagReader.read(ByteSource(bytes), ext)
        }
        // A truncated real file still reads what it has.
        val cut = TestFiles.audio("tagged_v24.mp3").readBytes().copyOf(200)
        val t = TagReader.read(ByteSource(cut), "mp3")
        assertEquals("Mitti Di Khushboo", t.title)
    }

    @Test fun id3v1Fallback() {
        val b = ByteArray(128); "TAG".toByteArray().copyInto(b)
        "Old Song".toByteArray().copyInto(b, 3); "Old Artist".toByteArray().copyInto(b, 33); "Old Album".toByteArray().copyInto(b, 63)
        "1999".toByteArray().copyInto(b, 93); b[126] = 9; b[127] = 17
        val t = TagReader.read(ByteSource(ByteArray(500) + b), "mp3")
        assertEquals("Old Song", t.title); assertEquals("Old Artist", t.artist); assertEquals("Old Album", t.album)
        assertEquals(1999, t.year); assertEquals(9, t.track); assertEquals("Rock", t.genre)
    }

    @Test fun leadingInt() {
        assertEquals(3, TagReader.leadingInt("3/12")); assertEquals(2005, TagReader.leadingInt("2005-03-01")); assertNull(TagReader.leadingInt("abc"))
    }

    @Suppress("unused") private fun f(n: String) = File(n)
}
