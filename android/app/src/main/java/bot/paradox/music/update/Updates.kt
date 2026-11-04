package bot.paradox.music.update

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val pageUrl: String, val apkUrl: String)

/**
 * The same update check as the other Paradox apps: GitHub releases of the app's repo, at most every 12 h,
 * silent on any error. A release counts when it has an .apk asset and a "versionCode: N" line in its notes,
 * so Windows releases in the same repo are ignored. This is the app's only network use.
 */
object Updates {
    const val API = "https://api.github.com/repos/chinmays-tanx09/paradox-music/releases"
    const val INTERVAL_MS = 12L * 60 * 60 * 1000
    private val VC = Regex("(?im)^\\s*\\**\\s*versionCode\\s*\\**\\s*[:=]\\s*\\**\\s*(\\d+)")

    fun versionCodeIn(body: String?): Int? = body?.let { VC.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    fun parse(json: String): UpdateInfo? = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (o.optBoolean("draft")) return@mapNotNull null
            val vc = versionCodeIn(o.optString("body")) ?: return@mapNotNull null
            val assets = o.optJSONArray("assets") ?: return@mapNotNull null
            val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", true) }?.optString("browser_download_url") ?: return@mapNotNull null
            val name = o.optString("name").ifBlank { o.optString("tag_name") }.ifBlank { "v$vc" }
            UpdateInfo(vc, name, o.optString("html_url").ifBlank { apk }, apk)
        }.maxByOrNull { it.versionCode }
    }.getOrNull()

    fun shouldCheck(now: Long, last: Long): Boolean = last <= 0 || now - last >= INTERVAL_MS || now < last
    fun shouldShow(info: UpdateInfo?, installed: Int, skipped: Int): Boolean = info != null && info.versionCode > installed && info.versionCode != skipped

    /** Blocking; call off the main thread. */
    fun fetch(): String? = runCatching {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "ParadoxMusic")
        try { if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null } finally { c.disconnect() }
    }.getOrNull()
}
