package com.backlogquest.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

/** One game found on a web page. */
data class WebGame(val title: String, val url: String, val image: String, val genres: List<String> = emptyList())

/**
 * What a web address turned out to hold: the page itself as one game, and,
 * when the page is a list or a collection, the games on it.
 */
class WebFound(
    /** A name for the list, to suggest as its platform. */
    val name: String,
    val page: WebGame?,
    val games: List<WebGame>,
    /** How many games the site says there are, when that's more than could be read. */
    val total: Int = games.size,
)

/**
 * Reads games from a web address so they can be added to the wishlist.
 *
 * - Internet Archive collections, searches and items are read through the
 *   Archive's own API, so every item's name, picture and link is exact.
 * - Any other page is read as one game (its title and main picture), and is
 *   also searched for a repeated run of links with pictures, which is how
 *   list pages usually look. That part is a best guess.
 */
object WebImport {
    class WebError(message: String) : Exception(message)

    /** The most games taken from one Internet Archive collection or search. */
    const val ARCHIVE_LIMIT = 5000

    /** The first web address in a piece of shared text ("Check this out https://…"). */
    fun firstUrl(text: String?): String? =
        Regex("https?://[^\\s<>\"]+").find(text.orEmpty())?.value?.trimEnd('.', ',', ')', ']', '!', '?')

    fun gameId(url: String): String = "web:" + url.substringAfter("://").trimEnd('/').lowercase()

    // ---------------- Internet Archive ----------------

    /** What an archive.org address points at. */
    sealed class ArchiveLink {
        /** /details/<id>: one item, or a whole collection. */
        class Details(val id: String) : ArchiveLink()
        class Search(val query: String) : ArchiveLink()
    }

    fun archiveLink(url: String): ArchiveLink? {
        val uri = try {
            URI(url.trim())
        } catch (e: Exception) {
            return null
        }
        val host = uri.host?.lowercase() ?: return null
        if (host != "archive.org" && !host.endsWith(".archive.org")) return null
        val path = uri.path.orEmpty()
        Regex("^/(?:details|download|embed)/([^/?#]+)").find(path)?.let { return ArchiveLink.Details(it.groupValues[1]) }
        if (path.startsWith("/search")) {
            val query = uri.rawQuery.orEmpty().split('&')
                .firstOrNull { it.startsWith("query=") || it.startsWith("q=") }
                ?.substringAfter('=')?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            if (!query.isNullOrBlank()) return ArchiveLink.Search(query)
        }
        return null
    }

    private fun strings(value: Any?): List<String> = when (value) {
        is JSONArray -> (0 until value.length()).map { value.optString(it) }
        is String -> value.split(";")
        else -> emptyList()
    }.map { it.trim() }.filter { it.isNotBlank() }

    private fun archiveGame(id: String, title: String, subjects: Any?) = WebGame(
        title = title.ifBlank { id },
        url = "https://archive.org/details/$id",
        image = "https://archive.org/services/img/$id",
        genres = strings(subjects).distinct().take(8),
    )

    /** The items in one page of an Archive search answer, and how many the search has in all. */
    fun archiveResults(body: String): Pair<List<WebGame>, Int> {
        val response = JSONObject(body).optJSONObject("response") ?: return emptyList<WebGame>() to 0
        val docs = response.optJSONArray("docs") ?: JSONArray()
        val games = (0 until docs.length()).mapNotNull { docs.optJSONObject(it) }
            .filter { it.optString("identifier").isNotBlank() }
            .map { archiveGame(it.optString("identifier"), it.optString("title"), it.opt("subject")) }
        return games to response.optInt("numFound", games.size)
    }

    private fun archiveSearch(query: String, name: String, progress: (String) -> Unit): WebFound {
        val all = ArrayList<WebGame>()
        var total = 0
        var page = 1
        while (all.size < ARCHIVE_LIMIT) {
            val r = Http.call(
                "https://archive.org/advancedsearch.php?q=${Http.enc(query)}" +
                    "&fl[]=identifier&fl[]=title&fl[]=subject&sort[]=titleSorter+asc&rows=500&page=$page&output=json",
            )
            if (r.code !in 200..299) throw WebError("The Internet Archive couldn't be reached.")
            val (games, found) = archiveResults(r.body)
            total = found
            all += games
            progress("Reading the Internet Archive… ${"%,d".format(all.size)} of ${"%,d".format(minOf(total, ARCHIVE_LIMIT))}")
            if (games.isEmpty() || all.size >= total) break
            page++
        }
        if (all.isEmpty()) throw WebError("The Internet Archive found nothing there.")
        return WebFound(name, null, all.take(ARCHIVE_LIMIT).distinctBy { it.url }, total)
    }

    private fun archive(link: ArchiveLink, progress: (String) -> Unit): WebFound = when (link) {
        is ArchiveLink.Search -> archiveSearch(link.query, link.query, progress)
        is ArchiveLink.Details -> {
            val r = Http.call("https://archive.org/metadata/${Http.enc(link.id)}")
            if (r.code !in 200..299) throw WebError("The Internet Archive couldn't be reached.")
            val meta = JSONObject(r.body).optJSONObject("metadata")
                ?: throw WebError("The Internet Archive has nothing at that address.")
            // A title is usually text, but can be a list when an item has several.
            val title = strings(meta.opt("title")).firstOrNull() ?: link.id
            if (meta.optString("mediatype") == "collection") {
                archiveSearch("collection:${link.id}", title, progress)
            } else {
                val game = archiveGame(link.id, title, meta.opt("subject"))
                WebFound(title, game, emptyList())
            }
        }
    }

