package bot.paradox.music

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bot.paradox.music.data.AutoLists
import bot.paradox.music.data.Mix
import bot.paradox.music.data.Mixer
import bot.paradox.music.data.Playlist
import bot.paradox.music.data.SmartEngine
import bot.paradox.music.data.SmartPlaylist
import bot.paradox.music.data.ThemeMode
import bot.paradox.music.ui.*
import bot.paradox.music.update.UpdateInfo
import bot.paradox.music.update.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Route {
    data object Library : Route
    data class AlbumR(val key: String) : Route
    data class ArtistR(val name: String) : Route
    data class PlaylistR(val id: Long) : Route
    /** A smart list: "auto:…", "genre:…", "decade:…" or "smart:<id>". */
    data class SmartR(val key: String) : Route
    data class SmartEditR(val id: Long?) : Route
    data object MixR : Route
    data object Settings : Route
}

private enum class PickMode { SETUP, CHANGE, ADD }

class MainActivity : ComponentActivity() {
    private val app get() = application as MusicApp
    private var pickMode = PickMode.SETUP
    private var onPicked: (Uri) -> Unit = {}
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) onPicked(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        app.repo.loadAsync()
        setContent {
            var theme by remember { mutableStateOf(app.prefs.theme) }
            MusicTheme(theme) {
                CompositionLocalProvider(LocalArtDir provides app.repo.artDir) {
                    Root(theme) { theme = it; app.prefs.theme = it }
                }
            }
        }
    }

    override fun onStart() { super.onStart(); app.hub.connect() }
    override fun onStop() { app.hub.disconnect(); super.onStop() }

    private fun pick(mode: PickMode, then: (Uri) -> Unit) {
        pickMode = mode; onPicked = then
        runCatching { picker.launch(null) }.onFailure { toast("No folder picker on this phone") }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun web(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
            .onFailure { toast("No browser found") }
    }

    @Composable
    private fun Root(theme: ThemeMode, setTheme: (ThemeMode) -> Unit) {
        val repo = app.repo
        val hub = app.hub
        val prefs = app.prefs
        val scope = rememberCoroutineScope()

        // ---- Paradox intro: first install and once after each update.
        var intro by remember { mutableStateOf(prefs.introVc != BuildConfig.VERSION_CODE && !app.introShownThisRun) }
        if (intro) {
            IntroVideo { app.introShownThisRun = true; prefs.introVc = BuildConfig.VERSION_CODE; intro = false }
            return
        }

        val index by repo.index.collectAsStateWithLifecycle()
        val scan by repo.scan.collectAsStateWithLifecycle()
        val folders by repo.folders.collectAsStateWithLifecycle()
        val lost by repo.lost.collectAsStateWithLifecycle()
        val playlists by repo.playlists.collectAsStateWithLifecycle()
        val stats by repo.stats.collectAsStateWithLifecycle()
        val smart by repo.smart.collectAsStateWithLifecycle()
        val ps by hub.state.collectAsStateWithLifecycle()
        val scanText = scan.progress?.let { "Scanning… ${songs(it.filesFound)} found" }

        // ---- First launch: choose the folder, Start, scan.
        var setupDone by remember { mutableStateOf(prefs.setupDone) }
        if (!setupDone) {
            var chosen by remember { mutableStateOf<Uri?>(null) }
            var error by remember { mutableStateOf<String?>(null) }
            SetupScreen(chosen?.let { repo.folderFor(it).name }, onChoose = {
                pick(PickMode.SETUP) { u -> if (repo.takePermission(u)) { chosen = u; error = null } else toast("Couldn't open that folder") }
            }, onStart = {
                val u = chosen ?: return@SetupScreen
                repo.replaceFolders(u)
                repo.rescan {
                    if (repo.index.value.tracks.isEmpty()) error = "No songs found in that folder. Try the folder that holds your music."
                    else { prefs.setupDone = true; setupDone = true }
                }
            }, scanning = scan.running, scanText = scanText, error = error)
            return
        }

        // Quick refresh once per launch: only new or changed files are read.
        LaunchedEffect(Unit) { if (!app.rescannedThisRun) { app.rescannedThisRun = true; repo.rescan() } }

        val stack = remember { mutableStateListOf<Route>(Route.Library) }
        var nowOpen by remember { mutableStateOf(false) }
        var queueOpen by remember { mutableStateOf(false) }
        var tab by remember { mutableIntStateOf(prefs.tab) }
        var sorts by remember { mutableStateOf(Sorts(prefs.songSort, prefs.albumSort, prefs.artistSort)) }
        var addTo by remember { mutableStateOf<List<String>?>(null) }
        var naming by remember { mutableStateOf<Pair<Playlist?, List<String>>?>(null) }  // (playlist to rename | null for new, songs to add)
        var deleting by remember { mutableStateOf<Playlist?>(null) }
        var from by remember { mutableStateOf("Library") }
        var mix by remember { mutableStateOf<Mix?>(null) }
        var saveAs by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
        var deletingSmart by remember { mutableStateOf<SmartPlaylist?>(null) }
        val likes = remember(stats) { stats.filterValues { it.liked }.keys }
        // One clock per minute is enough for "added in the last N days" and "2 h ago".
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }

        fun label(r: Route): String = when (r) {
            Route.Library, Route.Settings -> "Library"
            is Route.AlbumR -> repo.index.value.albumByKey[r.key]?.title ?: "Album"
            is Route.ArtistR -> r.name
            is Route.PlaylistR -> repo.playlists.value.firstOrNull { it.id == r.id }?.name ?: "Playlist"
            is Route.SmartR -> if (r.key.startsWith("smart:")) repo.smart.value.firstOrNull { "smart:${it.id}" == r.key }?.name ?: "Smart playlist"
                else AutoLists.byKey(r.key, repo.index.value.tracks)?.name ?: "Smart playlist"
            is Route.SmartEditR -> "Smart playlist"
            Route.MixR -> mix?.name ?: "Mix"
        }
        fun openMix(m: Mix) {
            if (m.trackIds.isEmpty()) { toast("Nothing to mix yet"); return }
            mix = m; nowOpen = false; queueOpen = false
            if (stack.last() != Route.MixR) stack.add(Route.MixR)
        }
        val actions = remember {
            Actions(
                play = { ids, i -> from = label(stack.last()); hub.playTracks(ids, i) },
                shuffle = { ids -> from = label(stack.last()); hub.shuffleAll(ids) },
                playNext = { ids -> hub.playNext(ids) },
                addToQueue = { ids -> hub.addToQueue(ids) },
                addToPlaylist = { ids -> addTo = ids },
                openAlbum = { k -> nowOpen = false; queueOpen = false; stack.add(Route.AlbumR(k)) },
                openArtist = { n -> nowOpen = false; queueOpen = false; stack.add(Route.ArtistR(n)) },
                openPlaylist = { id -> stack.add(Route.PlaylistR(id)) },
                toast = { toast(it) },
                toggleLike = { id -> toast(if (repo.toggleLike(id)) "Added to Favorites" else "Removed from Favorites") },
                mixSong = { t -> openMix(Mixer.fromSong(t, repo.index.value.tracks, repo.stats.value, System.nanoTime())) },
                mixArtist = { n -> openMix(Mixer.fromArtist(n, repo.index.value.tracks, repo.stats.value, System.nanoTime())) },
            )
        }
        val smartActions = remember {
            bot.paradox.music.ui.SmartActions(
                open = { k -> stack.add(Route.SmartR(k)) },
                new = { stack.add(Route.SmartEditR(null)) },
                edit = { sp -> stack.add(Route.SmartEditR(sp.id)) },
                delete = { sp -> deletingSmart = sp },
                saveAs = { name, ids -> if (ids.isEmpty()) toast("Nothing to save yet") else saveAs = name to ids },
            )
        }
        val controls = remember {
            PlayerControls(toggle = hub::toggle, next = hub::next, previous = hub::previous, seek = hub::seekTo,
                shuffle = hub::toggleShuffle, repeat = hub::cycleRepeat, jump = hub::jumpTo, remove = hub::removeAt, clearUpNext = hub::clearUpNext)
        }

        // Position ticker while something is loaded.
        var pos by remember { mutableLongStateOf(0L) }
        LaunchedEffect(ps.playing, ps.queue.current, nowOpen) {
            pos = hub.positionMs()
            while (ps.playing) { delay(if (nowOpen) 250 else 1000); pos = hub.positionMs() }
        }

        // ---- Channel popup (once per launch unless "Don't show again") and update check (every 12 h).
        var showChannel by remember { mutableStateOf(!prefs.channelNever && !app.channelShownThisRun) }
        val channelUrl = "https://whatsapp.com/channel/0029Vb96Yl6IHphOLQa5H10O"
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        var updateLine by remember { mutableStateOf("Version ${BuildConfig.VERSION_NAME}") }
        LaunchedEffect(Unit) {
            if (app.updateDismissedThisRun) return@LaunchedEffect
            val cached = prefs.cachedRelease?.let { Updates.parse(it) }
            if (Updates.shouldShow(cached, BuildConfig.VERSION_CODE, prefs.skippedVersion)) update = cached
            if (Updates.shouldCheck(System.currentTimeMillis(), prefs.lastUpdateCheck)) {
                val json = withContext(Dispatchers.IO) { Updates.fetch() }
                if (json != null) {
                    prefs.lastUpdateCheck = System.currentTimeMillis(); prefs.cachedRelease = json
                    update = Updates.parse(json).takeIf { Updates.shouldShow(it, BuildConfig.VERSION_CODE, prefs.skippedVersion) }
                }
            }
        }
        fun checkNow() {
            updateLine = "Checking…"
            scope.launch {
                val json = withContext(Dispatchers.IO) { Updates.fetch() }
                if (json == null) { updateLine = "Couldn't check. Are you online?"; return@launch }
                prefs.lastUpdateCheck = System.currentTimeMillis(); prefs.cachedRelease = json
                val info = Updates.parse(json)
                if (info != null && info.versionCode > BuildConfig.VERSION_CODE) { update = info; updateLine = "${info.versionName} is available"; web(info.pageUrl) }
                else updateLine = "You're on the latest version"
            }
        }

        val current = ps.currentTrackId?.let { index.byId[it] }
        val bottomPad = if (current != null) 104.dp else 24.dp
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        BackHandler(queueOpen || nowOpen || stack.size > 1) {
            when { queueOpen -> queueOpen = false; nowOpen -> nowOpen = false; else -> stack.removeAt(stack.lastIndex) }
        }

        CompositionLocalProvider(LocalLikes provides likes) {
        Box(Modifier.fillMaxSize()) {
            when (val r = stack.last()) {
                Route.Library -> LibraryScreen(index, playlists, ps.currentTrackId, tab, { tab = it; prefs.tab = it }, sorts,
                    { sorts = it; prefs.songSort = it.song; prefs.albumSort = it.album; prefs.artistSort = it.artist },
                    actions, onSettings = { stack.add(Route.Settings) },
                    onNewPlaylist = { naming = null to emptyList() }, onRenamePlaylist = { naming = it to emptyList() }, onDeletePlaylist = { deleting = it },
                    scanning = if (scan.running) scanText ?: "Scanning…" else null,
                    banner = update?.let { u -> {
                        UpdateCard(u.versionName, onDownload = { web(u.pageUrl); app.updateDismissedThisRun = true; update = null },
                            onLater = { app.updateDismissedThisRun = true; update = null }, onSkip = { prefs.skippedVersion = u.versionCode; update = null })
                    } }, bottomPad = bottomPad + navBottom, smart = smart, stats = stats, smartActions = smartActions, now = now)
                is Route.AlbumR -> index.albumByKey[r.key]?.let { al -> AlbumScreen(al, ps.currentTrackId, actions, { stack.removeAt(stack.lastIndex) }, bottomPad + navBottom) }
                    ?: LaunchedEffect(r) { stack.removeAt(stack.lastIndex) }
                is Route.ArtistR -> index.artists.firstOrNull { it.name == r.name }?.let { ar -> ArtistScreen(ar, ps.currentTrackId, actions, { stack.removeAt(stack.lastIndex) }, bottomPad + navBottom) }
                    ?: LaunchedEffect(r) { stack.removeAt(stack.lastIndex) }
                is Route.PlaylistR -> playlists.firstOrNull { it.id == r.id }?.let { pl ->
                    PlaylistScreen(pl, pl.trackIds.map { index.byId[it] }, ps.currentTrackId, actions, { stack.removeAt(stack.lastIndex) },
                        onRename = { naming = pl to emptyList() }, onDelete = { deleting = pl }, onRemoveAt = { repo.removeFromPlaylist(pl.id, it) }, bottomPad = bottomPad + navBottom)
                } ?: LaunchedEffect(r) { stack.removeAt(stack.lastIndex) }
                is Route.SmartR -> {
                    val saved = if (r.key.startsWith("smart:")) smart.firstOrNull { smartKey(it) == r.key } else null
                    val auto = if (saved == null) remember(r.key, index) { AutoLists.byKey(r.key, index.tracks) } else null
                    val rule = saved?.rule ?: auto?.rule
                    if (rule == null) LaunchedEffect(r) { stack.removeAt(stack.lastIndex) }
                    else {
                        val list = remember(rule, index, stats, now) { SmartEngine.evaluate(rule, index.tracks, stats, now, seed = saved?.id ?: 7) }
                        SmartScreen(saved?.name ?: auto!!.name, auto?.kind, rule, list, stats, now, ps.currentTrackId, actions, smartActions,
                            { stack.removeAt(stack.lastIndex) }, saved, bottomPad + navBottom)
                    }
                }
                is Route.SmartEditR -> {
                    val initial = r.id?.let { id -> smart.firstOrNull { it.id == id } }
                    if (r.id != null && initial == null) LaunchedEffect(r) { stack.removeAt(stack.lastIndex) }
                    else SmartEditor(initial, index, stats, now, onSave = { name, rule ->
                        stack.removeAt(stack.lastIndex)
                        if (initial == null) { val n = repo.createSmart(name, rule); stack.add(Route.SmartR(smartKey(n))) }
                        else repo.updateSmart(initial.id, name, rule)
                    }, onDelete = initial?.let { sp -> { deletingSmart = sp } }, onBack = { stack.removeAt(stack.lastIndex) })
                }
                Route.MixR -> mix?.let { m ->
                    MixScreen(m.name, m.seedTitle, m.trackIds.mapNotNull { index.byId[it] }, ps.currentTrackId, actions, { stack.removeAt(stack.lastIndex) },
                        onSave = { saveAs = m.name to m.trackIds },
                        onReroll = {
                            val seed = m.seedTrackId?.let { index.byId[it] }
                            val next = if (seed != null) Mixer.fromSong(seed, index.tracks, stats, System.nanoTime())
                                else Mixer.fromArtist(m.seedTitle, index.tracks, stats, System.nanoTime())
                            mix = next
                        }, bottomPad = bottomPad + navBottom)
                } ?: LaunchedEffect(r) { stack.removeAt(stack.lastIndex) }
                Route.Settings -> SettingsScreen(folders, lost,
                    libraryLine = countLine(index.tracks.size, index.albums.size) + " · " + folders.size + if (folders.size == 1) " folder" else " folders",
                    scanning = scan.running, theme = theme, version = BuildConfig.VERSION_NAME, onBack = { stack.removeAt(stack.lastIndex) },
                    onChange = { pick(PickMode.CHANGE) { u -> if (repo.replaceFolders(u)) repo.rescan { toast("Library updated: ${songs(repo.index.value.tracks.size)}") } } },
                    onAdd = { pick(PickMode.ADD) { u -> if (repo.addFolder(u)) repo.rescan { toast("Library updated: ${songs(repo.index.value.tracks.size)}") } } },
                    onRemove = { repo.removeFolder(it) },
                    onRescan = { repo.rescan { toast("Library updated: ${songs(repo.index.value.tracks.size)}") } },
                    onTheme = setTheme, onChannel = { web(channelUrl) }, onUpdates = ::checkNow, updateLine = updateLine,
                    onCredits = { web("https://www.linkedin.com/in/chinmay-shastry-a0a76b324") }, bottomPad = bottomPad + navBottom)
            }

            if (current != null) {
                val dur = ps.durationMs.takeIf { it > 0 } ?: current.durationMs
                MiniPlayer(current, ps.playing, if (dur > 0) pos.toFloat() / dur else 0f, controls, { nowOpen = true },
                    Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = navBottom + 10.dp))
            }

            AnimatedVisibility(nowOpen && current != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                val t = current ?: return@AnimatedVisibility
                val upNext = ps.queue.entries.getOrNull(ps.queue.index + 1)?.let { index.byId[it.trackId] }
                    ?: if (ps.repeat == bot.paradox.music.play.Repeat.ALL) ps.queue.entries.firstOrNull()?.let { index.byId[it.trackId] } else null
                NowPlayingScreen(t, ps.playing, pos, ps.durationMs.takeIf { it > 0 } ?: t.durationMs, ps.queue.shuffled, ps.repeat,
                    upNext, from, controls, onClose = { nowOpen = false }, onQueue = { queueOpen = true },
                    liked = t.id in likes, onLike = { actions.toggleLike(t.id) })
            }
            AnimatedVisibility(queueOpen, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                QueueScreen(ps.queue.entries.map { index.byId[it.trackId] }, ps.queue.index, controls) { queueOpen = false }
            }
        }
        }

        addTo?.let { ids ->
            AddToPlaylistDialog(playlists, onPick = { pl -> repo.addToPlaylist(pl.id, ids); toast("Added to ${pl.name}"); addTo = null },
                onNew = { naming = null to ids; addTo = null }, onDismiss = { addTo = null })
        }
        naming?.let { (pl, ids) ->
            NameDialog(if (pl == null) "New playlist" else "Rename playlist", pl?.name ?: "", if (pl == null) "Create" else "Save", onDone = { name ->
                if (pl == null) { val n = repo.createPlaylist(name, ids); if (ids.isNotEmpty()) toast("Added to ${n.name}") } else repo.renamePlaylist(pl.id, name)
                naming = null
            }, onDismiss = { naming = null })
        }
        saveAs?.let { (name, ids) ->
            NameDialog("Save as playlist", name, "Save", onDone = { n ->
                val pl = repo.createPlaylist(n, ids); toast("Saved as ${pl.name}"); saveAs = null
            }, onDismiss = { saveAs = null })
        }
        deletingSmart?.let { sp ->
            ConfirmDialog("Delete “${sp.name}”?", "Only the rules are deleted. Your songs stay in your library.", "Delete", onConfirm = {
                repo.deleteSmart(sp.id); deletingSmart = null
                while (stack.size > 1 && stack.last().let { it is Route.SmartEditR && it.id == sp.id || it is Route.SmartR && it.key == smartKey(sp) }) stack.removeAt(stack.lastIndex)
            }, onDismiss = { deletingSmart = null })
        }
        deleting?.let { pl ->
            ConfirmDialog("Delete “${pl.name}”?", "The songs stay in your library.", "Delete", onConfirm = {
                repo.deletePlaylist(pl.id); deleting = null
                if ((stack.last() as? Route.PlaylistR)?.id == pl.id) stack.removeAt(stack.lastIndex)
            }, onDismiss = { deleting = null })
        }
        if (showChannel) ChannelDialog(
            onJoin = { app.channelShownThisRun = true; showChannel = false; web(channelUrl) },
            onLater = { app.channelShownThisRun = true; showChannel = false },
            onNever = { app.channelShownThisRun = true; prefs.channelNever = true; showChannel = false },
        )
    }

    /** The 2-second Paradox intro on black; tap to skip; never blocks the app if the video can't play. */
    @Composable
    private fun IntroVideo(onDone: () -> Unit) {
        var done by remember { mutableStateOf(false) }
        fun finish() { if (!done) { done = true; onDone() } }
        LaunchedEffect(Unit) { delay(6000); finish() }
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { finish() }, contentAlignment = Alignment.Center) {
            AndroidView({ ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse("android.resource://${ctx.packageName}/${R.raw.paradox_intro}"))
                    setOnPreparedListener { it.setVolume(0f, 0f); start() }
                    setOnCompletionListener { finish() }
                    setOnErrorListener { _, _, _ -> finish(); true }
                }
            }, Modifier.fillMaxSize())
        }
    }
}
