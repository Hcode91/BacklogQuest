package com.backlogquest.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads an emulator's releases from GitHub, and works out whether the version
 * you have is the latest.
 */
object GitHubReleases {
    class ReleaseError(message: String) : Exception(message)

    data class Asset(val name: String, val url: String, val size: Long)

    data class Release(
        val tag: String,
        val name: String,
        /** The release's page on GitHub. */
        val page: String,
        /** "2026-11-01" */
        val published: String,
        val prerelease: Boolean,
        val assets: List<Asset>,
        val notes: String,
    ) {
        /** What to call this version: the tag, unless the name holds a clearer version number. */
        val version: String get() = tag.ifBlank { name }
    }

    /** "owner/repo" from a GitHub link ("https://github.com/owner/repo/releases"), "github.com/owner/repo" or "owner/repo". */
    fun repoFrom(text: String): String? {
        val t = text.trim().removeSuffix("/").removeSuffix(".git")
        val m = Regex("""(?:https?://)?(?:www\.)?github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)""").find(t)
            ?: Regex("""^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$""").find(t)
            ?: return null
        val owner = m.groupValues[1]
        val repo = m.groupValues[2].removeSuffix(".git")
        if (owner.isBlank() || repo.isBlank() || owner == "." || repo == ".") return null
        return "$owner/$repo"
    }

    fun parseRelease(o: JSONObject): Release {
        val assets = o.optJSONArray("assets") ?: JSONArray()
        return Release(
            tag = o.optString("tag_name"),
            name = o.optString("name"),
            page = o.optString("html_url"),
            published = o.optString("published_at").take(10),
            prerelease = o.optBoolean("prerelease"),
            assets = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.map {
                Asset(it.optString("name"), it.optString("browser_download_url"), it.optLong("size"))
            }.filter { it.url.startsWith("https://") },
            notes = o.optString("body").take(4000),
        )
    }

    /**
     * The newest release from a list as GitHub gives it (newest first). Drafts are
     * skipped, and pre-releases unless [includePre].
     */
    fun newest(list: JSONArray, includePre: Boolean): Release? =
        (0 until list.length()).mapNotNull { list.optJSONObject(it) }
            .filter { !it.optBoolean("draft") && (includePre || !it.optBoolean("prerelease")) }
            .map(::parseRelease)
            .firstOrNull()

    private const val API = "https://api.github.com/repos/"
    private val HEADERS = mapOf(
        "Accept" to "application/vnd.github+json",
        "User-Agent" to "BacklogQuest (+https://github.com/Hcode91/BacklogQuest)",
    )

    private fun get(path: String): Http.Resp {
        val r = try {
            Http.call(API + path, headers = HEADERS)
        } catch (e: Exception) {
            throw ReleaseError("GitHub couldn't be reached. Check your connection.")
        }
        if (r.code == 403 || r.code == 429) {
            throw ReleaseError("GitHub is limiting how often it's asked (60 times an hour). Try again later.")
        }
        return r
    }

    /** The latest release of [repo]. Some projects only publish pre-releases, so those are used if there's nothing else. */
    suspend fun latest(repo: String, includePre: Boolean): Release = withContext(Dispatchers.IO) {
        val r = get("$repo/releases?per_page=15")
        if (r.code == 404) throw ReleaseError("No GitHub project at github.com/$repo.")
        if (r.code !in 200..299) throw ReleaseError("GitHub answered with an error (${r.code}).")
        val list = try {
            JSONArray(r.body)
        } catch (e: Exception) {
            throw ReleaseError("GitHub sent something unexpected.")
        }
        newest(list, includePre) ?: newest(list, includePre = true)
            ?: throw ReleaseError("github.com/$repo has no releases. It may publish its builds elsewhere.")
    }

    // ---------------- Versions ----------------

    /** The numbers in a version: "v2.3.1-beta" and "Azahar 2.3.1" both give [2, 3, 1]. */
    fun numbers(version: String): List<Int>? {
        val m = Regex("""\d+(?:\.\d+)+""").find(version) ?: Regex("""\d+""").find(version) ?: return null
        return m.value.split('.').map { it.toIntOrNull() ?: return null }
    }

