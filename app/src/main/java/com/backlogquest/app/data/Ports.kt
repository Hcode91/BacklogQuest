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
    /** The newest Reddit post about it, from r/AndroidNativePorts or r/decomps. */
    val redditUrl: String = "",
    val redditSub: String = "",
    /** Not on Awesome Decomp Games: found only through a Reddit post. */
    val fromReddit: Boolean = false,
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
 * The Ports tab's data: the list (kept on the phone and refreshed once a day), Reddit posts
 * that link to GitHub projects, the projects you watch, and when each project first appeared.
 */
object PortsStore {
    /** Everything shown: the Awesome Decomp Games list plus projects from Reddit. */
    var ports by mutableStateOf<List<Port>>(emptyList())
        private set
    var listDate by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** A problem reading Reddit or GitHub that doesn't stop the main list showing. */
    var redditNote by mutableStateOf<String?>(null)
        private set
    /** Watched project id → the release you last saw ("" if it had none). */
    var watched by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var firstSeen by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    private var main: List<Port> = emptyList()
    private var posts: List<RedditPost> = emptyList()
    private var info: Map<String, RepoInfo> = emptyMap()
    private var loaded = false

    private class Saved(
        val firstSeen: Map<String, Long>,
        val posts: List<RedditPost>,
        val info: Map<String, RepoInfo>,
        val list: Pair<String, List<Port>>?,
    )

    private fun dir(ctx: Context) = File(ctx.filesDir, "ports").apply { mkdirs() }
    private fun file(ctx: Context, name: String) = File(dir(ctx), name)
    private fun read(ctx: Context, name: String) = file(ctx, name).takeIf { it.exists() }?.readText()
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

    private fun rebuild() {
        ports = RedditPorts.merge(main, posts, info)
    }

    /** Opens the saved list, then fetches a new one if it's more than a day old. */
    suspend fun open(ctx: Context) {
        if (!loaded) {
            loaded = true
            watched = jsonToStrings(prefs(ctx).getString("watched", null))
            // Read on a background thread, then shown in one go.
            val saved = withContext(Dispatchers.IO) {
                Saved(
                    jsonToLongs(read(ctx, "first-seen.json")),
                    read(ctx, "reddit.json")?.let(RedditPorts::postsFromJson).orEmpty(),
                    read(ctx, "github.json")?.let(RedditPorts::infoFromJson).orEmpty(),
                    read(ctx, "games.json")?.let { runCatching { Ports.parse(it) }.getOrNull() },
                )
            }
            firstSeen = saved.firstSeen
            posts = saved.posts
            info = saved.info
            saved.list?.let { (date, list) -> listDate = date; main = list }
            rebuild()
        }
        val last = prefs(ctx).getLong("fetched", 0L)
        if (ports.isEmpty() || System.currentTimeMillis() - last > 20 * 60 * 60 * 1000L) refresh(ctx)
    }

    suspend fun refresh(ctx: Context) {
        if (busy) return
        busy = true
        error = null
        redditNote = null
        try {
            // 1. The Awesome Decomp Games list.
            val text = withContext(Dispatchers.IO) {
                listOf(Ports.SOURCE, Ports.SOURCE_ALT).firstNotNullOfOrNull { url ->
                    runCatching { Http.call(url, headers = mapOf("User-Agent" to "BacklogQuest")) }.getOrNull()
                        ?.takeIf { it.code in 200..299 && it.body.isNotBlank() }?.body
                }
            }
            if (text != null) {
                runCatching { withContext(Dispatchers.Default) { Ports.parse(text) } }
                    .onSuccess { (date, list) ->
                        if (list.isNotEmpty()) {
                            withContext(Dispatchers.IO) { file(ctx, "games.json").writeText(text) }
                            listDate = date
                            main = list
                        }
                    }
            } else if (main.isEmpty()) {
                error = "The ports list couldn't be downloaded. Check your connection."
            }

            // 2. Reddit posts that link to GitHub.
            val fresh = fetchReddit(ctx, firstTime = posts.isEmpty())
            if (fresh == null) {
                redditNote = "Reddit couldn't be read this time" +
                    (if (redditProblem.isNotBlank()) " ($redditProblem)" else "") +
                    (if (posts.isEmpty()) "." else "; its posts are from the last refresh.")
            }
            else {
                posts = RedditPorts.mergePosts(posts, fresh)
                withContext(Dispatchers.IO) { file(ctx, "reddit.json").writeText(RedditPorts.postsToJson(posts)) }
            }

            // 3. Look up projects that are only on Reddit, a few at a time (GitHub allows 60 lookups an hour).
            val todo = RedditPorts.toCheck(posts, main, info, System.currentTimeMillis(), max = 15)
            if (todo.isNotEmpty()) {
                val (found, limited) = withContext(Dispatchers.IO) { lookUp(todo) }
                info = info + found
                withContext(Dispatchers.IO) { file(ctx, "github.json").writeText(RedditPorts.infoToJson(info)) }
                if (limited) redditNote = "GitHub is limiting lookups; some Reddit projects will fill in later."
            }

            rebuild()
            if (ports.isNotEmpty()) {
                val seen = Ports.markFirstSeen(firstSeen, ports.map { it.id }, LocalDate.now().toEpochDay())
                withContext(Dispatchers.IO) { file(ctx, "first-seen.json").writeText(mapToJson(seen)) }
                firstSeen = seen
                if (text != null) prefs(ctx).edit().putLong("fetched", System.currentTimeMillis()).apply()
            }
        } catch (e: Exception) {
            error = e.message ?: "The ports list couldn't be read."
        } finally {
            busy = false
        }
    }

