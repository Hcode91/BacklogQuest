package com.backlogquest.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    /** How the list's pages are numbered, when it's split over several. */
    val paging: Paging? = null,
)

/**
 * How a list split over several pages is numbered, worked out from its page links.
 * Both page numbers (/page/3/, ?page=3) and item offsets (?start=40) are understood.
 */
data class Paging(
    /** The address with the number that changes from page to page replaced by {n}. */
    val template: String,
    /** The number in the address for page 1, and how much it goes up per page. */
    val first: Int,
    val step: Int,
    /** Which page the address you gave is. */
    val current: Int,
    /** The highest page linked from it. Sites often show only a few, so there may be more. */
    val last: Int,
    /** Page 1's own address, when it differs from the pattern (often the list's plain address). */
    val pageOne: String = "",
) {
    fun url(page: Int): String =
        if (page <= 1 && pageOne.isNotBlank()) pageOne else template.replace("{n}", (first + (page - 1) * step).toString())
}

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
        // Lazy-loading sites keep the real address in an attribute of their own:
        // data-src, data-original, data-lazy_src and so on.
        val lazy = img.attributes().asList()
            .filter { it.key.startsWith("data-") && it.key.contains("src") && !it.key.contains("set") }
            .map { img.absUrl(it.key) }.firstOrNull { it.startsWith("http") }
        val first = img.attr("srcset").ifBlank { img.attr("data-srcset") }.split(",").firstOrNull()?.trim()?.substringBefore(' ').orEmpty()
        val fromSet = if (first.isBlank()) "" else runCatching { URI(img.baseUri()).resolve(first).toString() }.getOrDefault("")
        return lazy ?: img.absUrl("src").takeIf { it.isNotBlank() && !it.startsWith("data:") } ?: fromSet
    }

    /**
     * The site an address belongs to, ignoring sub-addresses: "author.itch.io"
     * and "itch.io" are the same site, and so are "www.bbc.co.uk" and "bbc.co.uk".
     */
    fun siteOf(url: String): String {
        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("").lowercase()
        val labels = host.split('.').filter { it.isNotBlank() }
        if (labels.size <= 2) return labels.joinToString(".")
        val keep = if (labels[labels.size - 2].length <= 3 && labels.last().length == 2) 3 else 2
        return labels.takeLast(keep).joinToString(".")
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

    /** Link text that is a control rather than a game's name. */
    private val CONTROL_TEXT = Regex(
        "^(more|read more|see more|show more|view|view all|see all|details|info|download|downloads|play|" +
            "next|prev|previous|back|home|top|log ?in|sign ?in|sign ?up|register|[«»‹›<>←→↑]+|\\d+)$",
        RegexOption.IGNORE_CASE,
    )

    /** Addresses of a site's own pages (menus, tags, paging) rather than of games. */
    private val UTILITY_PATH = Regex(
        "/(tags?|categor(y|ies)|page|log(in|out)|register|sign(up|in|-in|-up)|account|cart|search|privacy|terms|" +
            "contact|about|help|faq|forums?|users?|profile|members?|login\\.php)(/|$)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Which part of a site a link goes to: its host and folders, with numbers
     * evened out. "/game/115374/airsea-attack/" and "/game/115332/auto-sports-10/"
     * both give "/game/#", while "/about" gives "/".
     */
    fun pathPattern(url: String): String {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return url
        }
        val parts = uri.path.orEmpty().split('/').filter { it.isNotBlank() }.dropLast(1)
            .map { it.replace(Regex("\\d+"), "#") }
        return siteOf(url) + "/" + parts.joinToString("/")
    }

    /**
     * The games on a list page. Every link is a candidate, with or without a picture.
     * Links are grouped by where they sit on the page and which part of the site
     * they go to, and the biggest group is taken: a list of games is many links of
     * the same kind, side by side, all going to the same sort of page. Menus,
     * paging and links to other parts of the site land in groups of their own.
     *
     * Games without a picture here usually have one on their own page, which
     * Fetch pictures picks up after they're added.
     *
     * Returns nothing when the page doesn't look like a list (fewer than four games).
     */
    fun listGames(html: String, url: String): List<WebGame> {
        val doc = Jsoup.parse(html, url)
        val here = url.substringBefore('#')
        val site = siteOf(url)
        class Entry(val key: String, val at: Int, val game: WebGame)
        val entries = ArrayList<Entry>()
        // Covers by the address they link to, so a name found on another link can be given its picture.
        val pictures = HashMap<String, String>()
        for ((at, link) in doc.select("a[href]").withIndex()) {
            val href = link.absUrl("href").substringBefore('#')
            if (!href.startsWith("http") || href == here) continue
            val img = link.selectFirst("img")
            val image = img?.let(::picture).orEmpty()
            val title: String
            if (img != null && image.isNotBlank()) {
                pictures.putIfAbsent(href, image)
                // The name: the picture's own description or the link's tooltip, then (a few levels
                // up in the same card) another link to the same game, then the link's own text,
                // then a heading. A cover link's own text is often just a badge such as "GIF" or "NEW".
                val around = link.parents().take(3)
                title = clean(
                    (
                        listOf(img.attr("alt"), link.attr("title"), link.attr("aria-label")) +
                            around.map { c ->
                                c.select("a[href]").firstOrNull {
                                    it !== link && it.absUrl("href").substringBefore('#') == href && it.text().isNotBlank()
                                }?.text().orEmpty()
                            } +
                            listOf(link.text()) +
                            around.map { c -> c.selectFirst("h1, h2, h3, h4, h5, .title, [class*=title], [class*=name]")?.text().orEmpty() }
                        ).firstOrNull { clean(it).length >= 2 }.orEmpty(),
                )
            } else {
                // A plain text link: only on this site, outside menus, and not a control like "Next".
                if (siteOf(href) != site) continue
                if (link.parents().any { it.tagName() in setOf("nav", "header", "footer", "aside") }) continue
                if (UTILITY_PATH.containsMatchIn(runCatching { URI(href).path.orEmpty() }.getOrDefault(""))) continue
                title = clean(link.text().ifBlank { link.attr("title") })
                if (CONTROL_TEXT.matches(title)) continue
            }
            if (title.length < 2) continue
            entries += Entry(shape(link) + " @ " + pathPattern(href), at, WebGame(title.take(160), href, image))
        }
        val groups = entries.groupBy { it.key }.values
            .map { group ->
                group.distinctBy { it.game.url }
                    .map { e -> Entry(e.key, e.at, e.game.copy(image = e.game.image.ifBlank { pictures[e.game.url].orEmpty() })) }
            }
            .filter { it.size >= 4 }
        // The most games wins, with games that have pictures counting for half as much
        // again, so a grid of covers beats a slightly longer row of tags. On a tie,
        // whichever comes first on the page.
        fun score(group: List<Entry>) = group.size + 0.5 * group.count { it.game.image.isNotBlank() }
        val best = groups.maxWithOrNull(
            compareBy<List<Entry>> { score(it) }.thenByDescending { group -> group.first().at },
        ).orEmpty()
        return best.map { it.game }
    }

    private val NEXT_TEXT = Regex("^(next( page)?|older|more|[›»>→]|next ?[›»>→]+|[›»>→] ?next)$", RegexOption.IGNORE_CASE)
    private val LAST_TEXT = Regex("^(last( page)?|end|[»]{1,2}|[›>]{2}|last ?[»›>]+)$", RegexOption.IGNORE_CASE)
    private val PAGE_TEXT = Regex("^(page ?)?(\\d{1,5})$", RegexOption.IGNORE_CASE)

    /**
     * Finds how a list's pages are numbered. Looks at links on the same site whose text is a
     * page number ("2", "Page 3") or a control ("Next", "»", "Last"), and at the number in each
     * one's address that changes along with the text. The pattern that most numbered links agree
     * on wins. Returns null when the page isn't split into pages.
     */
    fun paging(html: String, url: String): Paging? {
        val doc = Jsoup.parse(html, url)
        val here = url.substringBefore('#')
        val site = siteOf(url)
        class Cand(val template: String, val num: Int, val pageNo: Int?, val control: Boolean, val href: String)
        val cands = ArrayList<Cand>()
        var pageOneLink = ""
        for (link in doc.select("a[href]")) {
            val href = link.absUrl("href").substringBefore('#')
            if (!href.startsWith("http") || siteOf(href) != site) continue
            val text = clean(link.text()).ifBlank { clean(link.attr("aria-label")).ifBlank { clean(link.attr("title"))  } }
            val pageNo = PAGE_TEXT.matchEntire(text)?.groupValues?.get(2)?.toIntOrNull()
            val control = "next" in link.attr("rel").lowercase() || NEXT_TEXT.matches(text) || LAST_TEXT.matches(text)
            if (pageNo == null && !control) continue
            if (pageNo == 1) pageOneLink = href
            // Only the part after the site's name can hold the page number.
            val start = href.indexOf('/', href.indexOf("://") + 3).let { if (it < 0) href.length else it }
            for (m in Regex("\\d+").findAll(href)) {
                if (m.range.first < start || m.value.length > 7) continue
                cands += Cand(href.replaceRange(m.range, "{n}"), m.value.toInt(), pageNo, control, href)
            }
        }
        class Fit(val template: String, val first: Int, val step: Int, val numbered: List<Cand>, val all: List<Cand>)
        val fits = cands.groupBy { it.template }.mapNotNull { (template, group) ->
            val numbered = group.filter { it.pageNo != null && it.pageNo > 1 }.distinctBy { it.pageNo }
            if (numbered.isEmpty()) return@mapNotNull null
            // Page numbers in the address, or offsets: page n starts at (n - 1) × step.
            val fit = when {
                numbered.all { it.num == it.pageNo } -> Fit(template, 1, 1, numbered, group)
                else -> {
                    val step = numbered.first().let { it.num / (it.pageNo!! - 1) }
                    if (step > 1 && numbered.all { it.num == (it.pageNo!! - 1) * step }) Fit(template, 0, step, numbered, group) else null
                }
            } ?: return@mapNotNull null
            // One numbered link is only enough alongside a "Next" or "Last" link of the same kind.
            if (numbered.size < 2 && group.none { it.control }) null else fit
        }
        val best = fits.maxByOrNull { it.numbered.size } ?: return null
        fun pageOf(num: Int) = (num - best.first) / best.step + 1
        val pattern = Regex(Regex.escape(best.template).replace("{n}", "\\E(\\d+)\\Q"))
        val current = pattern.matchEntire(here)?.groupValues?.get(1)?.toIntOrNull()?.let(::pageOf) ?: 1
        val last = maxOf(current, (best.all.map { pageOf(it.num) }).maxOrNull() ?: current)
        if (last <= 1) return null
        val pageOne = when {
            pageOneLink.isNotBlank() -> pageOneLink
            current == 1 -> here
            else -> ""
        }
        return Paging(best.template, best.first, best.step, current, last, pageOne)
    }

    /** The games on one page, looking again with the hidden browser if the page fills its list in with scripts. */
    private suspend fun gamesOn(ctx: Context, url: String): Triple<List<WebGame>, Paging?, String> {
        val body = html(ctx, url)
        var games = withContext(Dispatchers.Default) { listGames(body, url) }
        var paging = withContext(Dispatchers.Default) { paging(body, url) }
        if (games.size < 10) {
            val shown = PageReader.read(ctx, url, BUILT_SCRIPT, timeoutMs = 25_000)
            if (shown != null) {
                val more = withContext(Dispatchers.Default) { listGames(shown, url) }
                if (more.size > games.size) games = more
                if (paging == null) paging = withContext(Dispatchers.Default) { paging(shown, url) }
            }
        }
        return Triple(games, paging, body)
    }

    /**
     * Reads pages [from] to [to] of a list (or until the pages run out, when [to] is null),
     * one after another with a short pause, and gathers the games from all of them.
     * [onPage] hears after each page: its number, the highest page known so far, and the games so far.
     * Stops early when a page brings no new games, which is what happens past the last page.
     */
    suspend fun readPages(
        ctx: Context,
        paging: Paging,
        from: Int,
        to: Int?,
        onPage: (page: Int, last: Int, games: List<WebGame>) -> Unit,
    ): List<WebGame> {
        val seen = LinkedHashMap<String, WebGame>()
        var last = paging.last
        var page = maxOf(1, from)
        val end = to ?: MAX_PAGES
        while (page <= minOf(end, MAX_PAGES) && (to != null || page <= last)) {
            val url = paging.url(page)
            val (games, found, _) = try {
                gamesOn(ctx, url)
            } catch (e: WebError) {
                if (page == from) throw e else break
            }
            // Later pages link further on, so the last page known keeps growing.
            if (found != null && found.template == paging.template) last = maxOf(last, found.last)
            var added = 0
            for (g in games) if (seen.putIfAbsent(g.url, g) == null) added++
            onPage(page, maxOf(last, page), seen.values.toList())
            if (added == 0) break
            page++
            if (page <= minOf(end, MAX_PAGES)) delay(PAGE_PAUSE_MS)
        }
        return seen.values.toList()
    }

    /** The most pages read in one go, and the pause between them so the site isn't hammered. */
    const val MAX_PAGES = 500
    private const val PAGE_PAUSE_MS = 800L

    /** The whole page as the hidden browser sees it, for sites that only answer a real browser. */
    private val HTML_SCRIPT = "(function(){ ${Achievements.READY} return document.documentElement.outerHTML; })()"

    /**
     * The page once its scripts have filled it in: waits until the number of
     * links stops growing (checked every 1.5 s) before handing the page over.
     */
    private val BUILT_SCRIPT = """
        (function(){ ${Achievements.READY}
          var n = document.querySelectorAll('a[href]').length;
          var last = (typeof window.__bqLinks === 'number') ? window.__bqLinks : -1;
          window.__bqLinks = n;
          if (n !== last) return '';
          return document.documentElement.outerHTML;
        })()
    """.trimIndent()

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
        // Many sites send a bare page and fill the list in with scripts; gamesOn looks again
        // with the hidden browser, which runs them, when little turned up.
        val (games, paging, body) = gamesOn(ctx, url)
        val page = withContext(Dispatchers.Default) { pageGame(body, url) }
        if (page == null && games.isEmpty()) throw WebError("No game or list could be found on that page.")
        return WebFound(page?.title.orEmpty(), page, games, paging = paging?.takeIf { games.isNotEmpty() })
    }
}