    enum class Status { UP_TO_DATE, UPDATE, UNKNOWN }

    /**
     * Whether [installed] is the latest. Versions are compared by their numbers when both
     * have some ("1.19.1_GIT" is the same as "v1.19.1"); otherwise only an exact match
     * counts as up to date, and anything else is "can't tell".
     */
    fun compare(installed: String, latest: Release): Status {
        val have = installed.trim()
        if (have.isBlank()) return Status.UNKNOWN
        val candidates = listOf(latest.tag, latest.name).filter { it.isNotBlank() }
        fun bare(s: String) = s.trim().lowercase().removePrefix("v")
        if (candidates.any { bare(it) == bare(have) }) return Status.UP_TO_DATE
        // A build named after a commit ("a1b2c3d") matches a release that mentions it.
        val hash = Regex("""\b[0-9a-f]{7,40}\b""").find(have.lowercase())?.value
        if (hash != null && candidates.any { it.lowercase().contains(hash.take(7)) }) return Status.UP_TO_DATE
        val mine = numbers(have) ?: return Status.UNKNOWN
        val theirs = candidates.firstNotNullOfOrNull(::numbers) ?: return Status.UNKNOWN
        for (i in 0 until maxOf(mine.size, theirs.size)) {
            val a = mine.getOrElse(i) { 0 }
            val b = theirs.getOrElse(i) { 0 }
            if (a != b) return if (a < b) Status.UPDATE else Status.UP_TO_DATE
        }
        return Status.UP_TO_DATE
    }

    // ---------------- Downloads ----------------

    fun apks(r: Release): List<Asset> = r.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }

    private val ABI_WORDS = mapOf(
        "arm64-v8a" to listOf("arm64", "aarch64", "v8a", "arm64-v8a"),
        "armeabi-v7a" to listOf("armeabi", "armv7", "v7a", "arm32"),
        "x86_64" to listOf("x86_64", "x64", "amd64"),
        "x86" to listOf("x86"),
    )

    /**
     * The APK to install: the one whose name contains [hint] if you've set one; otherwise
     * the only APK, or the one built for this phone's processor ([abis], best first), or a
     * universal build. Null when it can't tell, so you choose.
     */
    fun pickApk(r: Release, abis: List<String>, hint: String = ""): Asset? {
        val apks = apks(r)
        if (apks.isEmpty()) return null
        if (hint.isNotBlank()) apks.firstOrNull { it.name.contains(hint.trim(), ignoreCase = true) }?.let { return it }
        if (apks.size == 1) return apks.single()
        // Builds for other kinds of phone (debug builds, other app variants) are less likely to be wanted.
        val plain = apks.filter { !Regex("debug|unsigned", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }.ifEmpty { apks }
        for (abi in abis) {
            val words = ABI_WORDS[abi] ?: listOf(abi)
            val forAbi = plain.filter { a -> words.any { a.name.contains(it, ignoreCase = true) } }
                // "x86" also appears in "x86_64": don't let one stand for the other.
                .filter { a -> abi != "x86" || !a.name.contains("x86_64", ignoreCase = true) }
            if (forAbi.size == 1) return forAbi.single()
        }
        val universal = plain.filter { it.name.contains("universal", ignoreCase = true) }
        if (universal.size == 1) return universal.single()
        // A single APK that isn't tied to any processor.
        val neutral = plain.filter { a -> ABI_WORDS.values.flatten().none { a.name.contains(it, ignoreCase = true) } }
        return neutral.singleOrNull()
    }

    /** Release notes as plain text: markdown marks and HTML tags taken out. */
    fun plainNotes(notes: String): String = notes
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
        .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
        .replace(Regex("(?m)^#+\\s*"), "")
        .replace("**", "").replace("__", "").replace("`", "")
        .replace(Regex("\r"), "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
