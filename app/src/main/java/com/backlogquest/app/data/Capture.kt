package com.backlogquest.app.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import com.backlogquest.app.CaptureService

/**
 * Auto screenshots while you play (what the separate AppCapture app did, built in).
 * When you start a game from Backlog Quest, its emulator or app is remembered here
 * along with the game's title. CaptureService then saves a screenshot every N seconds
 * while that app is on screen, named after the game.
 */
object Capture {
    const val MIN_INTERVAL = 5
    const val DEFAULT_INTERVAL = 30

    /** Shots stop once you've been away from the game this long without coming back. */
    const val STALE_AFTER_MS = 30 * 60_000L

    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences("capture", Context.MODE_PRIVATE)

    /** Screenshots need Android 11 or newer (the accessibility screenshot call). */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, on: Boolean) = sp(c).edit().putBoolean("enabled", on).apply()

    fun intervalSeconds(c: Context): Int = sp(c).getInt("interval", DEFAULT_INTERVAL).coerceAtLeast(MIN_INTERVAL)
    fun setIntervalSeconds(c: Context, s: Int) = sp(c).edit().putInt("interval", s.coerceAtLeast(MIN_INTERVAL)).apply()

    /** The game being played: the package to watch and the title to name shots after. */
    class Session(val pkg: String, val title: String)

    fun session(c: Context): Session? {
        val p = sp(c)
        val pkg = p.getString("pkg", null) ?: return null
        return Session(pkg, p.getString("title", null).orEmpty().ifBlank { pkg })
    }

    /** Called when a game is started from Backlog Quest. */
    fun begin(c: Context, pkg: String, title: String) {
        sp(c).edit().putString("pkg", pkg).putString("title", title).apply()
    }

    /** Called when the play session is over (you came back and the time was worked out). */
    fun end(c: Context) {
        sp(c).edit().remove("pkg").remove("title").apply()
    }

    /** True when the screenshot service is switched on in Accessibility settings. */
    fun serviceOn(c: Context): Boolean {
        val want = ComponentName(c, CaptureService::class.java)
        val list = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return list.split(':').any { ComponentName.unflattenFromString(it) == want }
    }

    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** App info page, where "Allow restricted settings" lives for sideloaded apps. */
    fun appInfoIntent(c: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${c.packageName}"))

    /** Game titles become folder and file names, so keep them file-safe. */
    fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(80).ifEmpty { "Game" }
}
