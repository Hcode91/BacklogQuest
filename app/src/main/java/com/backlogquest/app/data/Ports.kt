package com.backlogquest.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * One decompilation, recompilation, source port or engine remake, from the
 * Awesome Decomp Games list (github.com/SubmitGame/awesome-decomp-games, CC0).
 */
data class Port(
    val id: String,
    val title: String,
    val platform: String,
    /** "static-recomp", "decompilation", "source-port", "engine-remake", "browser-port", "disassembly", "tool" */
    val type: String,
    val url: String,
    val repo: String,
    val description: String,
    val note: String,
    val stars: Int,
    /** "2026-11-23", or "" */
    val lastCommit: String,
    val archived: Boolean,
    val release: String,
    val releaseDate: String,
    val releaseUrl: String,
    val playUrl: String,
    val score: Double,
    /** The list's sources flag it as made mostly with AI. */
    val ai: Boolean,
    val browser: Boolean,
    val inactive: Boolean,
    /** Mentions Android in its name or description, so it probably runs on a phone. */
    val android: Boolean,
)

object Ports {
    /** The list's own data file, rebuilt every day by its maintainers. */
    const val SOURCE = "https://submitgame.github.io/awesome-decomp-games/data/games.json"
    const val SOURCE_ALT = "https://raw.githubusercontent.com/SubmitGame/awesome-decomp-games/main/data/games.json"
    const val SITE = "https://submitgame.github.io/awesome-decomp-games/"

    val TYPES = linkedMapOf(
        "static-recomp" to "Recomp",
        "decompilation" to "Decomp",
        "source-port" to "Source port",
        "engine-remake" to "Engine remake",
        "browser-port" to "Browser port",
        "disassembly" to "Disassembly",
        "tool" to "Tool",
    )

    fun typeLabel(t: String) = TYPES[t] ?: t.replace('-', ' ').replaceFirstChar { it.uppercase() }

    /** The list spells some platforms two ways ("switch2" and "Switch 2"). */
    fun platformName(p: String): String = when (p.trim().lowercase()) {
        "" , "other" -> "Other"
        "switch2", "switch 2" -> "Switch 2"
        "og xbox", "xbox" -> "Xbox"
        else -> p.trim()
    }

    private val ANDROID = Regex("""android|\bapk\b""", RegexOption.IGNORE_CASE)

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k)

    /** Reads games.json. Returns the date the list was made and its projects. */
    fun parse(text: String): Pair<String, List<Port>> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("games") ?: JSONArray()
        val out = ArrayList<Port>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("id").ifBlank { o.str("repo") }
            if (id.isBlank()) continue
            val title = o.str("title").ifBlank { o.str("repo") }
            val desc = o.str("description")
            val note = o.str("note")
            val repo = o.str("repo")
            out += Port(
                id = id,
                title = title,
                platform = platformName(o.str("platform")),
                type = o.str("type"),
                url = o.str("url"),
                repo = repo,
                description = desc,
                note = note,
                stars = o.optInt("stars"),
                lastCommit = o.str("last_commit").take(10),
                archived = o.optBoolean("archived"),
                release = o.str("release"),
                releaseDate = o.str("release_date").take(10),
                releaseUrl = o.str("release_url"),
                playUrl = o.str("play_url"),
                score = o.optDouble("score", 0.0).let { if (it.isNaN()) 0.0 else it },
                ai = o.optBoolean("ai_flag"),
                browser = o.optBoolean("browser_playable"),
                inactive = o.optBoolean("inactive"),
                android = ANDROID.containsMatchIn("$title $repo $desc $note"),
            )
        }
        return root.str("generated_at").take(10) to out
    }

    /**
     * Brings the remembered "first seen" days up to date with a new list: projects not seen
     * before get [today]. The very first list gives everything day 0, so nothing shows as new.
     */
    fun markFirstSeen(old: Map<String, Long>, ids: Collection<String>, today: Long): Map<String, Long> {
        val firstTime = old.isEmpty()
        val out = HashMap<String, Long>(ids.size)
        for (id in ids) out[id] = old[id] ?: if (firstTime) 0L else today
        return out
    }

    /** Watched projects that have a newer release than the one you last saw. */
    fun updated(ports: List<Port>, seen: Map<String, String>): Set<String> =
        ports.filter { p -> seen.containsKey(p.id) && p.release.isNotBlank() && p.release != seen[p.id] }
            .map { it.id }.toSet()
}

