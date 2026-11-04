package bot.paradox.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import bot.paradox.music.data.Playlist

@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val p = LocalPal.current
        Box(Modifier.fillMaxSize().background(p.scrim).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(24.dp).fillMaxWidth().widthIn(max = 480.dp).clip(RoundedCornerShape(30.dp)).background(p.surface).clickable(enabled = false) {}.padding(24.dp), content = content)
        }
    }
}

/** The WhatsApp channel invite, as in College WiFi: monochrome card, three buttons, once per launch. */
@Composable
fun ChannelDialog(onJoin: () -> Unit, onLater: () -> Unit, onNever: () -> Unit) {
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) { ChannelCard(onJoin, onLater, onNever) }
}

@Composable
fun ChannelCard(onJoin: () -> Unit, onLater: () -> Unit, onNever: () -> Unit) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize().background(p.scrim), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(p.surface).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(p.primary), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Campaign, null, tint = p.onPrimary, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("Join Paradox on WhatsApp", style = ts(21f, 850, p.text))
            Spacer(Modifier.height(6.dp))
            Body("Updates and the rest of the Paradox apps, first on the channel.", center = true, size = 14f)
            Spacer(Modifier.height(20.dp))
            PillButton("Join channel", Modifier.fillMaxWidth(), onClick = onJoin)
            Spacer(Modifier.height(10.dp))
            PillButton("Later", Modifier.fillMaxWidth(), primary = false, onClick = onLater)
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onNever) { Text("Don't show again", style = ts(14f, 750, p.muted)) }
        }
    }
}

/** In-app update notice. Download opens the release page in the browser. */
@Composable
fun UpdateCard(versionName: String, onDownload: () -> Unit, onLater: () -> Unit, onSkip: () -> Unit) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(p.surface).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(p.primary), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SystemUpdate, null, tint = p.onPrimary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Update available", style = ts(16.5f, 800, p.text))
                Text("Paradox Music $versionName", style = ts(13f, 650, p.muted))
            }
        }
        Spacer(Modifier.height(14.dp))
        PillButton("Download", Modifier.fillMaxWidth(), icon = Icons.Rounded.Download, onClick = onDownload)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Later", Modifier.weight(1f), primary = false, onClick = onLater)
            PillButton("Skip this version", Modifier.weight(1.4f), primary = false, onClick = onSkip)
        }
    }
}

/** Name a playlist (new or rename). */
@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPal.current
    var v by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    Sheet(onDismiss) {
        Text(title, style = ts(21f, 850, p.text))
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface2).padding(16.dp)) {
            if (v.text.isEmpty()) Text("Playlist name", style = ts(16f, 600, p.faint))
            BasicTextField(v, { v = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true, textStyle = ts(16f, 700, p.text), cursorBrush = SolidColor(p.text))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Cancel", Modifier.weight(1f), primary = false, onClick = onDismiss)
            PillButton(confirm, Modifier.weight(1f), enabled = v.text.isNotBlank()) { onDone(v.text.trim()) }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPal.current
    Sheet(onDismiss) {
        Text(title, style = ts(21f, 850, p.text))
        Spacer(Modifier.height(8.dp))
        Body(body)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Cancel", Modifier.weight(1f), primary = false, onClick = onDismiss)
            PillButton(confirm, Modifier.weight(1f), onClick = onConfirm)
        }
    }
}

/** Pick a playlist for the given songs, or make a new one. */
@Composable
fun AddToPlaylistDialog(playlists: List<Playlist>, onPick: (Playlist) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPal.current
    Sheet(onDismiss) {
        Text("Add to playlist", style = ts(21f, 850, p.text))
        Spacer(Modifier.height(10.dp))
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            ListRow("New playlist", leading = Icons.Rounded.Add, onClick = onNew)
            playlists.forEach { pl -> ListRow(pl.name, songs(pl.trackIds.size), onClick = { onPick(pl) }) }
        }
        Spacer(Modifier.height(8.dp))
        PillButton("Cancel", Modifier.fillMaxWidth(), primary = false, onClick = onDismiss)
    }
}
