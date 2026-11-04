package bot.paradox.music

import android.app.Application
import bot.paradox.music.data.Prefs
import bot.paradox.music.data.Repo
import bot.paradox.music.play.PlayerHub

class MusicApp : Application() {
    lateinit var prefs: Prefs
    lateinit var repo: Repo
    lateinit var hub: PlayerHub

    // Per-process flags for the popups (once per cold start, like the other Paradox apps).
    var channelShownThisRun = false
    var updateDismissedThisRun = false
    var introShownThisRun = false
    var rescannedThisRun = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repo = Repo(this, prefs)
        hub = PlayerHub(this, repo)
    }
}
