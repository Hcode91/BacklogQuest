package com.backlogquest.app.data

import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a note of why the app crashed, so the next time it opens it can show
 * the reason and you can copy it. A freeze that Android closes ("app isn't
 * responding") isn't a crash inside the app, so it can't be caught here.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"
    @Volatile private var installed = false

    fun install(dir: File) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(dir, FILE).writeText(describe(error, thread.name))
            } catch (t: Throwable) {
                // Nothing more can be done while crashing.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun describe(error: Throwable, thread: String): String = buildString {
        append("Backlog Quest crash, ")
        append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK).format(Date()))
        append("\nAndroid ").append(Build.VERSION.RELEASE).append(" (").append(Build.VERSION.SDK_INT).append("), ")
        append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        append("\nMemory: ").append(Runtime.getRuntime().maxMemory() / 1048576).append(" MB allowed")
        append("\nThread: ").append(thread).append("\n\n")
        append(error.stackTraceToString().take(8000))
    }

    fun read(dir: File): String? = File(dir, FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(dir: File) {
        File(dir, FILE).delete()
    }
}
