package bot.paradox.music.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.staticCompositionLocalOf
import bot.paradox.music.R

@Composable
fun PCard(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(p.surface)
            .then(if (!p.dark) Modifier.border(1.dp, p.hairline, RoundedCornerShape(28.dp)) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Text(text.uppercase(), modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp), style = ts(12f, 800, p.faint, 0.12f))
}

@Composable
fun H(text: String, size: Float = 17.5f, weight: Int = 750, modifier: Modifier = Modifier) {
    Text(text, modifier, style = ts(size, weight, LocalPal.current.text, -0.005f))
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, muted: Boolean = true, size: Float = 14.5f, center: Boolean = false, maxLines: Int = Int.MAX_VALUE) {
    val p = LocalPal.current
    Text(text, modifier, style = ts(size, 550, if (muted) p.muted else p.text), textAlign = if (center) TextAlign.Center else null, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    val bg = if (primary) p.primary else p.surface2
    val fg = if (primary) p.onPrimary else p.text
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = ts(15.5f, 750, if (enabled) fg else fg.copy(alpha = 0.6f)))
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, desc: String, size: Dp = 56.dp, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.size(size).clip(CircleShape).background(if (primary) p.primary else p.surface2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = (if (primary) p.onPrimary else p.text).copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(size * 0.45f)) }
}

@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface2).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) p.primary else Color.Transparent, tween(220), label = "seg")
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(18.dp)).background(bg).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, style = ts(13.5f, 750, if (sel) p.onPrimary else p.muted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp)) }
        }
    }
}

@Composable
fun ListRow(title: String, subtitle: String? = null, leading: ImageVector? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(p.surface2), contentAlignment = Alignment.Center) { Icon(leading, null, tint = p.text, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = ts(15.5f, 750, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = ts(13f, 550, p.muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.faint)
    }
}

@Composable
fun Hairline() { Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPal.current.hairline)) }


/** Off in screenshot tests: Robolectric can stall waiting for an infinite animation to idle. */
val LocalAnimate = staticCompositionLocalOf { true }

/**
 * The Paradox Music mark in College WiFi's orb language: a soft glow, two thin rings,
 * and the black disc carrying the headphones-and-wave glyph.
 */
@Composable
fun LogoOrb(modifier: Modifier = Modifier, size: Dp = 220.dp, active: Boolean = false) {
    val p = LocalPal.current
    val breath = if (LocalAnimate.current && active) {
        val inf = rememberInfiniteTransition(label = "orb")
        inf.animateFloat(0.8f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath").value
    } else 1f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2
            val g = (if (active) 0.2f else 0.1f) * breath
            drawCircle(Brush.radialGradient(listOf(p.primary.copy(alpha = g), p.primary.copy(alpha = g * 0.4f), Color.Transparent), c, r), r, c)
            drawCircle(p.primary.copy(alpha = 0.10f), r * 0.86f, c, style = Stroke(1.2.dp.toPx()))
            drawCircle(p.primary.copy(alpha = 0.18f), r * 0.68f, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(Color.Black, r * 0.5f, c)
            drawCircle(if (p.dark) Color(0x33FFFFFF) else Color(0x00000000), r * 0.5f, c, style = Stroke(1.dp.toPx()))
        }
        Image(painterResource(R.drawable.ic_logo_glyph), null, Modifier.size(size * 0.5f))
    }
}

/** Small round icon button without a fill (top bars). */
@Composable
fun BareIcon(icon: ImageVector, desc: String, tint: Color = LocalPal.current.text, size: Dp = 44.dp, iconSize: Dp = 24.dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(iconSize))
    }
}
