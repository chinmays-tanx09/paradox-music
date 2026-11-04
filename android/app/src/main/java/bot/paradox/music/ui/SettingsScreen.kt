package bot.paradox.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import bot.paradox.music.data.MusicFolder
import bot.paradox.music.data.ThemeMode

@Composable
fun SettingsScreen(
    folders: List<MusicFolder>, lost: Set<String>, libraryLine: String, scanning: Boolean, theme: ThemeMode, version: String,
    onBack: () -> Unit, onChange: () -> Unit, onAdd: () -> Unit, onRemove: (MusicFolder) -> Unit, onRescan: () -> Unit,
    onTheme: (ThemeMode) -> Unit, onChannel: () -> Unit, onUpdates: () -> Unit, updateLine: String, onCredits: () -> Unit,
    bottomPad: androidx.compose.ui.unit.Dp = 96.dp,
) {
    val p = LocalPal.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(Modifier.fillMaxSize().background(p.bg).verticalScroll(rememberScrollState()).padding(top = top + 4.dp, bottom = bottomPad, start = 18.dp, end = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BareIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = ts(28f, 800, p.text, -0.02f))
        }
        SectionLabel("Music folders")
        PCard(padding = 14.dp) {
            folders.forEach { f ->
                val gone = f.uri in lost
                ListRow(f.name.substringAfterLast('/'), if (gone) "Can't open this folder any more. Choose it again." else f.name,
                    leading = if (gone) Icons.Rounded.Warning else Icons.Rounded.Folder,
                    trailing = if (folders.size > 1) ({ BareIcon(Icons.Rounded.Close, "Remove folder", tint = p.faint, size = 40.dp, iconSize = 20.dp) { onRemove(f) } }) else ({}))
            }
            if (folders.isEmpty()) Body("No folder chosen.", Modifier.padding(8.dp))
            Spacer(Modifier.height(10.dp))
            PillButton("Change folder", Modifier.fillMaxWidth(), primary = false, icon = Icons.Rounded.FolderOpen, onClick = onChange)
            Spacer(Modifier.height(8.dp))
            PillButton("Add another folder", Modifier.fillMaxWidth(), primary = false, icon = Icons.Rounded.Add, onClick = onAdd)
        }
        SectionLabel("Library")
        PCard(padding = 14.dp) {
            ListRow("Rescan", if (scanning) "Scanning…" else libraryLine, leading = Icons.Rounded.Refresh, onClick = if (scanning) null else onRescan)
        }
        SectionLabel("Appearance")
        Segmented(listOf("System", "Light", "Dark"), theme.ordinal, onSelect = { onTheme(ThemeMode.entries[it]) })
        SectionLabel("About")
        PCard(padding = 14.dp) {
            ListRow("Paradox Music", "Version $version", leading = Icons.Rounded.Info)
            ListRow("Offline and private", "Plays only the files on your phone. No account, no tracking.", leading = Icons.Rounded.Lock)
            ListRow("Check for updates", updateLine, leading = Icons.Rounded.SystemUpdate, onClick = onUpdates)
            ListRow("Join Paradox on WhatsApp", "Updates and the other Paradox apps", leading = Icons.Rounded.Campaign, onClick = onChannel)
            ListRow("Made by Chinmay Shastry", "LinkedIn", leading = Icons.Rounded.Info, onClick = onCredits)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(1.dp).clip(CircleShape))
    }
}