/**
 * The Ports tab's data: the list (kept on the phone and refreshed once a day), the
 * projects you watch, and when each project first appeared.
 */
object PortsStore {
    var ports by mutableStateOf<List<Port>>(emptyList())
        private set
    var listDate by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Watched project id → the release you last saw ("" if it had none). */
    var watched by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var firstSeen by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    private var loaded = false

    private fun dir(ctx: Context) = File(ctx.filesDir, "ports").apply { mkdirs() }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ports", Context.MODE_PRIVATE)

    private fun mapToJson(m: Map<String, *>): String = JSONObject(m).toString()
    private fun jsonToStrings(s: String?): Map<String, String> = runCatching {
        val o = JSONObject(s ?: return emptyMap())
        o.keys().asSequence().associateWith { o.optString(it) }
    }.getOrDefault(emptyMap())
    private fun jsonToLongs(s: String?): Map<String, Long> = runCatching {
        val o = JSONObject(s ?: return emptyMap())
        o.keys().asSequence().associateWith { o.optLong(it) }
    }.getOrDefault(emptyMap())

    /** Opens the saved list, then fetches a new one if it's more than a day old. */
    suspend fun open(ctx: Context) {
        if (!loaded) {
            loaded = true
            val p = prefs(ctx)
            watched = jsonToStrings(p.getString("watched", null))
            firstSeen = withContext(Dispatchers.IO) { jsonToLongs(File(dir(ctx), "first-seen.json").takeIf { it.exists() }?.readText()) }
            val cached = File(dir(ctx), "games.json")
            if (cached.exists()) {
                runCatching { withContext(Dispatchers.Default) { Ports.parse(cached.readText()) } }
                    .onSuccess { (date, list) -> listDate = date; ports = list }
            }
        }
        val last = prefs(ctx).getLong("fetched", 0L)
        if (ports.isEmpty() || System.currentTimeMillis() - last > 20 * 60 * 60 * 1000L) refresh(ctx)
    }

    suspend fun refresh(ctx: Context) {
        if (busy) return
        busy = true
        error = null
        try {
            val text = withContext(Dispatchers.IO) {
                listOf(Ports.SOURCE, Ports.SOURCE_ALT).firstNotNullOfOrNull { url ->
                    runCatching { Http.call(url, headers = mapOf("User-Agent" to "BacklogQuest")) }.getOrNull()
                        ?.takeIf { it.code in 200..299 && it.body.isNotBlank() }?.body
                }
            } ?: throw IllegalStateException("The ports list couldn't be downloaded. Check your connection.")
            val (date, list) = withContext(Dispatchers.Default) { Ports.parse(text) }
            if (list.isEmpty()) throw IllegalStateException("The ports list came back empty. Try again later.")
            val seen = Ports.markFirstSeen(firstSeen, list.map { it.id }, LocalDate.now().toEpochDay())
            withContext(Dispatchers.IO) {
                File(dir(ctx), "games.json").writeText(text)
                File(dir(ctx), "first-seen.json").writeText(mapToJson(seen))
            }
            prefs(ctx).edit().putLong("fetched", System.currentTimeMillis()).apply()
            firstSeen = seen
            listDate = date
            ports = list
        } catch (e: Exception) {
            error = e.message ?: "The ports list couldn't be read."
        } finally {
            busy = false
        }
    }

    private fun saveWatched(ctx: Context, m: Map<String, String>) {
        watched = m
        prefs(ctx).edit().putString("watched", mapToJson(m)).apply()
    }

    /** Starts or stops watching a project. Watching remembers its current release. */
    fun toggleWatch(ctx: Context, p: Port) =
        saveWatched(ctx, if (p.id in watched) watched - p.id else watched + (p.id to p.release))

    /** You've seen a watched project's new release. */
    fun markSeen(ctx: Context, p: Port) {
        if (p.id in watched) saveWatched(ctx, watched + (p.id to p.release))
    }
}
