package bot.paradox.music

import android.graphics.BitmapFactory
import bot.paradox.music.scan.AndroidArtStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AndroidPartsTest {
    @Test fun artStoreDownsizesToAJpegAndDedupes() {
        val dir = Files.createTempDirectory("art").toFile()
        val store = AndroidArtStore(dir)
        val big = java.io.ByteArrayOutputStream().also {
            android.graphics.Bitmap.createBitmap(1400, 1000, android.graphics.Bitmap.Config.ARGB_8888).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val name = store.put(big)!!
        assertTrue(name.endsWith(".jpg"))
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(File(dir, name).path, o)
        assertTrue("${o.outWidth}x${o.outHeight}", o.outWidth <= 600 && o.outHeight <= 600 && o.outWidth >= 300)
        assertEquals(name, store.put(big)); assertEquals(1, dir.listFiles()!!.size)
        assertNull(store.put("not an image at all, just text".toByteArray()))
    }

    /** The manifest asks for no media/storage permission: music comes through the folder the user picks. */
    @Test fun manifestPermissionsAndService() {
        val m = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }.readText()
        val perms = Regex("uses-permission android:name=\"([^\"]+)\"\\s*/>").findAll(m).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "android.permission.WAKE_LOCK", "android.permission.INTERNET"), perms)
        assertTrue(m.contains("ACCESS_NETWORK_STATE\" tools:node=\"remove\""))
        assertTrue(m.contains("android:foregroundServiceType=\"mediaPlayback\""))
        assertTrue(m.contains("androidx.media3.session.MediaSessionService"))
        assertTrue(m.contains("android.intent.action.MEDIA_BUTTON"))
    }
}
