package com.backlogquest.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.backlogquest.app.data.Capture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * While a game you started from Backlog Quest is on screen, saves a screenshot
 * every N seconds (set in Setup → Screenshots) to
 * Pictures/BacklogQuest/<Game>/<Game>_yyyy-MM-dd_HH-mm-ss.jpg.
 */
@SuppressLint("NewApi") // every screenshot call is behind Capture.supported (Android 11+)
class CaptureService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var io: ExecutorService

    /** True while the game started from Backlog Quest is the app on screen. */
    private var inGame = false
    private var title = ""
    private var shotCount = 0
    private var shotsKey: String? = null
    private var leftGameAt = 0L
    private var lastProblem: String? = null

    /**
     * Runs on its own timer rather than relying on window events: overlays from other apps
     * (Game Booster, floating controls, toasts) send events too, and used to make it think
     * you'd left the game. Each pass checks which app is really on screen.
     */
    private val loop = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            handler.postDelayed(this, pass())
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        io = Executors.newSingleThreadExecutor()
        createChannel()
        handler.post(loop)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Only used to notice a game starting straight away; the loop does the rest.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!inGame && Capture.session(this)?.pkg == event.packageName?.toString()) {
            handler.removeCallbacks(loop)
            handler.post(loop)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        leaveGame()
        if (::io.isInitialized) io.shutdown()
        super.onDestroy()
    }

    /** One check (and maybe one screenshot). Returns how long to wait before the next pass. */
    private fun pass(): Long {
        val session = Capture.session(this)
        if (!Capture.supported || !Capture.enabled(this) || session == null) {
            leaveGame()
            leftGameAt = 0
            return IDLE_CHECK_MS
        }
        val onScreen = topAppPackage()
        val now = System.currentTimeMillis()
        if (onScreen == null) {
            // Couldn't tell (e.g. screen off): keep going as before.
            if (inGame) capture(session.title)
            return if (inGame) intervalMs() else AWAY_CHECK_MS
        }
        if (onScreen != session.pkg) {
            if (inGame) {
                leaveGame()
                leftGameAt = now
            } else if (leftGameAt > 0 && now - leftGameAt > Capture.STALE_AFTER_MS) {
                // Away for ages without coming back to Backlog Quest: the session is over, so
                // opening the emulator by itself later isn't named after the old game.
                Capture.end(this)
                leftGameAt = 0
            }
            return AWAY_CHECK_MS
        }
        leftGameAt = 0
        if (!inGame) {
            enterGame(session)
            return FIRST_SHOT_DELAY_MS // give the game a moment to draw
        }
        capture(session.title)
        return intervalMs()
    }

    private fun intervalMs() = Capture.intervalSeconds(this) * 1000L

    /**
     * The app whose window is on top, ignoring keyboards, the notification shade and other
     * apps' floating overlays (those aren't application windows). Null if it can't be read.
     */
    private fun topAppPackage(): String? = try {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val top = apps.maxByOrNull { it.layer }
        top?.root?.packageName?.toString()
    } catch (e: Exception) {
        null
    }

    private fun enterGame(session: Capture.Session) {
        inGame = true
        title = session.title
        val key = session.pkg + "|" + session.title
        if (shotsKey != key) {
            shotsKey = key
            shotCount = 0
            lastProblem = null
        }
        Log.i(TAG, "In game: ${session.title} (${session.pkg})")
        updateNotification()
    }

    private fun leaveGame() {
        if (!inGame) return
        inGame = false
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun capture(gameTitle: String) {
        // Skip while the screen is off or locked.
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return

        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hw = result.hardwareBuffer
                val bitmap = try {
                    Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                        ?.let { hwBmp -> hwBmp.copy(Bitmap.Config.ARGB_8888, false).also { hwBmp.recycle() } }
                } finally {
                    hw.close()
                }
                if (bitmap == null) {
                    report("couldn't read the screenshot")
                    return
                }
                io.execute {
                    val ok = save(bitmap, gameTitle)
                    bitmap.recycle()
                    handler.post {
                        if (ok) {
                            shotCount++
                            lastProblem = null
                            Capture.setLastResult(this@CaptureService, "Saved shot $shotCount of $gameTitle")
                            updateNotification()
                        } else {
                            report("couldn't save to Pictures")
                        }
                    }
                }
            }

            override fun onFailure(errorCode: Int) {
                report(failureText(errorCode))
            }
        })
    }

    private fun report(problem: String) {
        Log.w(TAG, "Screenshot failed: $problem")
        lastProblem = problem
        Capture.setLastResult(this, "Last shot failed: $problem")
        updateNotification()
    }

    private fun failureText(code: Int) = when (code) {
        1 -> "Android refused it (internal error, code 1)"
        2 -> "no permission, switch the service off and on again (code 2)"
        3 -> "too soon after the last one (code 3)"
        4 -> "screen not available (code 4)"
        5 -> "the game blocks screenshots (code 5)"
        6 -> "window not available (code 6)"
        else -> "error code $code"
    }

    private fun save(bitmap: Bitmap, gameTitle: String): Boolean {
        val safe = Capture.safeName(gameTitle)
        val time = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.UK).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${safe}_$time.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ROOT_FOLDER/$safe")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
                ?: throw IllegalStateException("No output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Saving screenshot failed", e)
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Screenshots while playing", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while Backlog Quest is taking screenshots of your game"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification() {
        if (!inGame) return
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Screenshots: $title")
            .setContentText(
                "Every ${Capture.intervalSeconds(this)}s · $shotCount saved" +
                    (lastProblem?.let { " · last try failed: $it" } ?: ""),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
        // Does nothing if notifications aren't allowed.
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
    }

    companion object {
        private const val TAG = "BQCapture"
        const val ROOT_FOLDER = "BacklogQuest"
        private const val CHANNEL_ID = "capturing"
        private const val NOTIFICATION_ID = 4201
        private const val FIRST_SHOT_DELAY_MS = 2000L
        private const val IDLE_CHECK_MS = 5000L
        private const val AWAY_CHECK_MS = 2000L
        private const val JPEG_QUALITY = 90
    }
}
