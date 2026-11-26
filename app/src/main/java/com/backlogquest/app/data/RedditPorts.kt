package com.backlogquest.app.data

import org.json.JSONArray
import org.json.JSONObject

/** A Reddit post that links to at least one GitHub project. */
data class RedditPost(
    val id: String,
    val sub: String,
    val title: String,
    val link: String,
    /** Seconds since 1970. */
    val created: Long,
    val flair: String,
    /** "owner/repo", as linked from the post. Never empty. */
    val repos: List<String>,
)

/** What GitHub says about a project linked from Reddit. */
data class RepoInfo(
    /** False when GitHub says there's no such project. */
    val exists: Boolean,
    val stars: Int = 0,
    val pushed: String = "",
    val description: String = "",
    val archived: Boolean = false,
    val release: String = "",
    val releaseDate: String = "",
    val releaseUrl: String = "",
    /** When this was looked up, in milliseconds. */
    val checked: Long = 0,
)

/**
 * Posts from r/AndroidNativePorts and r/decomps, kept only when they link to a GitHub
 * project, and turned into entries for the Ports tab.
 */
object RedditPorts {
    val SUBS = listOf("AndroidNativePorts", "decomps")

    /** GitHub addresses that aren't projects ("github.com/orgs/…", "github.com/topics/…"). */
    private val NOT_OWNERS = setOf(
        "orgs", "topics", "sponsors", "settings", "features", "marketplace", "about", "pricing",
        "login", "join", "explore", "collections", "trending", "issues", "pulls", "notifications",
        "search", "apps", "site", "security", "enterprise", "readme", "users", "codespaces", "events",
    )
    private val GH = Regex("""github\.com/([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))/([A-Za-z0-9_.-]{1,100})""", RegexOption.IGNORE_CASE)