    // ---------------- Any other page ----------------

    private fun clean(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    private fun picture(img: Element): String {
        val lazy = listOf("data-src", "data-original", "data-lazy-src", "data-lazy").map { img.absUrl(it) }.firstOrNull { it.isNotBlank() }
        val first = img.attr("srcset").ifBlank { img.attr("data-srcset") }.split(",").firstOrNull()?.trim()?.substringBefore(' ').orEmpty()
        val fromSet = if (first.isBlank()) "" else runCatching { URI(img.baseUri()).resolve(first).toString() }.getOrDefault("")
        return lazy ?: img.absUrl("src").takeIf { it.isNotBlank() && !it.startsWith("data:") } ?: fromSet
    }

    /** Where an element sits in the page, by tag and class: list entries share the same answer. */
    private fun shape(e: Element): String {
        val parts = ArrayList<String>()
        var at: Element? = e
        var depth = 0
        while (at != null && depth < 3) {
            parts += at.tagName() + "." + at.classNames().sorted().joinToString(".")
            at = at.parent()
            depth++
        }
        return parts.joinToString(" < ")
    }

    /** The page itself as one game: its preview title and picture. */
    fun pageGame(html: String, url: String): WebGame? {
        val doc = Jsoup.parse(html, url)
        val title = clean(
            doc.select("meta[property=og:title], meta[name=twitter:title]").map { it.attr("content") }.firstOrNull { it.isNotBlank() }
                ?: doc.title(),
        )
        if (title.isBlank()) return null
        return WebGame(title, url, WishImport.imageFrom(html, url).orEmpty())
    }

    /**
     * The games on a list page: the biggest run of links that each carry a
     * picture and sit in the same kind of spot on the page. Returns nothing
     * when the page doesn't look like a list (fewer than four such links).
     */
    fun listGames(html: String, url: String): List<WebGame> {
        val doc = Jsoup.parse(html, url)
        class Entry(val shape: String, val game: WebGame)
        val entries = ArrayList<Entry>()
        for (img in doc.select("a[href] img, a[href] picture img")) {
            val link = img.parents().firstOrNull { it.tagName() == "a" && it.hasAttr("href") } ?: continue
            val href = link.absUrl("href")
            if (!href.startsWith("http") || href.substringBefore('#') == url.substringBefore('#')) continue
            val image = picture(img)
            if (image.isBlank()) continue
            // The name: the picture's own description, the link's text or tooltip, or a heading beside it.
            val card = link.parent()
            val title = clean(
                listOf(
                    img.attr("alt"), link.attr("title"), link.attr("aria-label"), link.text(),
                    card?.selectFirst("h1, h2, h3, h4, h5, .title, [class*=title], [class*=name]")?.text().orEmpty(),
                    card?.select("a[href]")?.firstOrNull { it.absUrl("href") == href && it.text().isNotBlank() }?.text().orEmpty(),
                ).firstOrNull { clean(it).length >= 2 }.orEmpty(),
            )
            if (title.isBlank()) continue
            entries += Entry(shape(link), WebGame(title.take(160), href, image))
        }
        val best = entries.groupBy { it.shape }.values
            .map { group -> group.map { it.game }.distinctBy { it.url } }
            .maxByOrNull { it.size }.orEmpty()
        return if (best.size >= 4) best else emptyList()
    }

    /** The whole page as the hidden browser sees it, for sites that only answer a real browser. */
    private val HTML_SCRIPT = "(function(){ ${Achievements.READY} return document.documentElement.outerHTML; })()"

    private suspend fun html(ctx: Context, url: String): String {
        val plain = try {
            withContext(Dispatchers.IO) { Http.call(url) }
        } catch (e: Exception) {
            null
        }
        if (plain != null && plain.code in 200..299 && plain.body.contains("<")) return plain.body
        if (plain != null && plain.code == 404) throw WebError("That page doesn't exist (404).")
        return PageReader.read(ctx, url, HTML_SCRIPT, timeoutMs = 30_000)
            ?: throw WebError("The page couldn't be read. Check the address and your connection.")
    }

    /** Reads whatever [address] points at. */
    suspend fun read(ctx: Context, address: String, progress: (String) -> Unit = {}): WebFound {
        val url = firstUrl(address) ?: firstUrl("https://${address.trim()}")?.takeIf { address.contains('.') && !address.contains(' ') }
            ?: throw WebError("That doesn't look like a web address.")
        archiveLink(url)?.let { link ->
            return withContext(Dispatchers.IO) {
                try {
                    archive(link, progress)
                } catch (e: WebError) {
                    throw e
                } catch (e: Exception) {
                    throw WebError("The Internet Archive couldn't be read. Try again in a moment.")
                }
            }
        }
        progress("Reading the page…")
        val body = html(ctx, url)
        return withContext(Dispatchers.Default) {
            val page = pageGame(body, url)
            val games = listGames(body, url)
            if (page == null && games.isEmpty()) throw WebError("No game or list could be found on that page.")
            WebFound(page?.title.orEmpty(), page, games)
        }
    }
}