    private const val UA = "android:com.backlogquest.app:v1 (BacklogQuest; +https://github.com/Hcode91/BacklogQuest)"

    /** What went wrong the last time Reddit was read, for the message under the list. */
    private var redditProblem = ""

    /** Shows the listing's text when the page is Reddit's JSON, or "" while it isn't (yet). */
    private const val JSON_SCRIPT =
        "(function(){var b=document.body;var t=b?(b.innerText||b.textContent||''):'';t=t.trim();" +
            "return t.charAt(0)=='{'&&t.indexOf('\"children\"')>0?t:'';})()"

    /**
     * The newest posts from each subreddit (and, the first time, the best of all time).
     * Reddit refuses many apps that aren't signed in, so each page is tried three ways:
     * its JSON, its feed, and then its JSON in the invisible browser (which Reddit treats
     * like Chrome on the phone). Null if nothing could be read at all.
     */
    private suspend fun fetchReddit(ctx: Context, firstTime: Boolean): List<RedditPost>? {
        val out = ArrayList<RedditPost>()
        var anyOk = false
        redditProblem = ""
        for (sub in RedditPorts.SUBS) {
            val pages = listOf("new") + if (firstTime) listOf("top") else emptyList()
            for (page in pages) {
                val extra = if (page == "top") "&t=all" else ""
                val jsonUrl = "https://www.reddit.com/r/$sub/$page.json?limit=100&raw_json=1$extra"
                val parsed = withContext(Dispatchers.IO) {
                    val json = runCatching { Http.call(jsonUrl, headers = mapOf("User-Agent" to UA)) }.getOrNull()
                    if (json != null && json.code !in 200..299) redditProblem = "Reddit answered ${json.code}"
                    json?.takeIf { it.code in 200..299 }?.let { r -> runCatching { RedditPorts.parseJson(r.body, sub) }.getOrNull() }
                        ?: runCatching {
                            val r = Http.call("https://www.reddit.com/r/$sub/$page/.rss?limit=100$extra", headers = mapOf("User-Agent" to UA))
                            if (r.code in 200..299) RedditPorts.parseRss(r.body, sub) else null
                        }.getOrNull()
                } ?: runCatching {
                    PageReader.read(ctx, jsonUrl, JSON_SCRIPT, timeoutMs = 15_000)
                        ?.let { RedditPorts.parseJson(it, sub) }
                }.getOrNull()
                if (parsed != null) {
                    anyOk = true
                    out += parsed
                }
            }
        }
        return if (anyOk) out else null
    }

    /** Asks GitHub about each project: does it exist, its stars, last update and latest release. */
    private fun lookUp(repos: List<String>): Pair<Map<String, RepoInfo>, Boolean> {
        val headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to UA)
        val out = HashMap<String, RepoInfo>()
        val now = System.currentTimeMillis()
        for (repo in repos) {
            val r = runCatching { Http.call("https://api.github.com/repos/$repo", headers = headers) }.getOrNull() ?: continue
            if (r.code == 403 || r.code == 429) return out to true
            if (r.code == 404 || r.code == 451) {
                out[repo.lowercase()] = RepoInfo(exists = false, checked = now)
                continue
            }
            if (r.code !in 200..299) continue
            val o = runCatching { JSONObject(r.body) }.getOrNull() ?: continue
            var info = RepoInfo(
                exists = true,
                stars = o.optInt("stargazers_count"),
                pushed = (if (o.isNull("pushed_at")) "" else o.optString("pushed_at")).take(10),
                description = if (o.isNull("description")) "" else o.optString("description"),
                archived = o.optBoolean("archived"),
                checked = now,
            )
            val rel = runCatching { Http.call("https://api.github.com/repos/$repo/releases/latest", headers = headers) }.getOrNull()
            if (rel != null && (rel.code == 403 || rel.code == 429)) {
                out[repo.lowercase()] = info
                return out to true
            }
            if (rel != null && rel.code in 200..299) {
                runCatching { GitHubReleases.parseRelease(JSONObject(rel.body)) }.getOrNull()?.let {
                    info = info.copy(release = it.version, releaseDate = it.published, releaseUrl = it.page)
                }
            }
            out[repo.lowercase()] = info
        }
        return out to false
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