    /** Every "owner/repo" a piece of text links to, in order, without repeats. */
    fun githubRepos(text: String): List<String> {
        val out = LinkedHashMap<String, String>()
        for (m in GH.findAll(text)) {
            val owner = m.groupValues[1]
            val repo = m.groupValues[2].removeSuffix(".git").trimEnd('.', '-', '_')
            if (owner.lowercase() in NOT_OWNERS || repo.isBlank() || repo == "." || repo == "..") continue
            val full = "$owner/$repo"
            out.putIfAbsent(full.lowercase(), full)
        }
        return out.values.toList()
    }

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k)

    /** A subreddit listing in Reddit's JSON form. Posts without a GitHub link are left out. */
    fun parseJson(text: String, sub: String): List<RedditPost> {
        val children = JSONObject(text).optJSONObject("data")?.optJSONArray("children") ?: JSONArray()
        return (0 until children.length()).mapNotNull { i ->
            val d = children.optJSONObject(i)?.optJSONObject("data") ?: return@mapNotNull null
            // Posts taken down by the moderators or their author.
            if (d.str("removed_by_category").isNotBlank()) return@mapNotNull null
            val repos = githubRepos(listOf(d.str("url"), d.str("url_overridden_by_dest"), d.str("selftext")).joinToString(" "))
            if (repos.isEmpty()) return@mapNotNull null
            RedditPost(
                id = d.str("id").ifBlank { return@mapNotNull null },
                sub = d.str("subreddit").ifBlank { sub },
                title = d.str("title").trim(),
                link = "https://www.reddit.com" + d.str("permalink"),
                created = d.optDouble("created_utc", 0.0).let { if (it.isNaN()) 0L else it.toLong() },
                flair = d.str("link_flair_text").trim(),
                repos = repos,
            )
        }
    }

    /** The newest post's time in a listing (seconds since 1970), or 0. */
    fun newestCreated(text: String): Long {
        val kids = JSONObject(text).optJSONObject("data")?.optJSONArray("children") ?: return 0
        return (0 until kids.length()).maxOfOrNull { i ->
            kids.optJSONObject(i)?.optJSONObject("data")?.optDouble("created_utc", 0.0)?.takeIf { !it.isNaN() }?.toLong() ?: 0L
        } ?: 0L
    }

    /** What [author] wrote in a post's comments (Reddit's [post, comments] JSON), as one piece of text. */
    fun authorComments(text: String, author: String): String = runCatching {
        val kids = JSONArray(text).optJSONObject(1)?.optJSONObject("data")?.optJSONArray("children") ?: return ""
        (0 until kids.length()).mapNotNull { kids.optJSONObject(it)?.optJSONObject("data") }
            .filter { author.isNotBlank() && it.optString("author") == author }
            .joinToString(" ") { if (it.isNull("body")) "" else it.optString("body") }
    }.getOrDefault("")

    private fun unescape(s: String) = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&#x27;", "'").replace("&amp;", "&")

    /** A subreddit's Atom feed, used when the JSON listing is refused. */
    fun parseRss(text: String, sub: String): List<RedditPost> =
        Regex("""<entry>(.*?)</entry>""", RegexOption.DOT_MATCHES_ALL).findAll(text).mapNotNull { m ->
            val e = m.groupValues[1]
            val content = unescape(unescape(Regex("""<content[^>]*>(.*?)</content>""", RegexOption.DOT_MATCHES_ALL).find(e)?.groupValues?.get(1).orEmpty()))
            // The feed adds a link to the post's author and comments; those aren't GitHub, so only GitHub links count.
            val repos = githubRepos(content)
            if (repos.isEmpty()) return@mapNotNull null
            val link = Regex("""<link href="([^"]+)"""").find(e)?.groupValues?.get(1).orEmpty()
            val id = Regex("""<id>t3_([^<]+)</id>""").find(e)?.groupValues?.get(1)
                ?: Regex("""/comments/([a-z0-9]+)""").find(link)?.groupValues?.get(1)
                ?: return@mapNotNull null
            val date = Regex("""<(?:published|updated)>([^<]+)<""").find(e)?.groupValues?.get(1).orEmpty()
            RedditPost(
                id = id,
                sub = sub,
                title = unescape(Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL).find(e)?.groupValues?.get(1).orEmpty()).trim(),
                link = link,
                created = runCatching { java.time.OffsetDateTime.parse(date).toEpochSecond() }.getOrDefault(0L),
                flair = Regex("""<category term="([^"]*)" label="([^"]*)"""").find(e)?.groupValues?.get(2).orEmpty()
                    .takeIf { !it.startsWith("r/") }.orEmpty(),
                repos = repos,
            )
        }.toList()

    // ---------------- Saving ----------------

    fun postsToJson(posts: List<RedditPost>): String = JSONArray().apply {
        posts.forEach { p ->
            put(
                JSONObject().put("id", p.id).put("sub", p.sub).put("title", p.title).put("link", p.link)
                    .put("created", p.created).put("flair", p.flair).put("repos", JSONArray(p.repos)),
            )
        }
    }.toString()

    fun postsFromJson(text: String): List<RedditPost> = runCatching {
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val r = o.optJSONArray("repos") ?: JSONArray()
            RedditPost(
                o.optString("id"), o.optString("sub"), o.optString("title"), o.optString("link"),
                o.optLong("created"), o.optString("flair"), (0 until r.length()).map { r.optString(it) },
            ).takeIf { it.id.isNotBlank() && it.repos.isNotEmpty() }
        }
    }.getOrDefault(emptyList())

    fun infoToJson(m: Map<String, RepoInfo>): String = JSONObject().apply {
        m.forEach { (k, v) ->
            put(
                k,
                JSONObject().put("exists", v.exists).put("stars", v.stars).put("pushed", v.pushed)
                    .put("description", v.description).put("archived", v.archived).put("release", v.release)
                    .put("releaseDate", v.releaseDate).put("releaseUrl", v.releaseUrl).put("checked", v.checked),
            )
        }
    }.toString()

    fun infoFromJson(text: String): Map<String, RepoInfo> = runCatching {
        val o = JSONObject(text)
        o.keys().asSequence().mapNotNull { k ->
            val v = o.optJSONObject(k) ?: return@mapNotNull null
            k to RepoInfo(
                v.optBoolean("exists", true), v.optInt("stars"), v.optString("pushed"), v.optString("description"),
                v.optBoolean("archived"), v.optString("release"), v.optString("releaseDate"), v.optString("releaseUrl"),
                v.optLong("checked"),
            )
        }.toMap()
    }.getOrDefault(emptyMap())

    /** Keeps the newest copy of each post, newest first, up to [max]. */
    fun mergePosts(old: List<RedditPost>, new: List<RedditPost>, max: Int = 3000): List<RedditPost> =
        (new + old).distinctBy { it.id }.sortedByDescending { it.created }.take(max)

    // ---------------- Into the Ports list ----------------

    private val PLATFORMS = listOf(
        "xbox 360" to "Xbox 360", "x360" to "Xbox 360", "xbox one" to "Xbox One", "og xbox" to "Xbox", "xbox" to "Xbox",
        "ps5" to "PS5", "ps4" to "PS4", "ps3" to "PS3", "ps2" to "PS2", "playstation 2" to "PS2", "ps1" to "PS1",
        "psx" to "PS1", "playstation" to "PS1", "psp" to "PSP", "vita" to "PS Vita",
        "n64" to "N64", "nintendo 64" to "N64", "gamecube" to "GameCube", "wii u" to "Wii U", "wii" to "Wii",
        "switch 2" to "Switch 2", "switch" to "Switch", "3ds" to "3DS", "nds" to "DS", "gba" to "GBA",
        "game boy advance" to "GBA", "gbc" to "Game Boy Color", "game boy" to "Game Boy", "snes" to "SNES",
        "super nintendo" to "SNES", "nes" to "NES", "dreamcast" to "Dreamcast", "saturn" to "Saturn",
        "genesis" to "Genesis", "mega drive" to "Genesis", "dos" to "DOS", "windows" to "PC", "pc" to "PC",
    )

    /** The platform a post is about, from its flair or title ("[PS2] …"), or "Other". */
    fun guessPlatform(title: String, flair: String): String {
        val text = " ${flair.lowercase()} ${title.lowercase()} "
        for ((word, name) in PLATFORMS) {
            if (Regex("""(?<![a-z0-9])${Regex.escape(word)}(?![a-z0-9])""").containsMatchIn(text)) return name
        }
        return "Other"
    }

    fun guessType(title: String, flair: String, sub: String): String {
        val t = "$flair $title".lowercase()
        return when {
            "recomp" in t -> "static-recomp"
            "decomp" in t -> "decompilation"
            "disassembl" in t -> "disassembly"
            "remake" in t || "reimplement" in t -> "engine-remake"
            sub.equals("decomps", true) -> "decompilation"
            else -> "source-port"
        }
    }

    /**
     * Adds the Reddit posts to the list: a post about a project already on it puts the post's
     * link on that entry; any other project gets an entry of its own. Projects GitHub says don't
     * exist are left out.
     */
    fun merge(list: List<Port>, posts: List<RedditPost>, info: Map<String, RepoInfo>): List<Port> {
        // Newest post for each project.
        val byRepo = LinkedHashMap<String, RedditPost>()
        posts.sortedByDescending { it.created }.forEach { p -> p.repos.forEach { r -> byRepo.putIfAbsent(r.lowercase(), p) } }
        val known = list.associateBy { it.repo.lowercase() }
        val out = list.map { port ->
            val post = byRepo[port.repo.lowercase()] ?: return@map port
            port.copy(redditUrl = post.link, redditSub = post.sub, android = port.android || post.sub.equals("AndroidNativePorts", true))
        }.toMutableList()
        for ((key, post) in byRepo) {
            if (key in known) continue
            val repo = post.repos.first { it.lowercase() == key }
            val gh = info[key]
            if (gh != null && !gh.exists) continue
            // A post that links several projects: name each after the post, plus the project.
            val title = if (post.repos.size > 1) "${post.title} (${repo.substringAfter('/')})" else post.title
            out += Port(
                id = "reddit:$key",
                title = title.ifBlank { repo },
                platform = guessPlatform(post.title, post.flair),
                type = guessType(post.title, post.flair, post.sub),
                url = "https://github.com/$repo",
                repo = repo,
                description = gh?.description.orEmpty(),
                note = "",
                stars = gh?.stars ?: 0,
                lastCommit = gh?.pushed.orEmpty(),
                archived = gh?.archived ?: false,
                release = gh?.release.orEmpty(),
                releaseDate = gh?.releaseDate.orEmpty(),
                releaseUrl = gh?.releaseUrl.orEmpty(),
                playUrl = "",
                score = 0.0,
                ai = false,
                browser = false,
                inactive = false,
                android = post.sub.equals("AndroidNativePorts", true) ||
                    Regex("""android|\bapk\b""", RegexOption.IGNORE_CASE).containsMatchIn("${post.title} $repo"),
                redditUrl = post.link,
                redditSub = post.sub,
                fromReddit = true,
            )
        }
        return out
    }

    /** Reddit-only projects still to be looked up on GitHub (or last looked up over a week ago), newest posts first. */
    fun toCheck(posts: List<RedditPost>, list: List<Port>, info: Map<String, RepoInfo>, now: Long, max: Int): List<String> {
        val onList = list.map { it.repo.lowercase() }.toSet()
        return posts.sortedByDescending { it.created }.flatMap { it.repos }
            .distinctBy { it.lowercase() }
            .filter { it.lowercase() !in onList }
            .filter { r -> info[r.lowercase()]?.let { now - it.checked > 7 * 24 * 3600_000L } ?: true }
            .take(max)
    }
}
