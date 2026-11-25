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
import android.view.inputmethod.InputMethodManager
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

    private var capturing = false
    private var title = ""
    private var shotCount = 0
    private var shotsPkg: String? = null
    private var leftGameAt = 0L
    private var ignoredPackages: Set<String> = emptySet()

    private val tick = object : Runnable {
        override fun run() {
            capture()
            handler.postDelayed(this, Capture.intervalSeconds(this@CaptureService) * 1000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        io = Executors.newSingleThreadExecutor()
        refreshIgnored()
        createChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Keyboards, the notification shade and system pop-ups appear over games:
        // they don't count as leaving the game.
        if (pkg in ignoredPackages) return

        val session = Capture.session(this)
        val wanted = Capture.supported && Capture.enabled(this) && session != null && pkg == session.pkg
        if (wanted) {
            val now = System.currentTimeMillis()
            if (!capturing && leftGameAt > 0 && now - leftGameAt > Capture.STALE_AFTER_MS) {
                // Away for ages without going back to Backlog Quest: treat the session as over,
                // so opening the emulator on its own later isn't named after the old game.
                Capture.end(this)
                leftGameAt = 0
                return
            }
            leftGameAt = 0
            if (!capturing) start(session!!)
        } else {
            if (capturing) {
                stop()
                leftGameAt = System.currentTimeMillis()
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        stop()
        if (::io.isInitialized) io.shutdown()
        super.onDestroy()
    }

    private fun refreshIgnored() {
        val imes = try {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        ignoredPackages = (imes + listOf("com.android.systemui", "android")).toSet()
    }

    private fun start(session: Capture.Session) {
        handler.removeCallbacks(tick)
        refreshIgnored()
        capturing = true
        title = session.title
        if (shotsPkg != session.pkg + session.title) {
            shotsPkg = session.pkg + session.title
            shotCount = 0
        }
        Log.i(TAG, "Capturing ${session.title} (${session.pkg})")
        updateNotification()
        // Give the game a moment to draw before the first shot.
        handler.postDelayed(tick, FIRST_SHOT_DELAY_MS)
    }

    private fun stop() {
        handler.removeCallbacks(tick)
        capturing = false
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun capture() {
        if (!capturing || !Capture.supported) return
        if (!Capture.enabled(this) || Capture.session(this) == null) { stop(); return }

        // Skip while the screen is off or locked.
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return

        val name = title
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hw = result.hardwareBuffer
                val bitmap = try {
                    Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                        ?.let { hwBmp -> hwBmp.copy(Bitmap.Config.ARGB_8888, false).also { hwBmp.recycle() } }
                } finally {
                    hw.close()
                }
                if (bitmap == null) return
                io.execute {
                    if (save(bitmap, name)) handler.post { shotCount++; updateNotification() }
                    bitmap.recycle()
                }
            }

            override fun onFailure(errorCode: Int) {
                // e.g. the game blocks screenshots
                Log.w(TAG, "Screenshot failed, code $errorCode")
            }
        })
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
        if (!capturing) return
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Screenshots: $title")
            .setContentText("Every ${Capture.intervalSeconds(this)}s · $shotCount saved")
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
        private const val JPEG_QUALITY = 90
    }
}
