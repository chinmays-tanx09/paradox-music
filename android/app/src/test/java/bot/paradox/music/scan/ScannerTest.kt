package bot.paradox.music.scan

import bot.paradox.music.TestFiles
import bot.paradox.music.data.Track
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ScannerTest {
    private val artDir = Files.createTempDirectory("pm-art").toFile()
    private val scanner = Scanner(RawArtStore(artDir), now = { 1000L })

    private fun scan(dir: File, prev: List<Track> = emptyList(), progress: (ScanProgress) -> Unit = {}) =
        scanner.scan(listOf("root" to FileNode(dir)), prev, progress)

    @Test fun findsEveryAudioFileRecursivelyAndSkipsHiddenAndNomedia() {
        val dir = TestFiles.copyTree()
        val tracks = scan(dir)
        val names = tracks.map { it.fileName }.sorted()
        assertEquals(listOf("07 - Arijit Singh - Tum Hi Ho.mp3", "Intro.wav", "song.aac", "song.flac", "song.m4a", "song.ogg", "song.opus", "song.wav", "tagged_v23.mp3", "tagged_v24.mp3"), names)
        assertFalse(tracks.any { it.fileName == "secret.wav" || it.fileName == "ring.wav" || it.fileName == "notes.txt" })
        assertTrue(tracks.all { it.root == "root" })
    }

    @Test fun fileNameFallbackForUntaggedFiles() {
        val t = scan(TestFiles.copyTree()).first { it.fileName.startsWith("07") }
        assertEquals("Tum Hi Ho", t.title); assertEquals("Arijit Singh", t.artist); assertEquals(7, t.track)
        assertEquals(Track.UNKNOWN_ALBUM, t.album)   // top folder: no folder name to borrow
        assertNull(t.art)
    }

    @Test fun folderCoverAndFolderNameForUntaggedFileInASubfolder() {
        val t = scan(TestFiles.copyTree()).first { it.fileName == "Intro.wav" }
        assertEquals("Intro", t.title); assertEquals("Album X", t.album); assertEquals("Album X", t.folder)
        assertEquals(Track.UNKNOWN_ARTIST, t.artist)
        assertNotNull("cover.jpg in the folder is used", t.art)
        assertTrue(File(artDir, t.art!!).exists())
    }

    @Test fun embeddedCoversAreStoredOnceEach() {
        val dir = TestFiles.copyTree()
        File(dir, "tagged_v24.mp3").copyTo(File(dir, "Album X/copy of v24.mp3"))
        val tracks = scan(dir)
        val withArt = tracks.filter { it.art != null }
        assertEquals(setOf("tagged_v24.mp3", "copy of v24.mp3", "tagged_v23.mp3", "song.m4a", "song.flac", "song.ogg", "Intro.wav"), withArt.map { it.fileName }.toSet())
        // Two songs with the same embedded picture share one file in the art store; embedded art beats cover.jpg.
        assertEquals(tracks.first { it.fileName == "tagged_v24.mp3" }.art, tracks.first { it.fileName == "copy of v24.mp3" }.art)
        assertEquals(withArt.mapNotNull { it.art }.toSet().size, artDir.listFiles()!!.size)
    }

    @Test fun rescanReusesUnchangedFilesAndRereadsChangedOnes() {
        val dir = TestFiles.copyTree()
        val first = scan(dir)
        var last: ScanProgress? = null
        val second = scan(dir, first) { last = it }
        assertEquals(first.toSet(), second.toSet())
        assertEquals(0, last!!.tagsRead)
        val f = File(dir, "song.wav"); f.setLastModified(f.lastModified() + 60_000)
        scan(dir, second) { last = it }
        assertEquals(1, last!!.tagsRead)
    }

    @Test fun addedAtSurvivesRescanAndNewFilesGetNow() {
        val dir = TestFiles.copyTree()
        val first = scan(dir)
        val sc2 = Scanner(RawArtStore(artDir), now = { 5000L })
        File(dir, "song.wav").copyTo(File(dir, "new song.wav"))
        val second = sc2.scan(listOf("root" to FileNode(dir)), first)
        assertEquals(1000L, second.first { it.fileName == "song.wav" }.addedAt)
        assertEquals(5000L, second.first { it.fileName == "new song.wav" }.addedAt)
    }

    @Test fun deletedFilesDisappear() {
        val dir = TestFiles.copyTree()
        val first = scan(dir)
        File(dir, "song.ogg").delete()
        assertEquals(first.size - 1, scan(dir, first).size)
    }

    @Test fun twoRootsAreBothScanned() {
        val a = TestFiles.copyTree(); val b = TestFiles.copyTree()
        val t = scanner.scan(listOf("a" to FileNode(a), "b" to FileNode(b)), emptyList())
        assertEquals(20, t.size); assertEquals(setOf("a", "b"), t.map { it.root }.toSet())
    }

    @Test fun cancelStopsEarly() {
        val t = scanner.scan(listOf("root" to FileNode(TestFiles.copyTree())), emptyList(), cancelled = { true })
        assertTrue(t.isEmpty())
    }

    @Test fun fileNamePatterns() {
        assertEquals(Triple("Tum Hi Ho", "Arijit Singh", 7), Scanner.fromFileName("07 - Arijit Singh - Tum Hi Ho.mp3"))
        assertEquals(Triple("Kesariya", "Arijit Singh", null), Scanner.fromFileName("Arijit Singh - Kesariya.flac"))
        assertEquals(Triple("Intro", null, 3), Scanner.fromFileName("03. Intro.m4a"))
        assertEquals(Triple("Just a title", null, null), Scanner.fromFileName("Just_a_title.wav"))
        assertEquals(Triple("Song", null, 1), Scanner.fromFileName("01 Song.mp3"))
        assertEquals(Triple("2019", null, null), Scanner.fromFileName("2019.mp3"))
    }

    @Test fun audioExtensions() {
        for (n in listOf("a.mp3", "a.M4A", "a.aac", "a.flac", "a.wav", "a.ogg", "a.opus", "a.oga")) assertTrue(n, Scanner.isAudio(n))
        for (n in listOf("a.txt", "a.jpg", "._a.mp3", "mp3")) assertFalse(n, Scanner.isAudio(n))
    }
}
