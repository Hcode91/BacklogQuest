package com.backlogquest.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Reads installed emulators' versions, and downloads and opens updates for them. */
object EmuUpdates {
    class UpdateError(message: String) : Exception(message)

    /** The version of an app on this phone, or null if it isn't installed. */
    fun installedVersion(ctx: Context, pkg: String): String? = try {
        ctx.packageManager.getPackageInfo(pkg, 0).versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** This phone's processor types, best first ("arm64-v8a", …). */
    fun abis(): List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty()

    /** Android asks once whether Backlog Quest may install apps. */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** The settings page where you allow Backlog Quest to install apps. */
    fun allowInstalls(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    /** Downloads an update into the app's cache, replacing any older download. [progress] is 0–100. */
    suspend fun download(ctx: Context, asset: GitHubReleases.Asset, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safe = asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "update.apk" }
        val target = File(dir, safe)
        val part = File(dir, "$safe.part")
        val request = Request.Builder().url(asset.url).header("User-Agent", "BacklogQuest").build()
        try {
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw UpdateError("The download failed (${r.code}).")
                val body = r.body ?: throw UpdateError("The download was empty.")
                val total = body.contentLength().takeIf { it > 0 } ?: asset.size
                var done = 0L
                var shown = -1
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                            if (pct != shown) {
                                shown = pct
                                withContext(Dispatchers.Main) { progress(pct) }
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            part.delete()
            throw UpdateError("The download stopped. Check your connection and try again.")
        }
        if (!part.renameTo(target)) throw UpdateError("The download couldn't be saved.")
        target
    }

    /** Hands a downloaded APK to Android's installer, which asks you to confirm. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openPage(ctx: Context, url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
