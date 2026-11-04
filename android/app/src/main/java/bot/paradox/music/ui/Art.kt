package bot.paradox.music.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Where covers live (the repository's art folder). */
val LocalArtDir = staticCompositionLocalOf<File?> { null }

/** Decoded covers, kept small: list thumbnails and full-size covers are cached separately. */
object ArtCache {
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 10).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private fun key(f: File, px: Int) = f.name + "@" + (if (px <= 200) 200 else 700)

    fun peek(f: File, px: Int): ImageBitmap? = cache.get(key(f, px))

    fun load(f: File, px: Int): ImageBitmap? {
        cache.get(key(f, px))?.let { return it }
        if (!f.exists()) return null
        val target = if (px <= 200) 200 else 700
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= target && o.outHeight / (s * 2) >= target) s *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        return bmp.asImageBitmap().also { cache.put(key(f, px), it) }
    }
}

/** A cover: the song's art, or a calm monochrome tile with a note when there is none. */
@Composable
fun Cover(art: String?, size: Dp, shape: Shape = RoundedCornerShape(size * 0.22f), modifier: Modifier = Modifier, fill: Boolean = false) {
    val p = LocalPal.current
    val dir = LocalArtDir.current
    val file = if (art != null && dir != null) File(dir, art) else null
    val px = (size.value * 2.75f).toInt()
    var img by remember(file?.path, px) { mutableStateOf(file?.let { ArtCache.peek(it, px) }) }
    if (img == null && file != null) LaunchedEffect(file.path, px) { img = withContext(Dispatchers.IO) { ArtCache.load(file, px) } }
    val base = (if (fill) modifier.fillMaxSize() else modifier.size(size)).clip(shape)
    val b = img
    if (b != null) Image(b, null, base, contentScale = ContentScale.Crop)
    else Box(base.background(p.surface2), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = p.faint, modifier = Modifier.size(size * 0.38f))
    }
}
