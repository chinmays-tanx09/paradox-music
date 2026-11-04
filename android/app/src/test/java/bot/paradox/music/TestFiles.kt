package bot.paradox.music

import java.io.File
import java.nio.file.Files

/** Test audio made with ffmpeg (see TEST_DATA.md), copied to a temp folder so dot-folders survive. */
object TestFiles {
    val audioSrc: File = listOf(File("src/test/resources/audio"), File("app/src/test/resources/audio")).first { it.exists() }
    fun audio(name: String) = File(audioSrc, name)
    fun copyTree(): File {
        val dst = Files.createTempDirectory("pm-audio").toFile()
        audioSrc.copyRecursively(File(dst, "Music"))
        return File(dst, "Music")
    }
}
