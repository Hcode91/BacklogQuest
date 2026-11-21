package com.backlogquest.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.backlogquest.app.MainActivity
import com.backlogquest.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** An upcoming game you've wishlisted from the Upcoming tab, to be reminded about. */
data class ReleaseWatch(val gameId: Long, val title: String, val platform: String, val date: Long) {
    val wishId: String get() = "igdb:$gameId"
    val key: String get() = "$gameId|$platform"
}

/**
 * Reminders the day before a wishlisted game comes out. A background job runs twice a day: it
 * asks IGDB for the games' current dates (so a delayed game isn't announced on its old date) and
 * posts a notification for any coming out tomorrow, or today if the day before was missed.
 *
 * What it has announced and the dates it last saw are kept in their own file, so the app saving
 * its settings at the same moment can't undo them.
 */
object ReleaseReminders {
    private const val WORK = "release-reminders"
    private const val CHANNEL = "releases"
    private const val FILE = "release-reminders.json"

    /** Starts the twice-daily check, or stops it when reminders are off. */
    fun schedule(ctx: Context, on: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (!on) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val req = PeriodicWorkRequestBuilder<Worker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** What the job has done: announced (key → date announced for) and the latest dates (key → date). */
    class Book(val announced: MutableMap<String, Long>, val dates: MutableMap<String, Long>)

    fun loadBook(dir: File): Book {
        val f = File(dir, FILE)
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return Book(HashMap(), HashMap())
        fun map(name: String) = HashMap<String, Long>().apply {
            o.optJSONObject(name)?.let { m -> m.keys().forEach { k -> put(k, m.optLong(k)) } }
        }
        return Book(map("announced"), map("dates"))
    }

    private fun saveBook(dir: File, b: Book) {
        val o = JSONObject().put("announced", JSONObject(b.announced as Map<*, *>)).put("dates", JSONObject(b.dates as Map<*, *>))
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(File(dir, FILE))
    }

    /** A release's day. IGDB gives dates as midnight UTC, so the day is read in UTC. */
    fun releaseDay(date: Long): LocalDate = Instant.ofEpochSecond(date).atZone(ZoneOffset.UTC).toLocalDate()

    /** "TOMORROW", "TODAY" or null: whether [date] is due a reminder on [today]. */
    fun due(date: Long, today: LocalDate): String? = when (releaseDay(date)) {
        today.plusDays(1) -> "TOMORROW"
        today -> "TODAY"
        else -> null
    }

    /** The games to remind about: still wishlisted (not hidden), one entry per game and platform. */
    fun watched(s: AppState): List<ReleaseWatch> = s.releaseWatch.filter { it.wishId !in s.hiddenWish }.distinctBy { it.key }

    class Worker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            val dir = applicationContext.filesDir
            val s = Store.loadState(dir) ?: return Result.success()
            if (!s.releaseReminders) return Result.success()
            val watch = watched(s)
            if (watch.isEmpty()) return Result.success()
            val book = loadBook(dir)

            // Current dates from IGDB. If that fails, the dates already known are used.
            runCatching { refreshDates(s, watch, book) }

            val today = LocalDate.now(ZoneId.systemDefault())
            for (w in watch) {
                val date = book.dates[w.key] ?: w.date
                val whenText = due(date, today) ?: continue
                if (book.announced[w.key] == date) continue
                if (!canNotify(applicationContext)) break
                notify(applicationContext, w, whenText)
                book.announced[w.key] = date
            }
            saveBook(dir, book)
            return Result.success()
        }

        private fun refreshDates(s: AppState, watch: List<ReleaseWatch>, book: Book) {
            if (s.igdbClientId.isBlank() || s.igdbSecret.isBlank()) return
            val token = s.igdbToken.takeIf { it.isNotBlank() && s.igdbTokenExpires > System.currentTimeMillis() }
                ?: Igdb.token(s.igdbClientId, s.igdbSecret).value
            val matcher = Platforms.Matcher(s.platforms)
            val rows = Igdb.datesFor(s.igdbClientId, token, watch.map { it.gameId }.distinct())
            for (w in watch) {
                val dates = rows.filter { it.gameId == w.gameId && matcher.of(it.platform) == w.platform }.map { it.date }
                // The first date on that platform that hasn't long passed (a game can have
                // early-access and full-release dates).
                val now = System.currentTimeMillis() / 1000
                val next = dates.filter { it >= now - 2 * 86_400 }.minOrNull() ?: dates.maxOrNull() ?: continue
                book.dates[w.key] = next
            }
        }
    }

    private fun notify(ctx: Context, w: ReleaseWatch, whenText: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Release reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "The day before a game on your wishlist comes out"
                },
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (whenText == "TODAY") "Out today: ${w.title}" else "Out tomorrow: ${w.title}")
            .setContentText("On ${w.platform}, from your wishlist")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(w.key.hashCode(), n)
        } catch (_: SecurityException) {
            // Notifications were turned off for the app in the meantime.
        }
    }
}

/** Parses a saved list of reminders. */
internal fun releaseWatchFrom(a: JSONArray?): List<ReleaseWatch> =
    if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
        a.optJSONObject(i)?.let { o -> ReleaseWatch(o.optLong("gameId"), o.optString("title"), o.optString("platform"), o.optLong("date")) }
    }

internal fun releaseWatchTo(list: List<ReleaseWatch>): JSONArray = JSONArray().apply {
    list.forEach { w -> put(JSONObject().put("gameId", w.gameId).put("title", w.title).put("platform", w.platform).put("date", w.date)) }
}
