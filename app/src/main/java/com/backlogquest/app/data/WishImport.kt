package com.backlogquest.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File

/** One game from an imported CSV list. */
data class CsvGame(val id: String, val title: String, val url: String, val genres: List<String>)

/** One imported CSV file: every game in it belongs to [platform]. */
data class CsvList(val platform: String, val file: String, val games: List<CsvGame>)

/**
 * Wishlist games imported from CSV files, such as MobyGames platform lists.
 *
 * A file needs a header row. The columns used are the title (Title or Name),
 * the link (URL or Link), the genres (Genres, separated by commas) and, when
 * present, ID and Platform. Without a Platform column the platform comes from
 * the file's name: "All_Game_Boy_Games_April_2025.csv" is "Game Boy".
 */
object WishImport {
    class CsvError(message: String) : Exception(message)

    /** Stored instead of an image address when a game's page has no picture, so it isn't asked again. */
    const val NO_IMAGE = "-"

    // ---------------- Reading a CSV ----------------

    /** Splits CSV text into rows of cells, honouring quotes, doubled quotes and line breaks inside quotes. */
    fun rows(text: String): List<List<String>> {
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val s = text.removePrefix("﻿")
        fun endRow() {
            row.add(cell.toString())
            cell.setLength(0)
            if (row.any { it.isNotBlank() }) out.add(row)
            row = ArrayList()
        }
        while (i < s.length) {
            val c = s[i]
            when {
                quoted && c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row.add(cell.toString()); cell.setLength(0) }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                    endRow()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return out
    }

    /** "All_Game_Boy_Advance_Games_April_2025.csv" → "Game Boy Advance". */
    fun platformFromFileName(name: String): String {
        var base = name.substringAfterLast('/').substringBeforeLast('.')
        base = base.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
        base = base.replace(Regex("^all ", RegexOption.IGNORE_CASE), "")
        base = base.replace(Regex(" games( .*)?$", RegexOption.IGNORE_CASE), "")
        return base.trim()
    }

    /** The games in one CSV file. A Platform column, when there is one, wins over [platform]. */
    fun parse(text: String, platform: String, file: String): List<CsvList> {
        val rows = rows(text)
        if (rows.isEmpty()) throw CsvError("The file is empty.")
        val header = rows.first().map { it.trim().lowercase() }
        fun column(vararg names: String) = header.indexOfFirst { it in names }
        val title = column("title", "name", "game", "game title")
        if (title < 0) throw CsvError("No Title column found. The first row should name the columns.")
        val id = column("id", "game id", "moby id")
        val url = column("url", "link", "moby url")
        val genres = column("genres", "genre")
        val plat = column("platform", "platforms", "system", "console")

        val byPlatform = LinkedHashMap<String, ArrayList<CsvGame>>()
        for (r in rows.drop(1)) {
            val name = r.getOrNull(title)?.trim().orEmpty()
            if (name.isBlank()) continue
            val on = r.getOrNull(plat)?.trim().orEmpty().ifBlank { platform.trim() }
            val link = r.getOrNull(url)?.trim().orEmpty().takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()
            val number = r.getOrNull(id)?.trim().orEmpty()
            val key = when {
                number.isNotBlank() -> "csv:$number"
                else -> "csv:" + normTitle(name) + ":" + normTitle(on)
            }
            val list = r.getOrNull(genres).orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
            byPlatform.getOrPut(on) { ArrayList() }.add(CsvGame(key, name, link, list))
        }
        if (byPlatform.isEmpty()) throw CsvError("No games found in the file.")
        return byPlatform.map { (p, games) -> CsvList(p, file, games.distinctBy { it.id }) }
    }

    /**
     * Wishlist entries for everything imported. A game that's in several lists
     * (the same ID on two platforms) becomes one entry with both platforms.
     */
    fun wishItems(lists: List<CsvList>): List<WishItem> {
        val out = LinkedHashMap<String, WishItem>()
        for (list in lists) for (g in list.games) {
            val seen = out[g.id]
            out[g.id] = if (seen == null) {
                WishItem(
                    g.id, g.title, listOfNotNull(list.platform.takeIf { it.isNotBlank() }), 0.0, 0.0,
                    tags = emptyList(), unmarked = false, onDrive = "",
                    url = g.url, genres = g.genres, imported = true,
                )
            } else {
                seen.copy(
                    platforms = (seen.platforms + list.platform).filter { it.isNotBlank() }.distinct(),
                    genres = (seen.genres + g.genres).distinct(),
                    url = seen.url.ifBlank { g.url },
                )
            }
        }
        return out.values.toList()
    }

    /** Adds [added] to what's already imported; a list for the same platform is replaced. */
    fun merge(existing: List<CsvList>, added: List<CsvList>): List<CsvList> {
        val replaced = added.map { it.platform.lowercase() }.toSet()
        return existing.filter { it.platform.lowercase() !in replaced } + added
    }

    // ---------------- Saving on the phone ----------------

    private const val FILE = "wishlist-imports.json"

    fun save(dir: File, lists: List<CsvList>, images: Map<String, String>) {
        val root = JSONObject()
        root.put("lists", JSONArray().apply {
            lists.forEach { l ->
                put(JSONObject().put("platform", l.platform).put("file", l.file).put("games", JSONArray().apply {
                    l.games.forEach { g ->
                        put(JSONArray().put(g.id).put(g.title).put(g.url).put(g.genres.joinToString("|")))
                    }
                }))
            }
        })
        root.put("images", JSONObject().apply { images.forEach { (k, v) -> put(k, v) } })
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(File(dir, FILE))
    }

    fun load(dir: File): Pair<List<CsvList>, Map<String, String>> {
        val f = File(dir, FILE)
        if (!f.exists()) return emptyList<CsvList>() to emptyMap()
        return try {
            val root = JSONObject(f.readText())
            val lists = root.optJSONArray("lists")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { l ->
                    val games = l.optJSONArray("games") ?: JSONArray()
                    CsvList(
                        l.optString("platform"), l.optString("file"),
                        (0 until games.length()).mapNotNull { games.optJSONArray(it) }.map { g ->
                            CsvGame(g.optString(0), g.optString(1), g.optString(2), g.optString(3).split("|").filter { it.isNotBlank() })
                        },
                    )
                }
            } ?: emptyList()
            val images = root.optJSONObject("images")?.let { o -> o.keys().asSequence().associateWith { o.optString(it) } } ?: emptyMap()
            lists to images
        } catch (e: Exception) {
            emptyList<CsvList>() to emptyMap()
        }
    }

