package bot.paradox.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** First launch, one screen: logo, "Choose your music folder", [Choose folder], the chosen folder, [Start]. */
@Composable
fun SetupScreen(folder: String?, onChoose: () -> Unit, onStart: () -> Unit, scanning: Boolean = false, scanText: String? = null, error: String? = null) {
    val p = LocalPal.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    Column(
        Modifier.fillMaxSize().background(p.bg).verticalScroll(rememberScrollState())
            .padding(top = bars.calculateTopPadding() + 12.dp, bottom = bars.calculateBottomPadding() + 20.dp, start = 22.dp, end = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))
        LogoOrb(size = 230.dp, active = scanning)
        Spacer(Modifier.height(6.dp))
        Text("Paradox Music", style = ts(32f, 850, p.text, -0.022f))
        Spacer(Modifier.height(6.dp))
        Body("Your music, offline. Nothing leaves your phone.", center = true, size = 15.5f)
        Spacer(Modifier.height(28.dp))
        PCard {
            Text("CHOOSE YOUR MUSIC FOLDER", style = ts(11.5f, 800, p.faint, 0.1f))
            Spacer(Modifier.height(4.dp))
            Body("Pick the main folder where your songs are. Every folder inside it is included.", size = 14f)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(p.surface2).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (folder != null) p.primary else p.bg), contentAlignment = Alignment.Center) {
                    Icon(if (folder != null) Icons.Rounded.Folder else Icons.Rounded.FolderOpen, null, tint = if (folder != null) p.onPrimary else p.faint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder?.substringAfterLast('/') ?: "No folder chosen", style = ts(15.5f, 750, if (folder != null) p.text else p.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (folder != null) Text(folder, style = ts(12.5f, 600, p.muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(14.dp))
            PillButton(if (folder == null) "Choose folder" else "Choose another", Modifier.fillMaxWidth(), primary = false, enabled = !scanning, icon = Icons.Rounded.FolderOpen, onClick = onChoose)
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, style = ts(14f, 700, p.text), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(22.dp))
        if (scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = p.text, trackColor = p.hairline)
            Spacer(Modifier.height(10.dp))
            Body(scanText ?: "Scanning your music…", center = true, size = 14f)
        } else {
            PillButton("Start", Modifier.fillMaxWidth(), enabled = folder != null, icon = Icons.Rounded.ArrowForward, onClick = onStart)
        }
    }
}