    // ---------------- Pictures from a game's link ----------------

    private fun usable(address: String?): Boolean =
        !address.isNullOrBlank() && address.startsWith("http") &&
            !Regex("logo|favicon|placeholder|default[-_.]|/static/", RegexOption.IGNORE_CASE).containsMatchIn(address)

    /**
     * The main picture of a game's web page: the image the page offers for
     * link previews, or failing that its first cover, promo or screenshot.
     */
    fun imageFrom(html: String, pageUrl: String): String? {
        val doc = Jsoup.parse(html, pageUrl)
        val preview = doc.select("meta[property=og:image], meta[name=og:image], meta[name=twitter:image]")
            .map { it.absUrl("content").ifBlank { it.attr("content") } }.firstOrNull(::usable)
        if (preview != null) return preview
        return doc.select("img[src*=/covers/], img[src*=/promos/], img[src*=/screenshots/]")
            .map { it.absUrl("src") }.firstOrNull(::usable)
    }

    /** The same search as [imageFrom], run inside the hidden browser. "none" means the page has no picture. */
    private val IMAGE_SCRIPT = """
        (function(){ ${Achievements.READY}
          function ok(u){ return u && /^https?:/.test(u) && !/logo|favicon|placeholder|default[-_.]|\/static\//i.test(u); }
          var metas = document.querySelectorAll('meta[property="og:image"], meta[name="og:image"], meta[name="twitter:image"]');
          for (var i = 0; i < metas.length; i++) { if (ok(metas[i].content)) return metas[i].content; }
          var img = document.querySelector('img[src*="/covers/"], img[src*="/promos/"], img[src*="/screenshots/"]');
          if (img && ok(img.src)) return img.src;
          return 'none';
        })()
    """.trimIndent()

    sealed class Image {
        class Found(val address: String) : Image()
        object None : Image()
        /** The site didn't answer; worth trying again later. */
        object Failed : Image()
    }

    /**
     * Finds the picture for one game. A plain request is tried first because it's
     * quick; sites that only answer a real browser are read with the hidden one.
     * [plainWorks] is switched off by the caller once a site has refused a plain request.
     */
    suspend fun image(ctx: Context, url: String, plainWorks: Boolean, onRefused: () -> Unit): Image {
        if (plainWorks) {
            val r = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Http.call(url) }
            } catch (e: Exception) {
                null
            }
            if (r != null && r.code in 200..299) {
                return imageFrom(r.body, url)?.let { Image.Found(it) } ?: Image.None
            }
            if (r != null && r.code == 404) return Image.None
            onRefused()
        }
        return when (val read = PageReader.read(ctx, url, IMAGE_SCRIPT, timeoutMs = 25_000)) {
            null -> Image.Failed
            "none" -> Image.None
            else -> Image.Found(read)
        }
    }
}
