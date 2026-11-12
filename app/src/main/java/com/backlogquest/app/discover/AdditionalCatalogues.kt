package com.backlogquest.app.discover

import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI

class RequestGate {
    private val mutex = Mutex()
    private var last = 0L
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        val wait = 1000 - (System.nanoTime() / 1_000_000 - last)
        if(wait > 0) delay(wait)
        try { block() } finally { last = System.nanoTime() / 1_000_000 }
    }
}
enum class HtmlSite(val provider: String, val label: String, val console: String, val group: String, val url: String) {
    RMN("rpgmaker", "RPGMaker.net", "PC", "RPGMaker", "https://rpgmaker.net/games/?portal=yes")
}
class HtmlCatalogue(private val http: JsonHttp, private val site: HtmlSite, private val endpoint: String = site.url) : PagedGameProvider {
    override val id = site.provider
    override val name = site.label
    override val website = site.url
    override val section = site.group
    override val supportedConsoles = setOf(site.console)
    override val description = "Public catalogue pages with native listings and saved details. Pages load only on request. Verified 2026-11-07."
    override val refreshDetails = true
    private val gate = RequestGate()
    private var first: CatalogueBatch? = null
    override fun clearMemoryCache() { first = null }
    override suspend fun fetchGames(refresh: Boolean): List<Game> = if(!refresh && first != null) first!!.games else fetchCataloguePage(1).games
    override suspend fun fetchCataloguePage(page: Int): CatalogueBatch {
        require(page >= 1)
        val url = endpoint.toHttpUrl().newBuilder().setQueryParameter("p", page.toString()).build().toString()
        return HtmlCatalogueParser.catalogue(gate.run { http.text(url) }, site, page).also { if(page == 1) first = it }
    }
    override suspend fun fetchGameDetails(game: Game): Game {
        require(game.provider == id && game.consoles == listOf(site.console) && HtmlCatalogueParser.article(game.sourceUrl, site) != null)
        return HtmlCatalogueParser.details(gate.run { http.text(game.sourceUrl) }, site, game)
    }
}
object HtmlCatalogueParser {
    fun article(value: String, site: HtmlSite): String? = runCatching {
        val root = URI(site.url)
        val url = root.resolve(value).toString()
        if(!UrlSafety.valid(url)) return null
        val uri = URI(url)
        if(uri.host?.removePrefix("www.") != root.host.removePrefix("www.")) return null
        if(!uri.path.matches(Regex("/games/[0-9]+/"))) return null
        url
    }.getOrNull()
    private fun image(value: String?, site: HtmlSite): String? = value?.takeIf { it.isNotBlank() }?.let { runCatching { URI(site.url).resolve(it).toString() }.getOrNull() }?.takeIf(UrlSafety::valid)
    fun catalogue(html: String, site: HtmlSite, page: Int): CatalogueBatch {
        val doc = Jsoup.parse(html, site.url)
        val games = doc.select("tr").mapNotNull { row ->
            val link = row.selectFirst("a.name[href]") ?: return@mapNotNull null
            val url = article(link.attr("href"), site) ?: return@mapNotNull null
            val slug = URI(url).path.trim('/').substringAfter("games/")
            Game("${site.provider}:$slug", site.provider, slug, link.text(), row.select("td").firstOrNull()?.select("p")?.lastOrNull()?.text().orEmpty(), listOf(site.console), url,
                artwork = image(link.selectFirst("img")?.attr("src"), site), developer = row.selectFirst("a[href^=/users/]")?.text(),
                genres = row.select("a[href*=genre_tag]").map { it.text() }.distinct())
        }
        if(games.isEmpty()) throw IOException("${site.label} catalogue layout changed or returned no readable entries")
        val next = doc.select("a[href]").any { link ->
            runCatching {
                val uri = URI(site.url).resolve(link.attr("href"))
                if(uri.host?.removePrefix("www.") != URI(site.url).host.removePrefix("www.")) false
                else uri.path == "/games/" && uri.query.orEmpty().split('&').contains("p=${page+1}")
            }.getOrDefault(false)
        }
        return CatalogueBatch(games.distinctBy { it.id }, if(next) page+1 else null)
    }
    fun details(html: String, site: HtmlSite, summary: Game): Game {
        val doc = Jsoup.parse(html, site.url)
        val content = doc.selectFirst("#game_description") ?: throw IOException("${site.label} detail layout changed")
        val description = cleanDescription(content.html())
        val cover = image(doc.selectFirst("meta[property=og:image]")?.attr("content"), site) ?: summary.artwork
        val images = content.select("img[src]").mapNotNull { image(it.attr("src"), site) }.filter { it != cover }.distinct().take(12)
        return summary.copy(description = description.ifBlank { summary.description }, artwork = cover, screenshots = images, detailsFetchedAt = System.currentTimeMillis())
    }
}
class RpgMakerArchive(private val http: JsonHttp, private val endpoint: String = "https://archive.org/advancedsearch.php") : PagedGameProvider {
    override val id = "rpgmaker-archive"
    override val name = "Internet Archive · RPGMaker.net archive"
    override val website = "https://archive.org/details/rpgmaker-net-game-archive"
    override val section = "RPGMaker"
    override val supportedConsoles = setOf("PC")
    override val description = "Archived RPGMaker.net games. Loads 50 records per requested page. Public archive API verified 2026-11-07."
    override val refreshDetails = true
    private val gate = RequestGate()
    private var first: CatalogueBatch? = null
    override fun clearMemoryCache() { first = null }
    override suspend fun fetchGames(refresh: Boolean): List<Game> = if(!refresh && first != null) first!!.games else fetchCataloguePage(1).games
    override suspend fun fetchCataloguePage(page: Int): CatalogueBatch {
        require(page >= 1)
        val url = endpoint.toHttpUrl().newBuilder().addQueryParameter("q", "collection:rpgmaker-net-game-archive")
            .addQueryParameter("output", "json").addQueryParameter("rows", "50").addQueryParameter("page", page.toString())
            .addQueryParameter("sort[]", "identifier asc")
        listOf("identifier", "title", "description", "creator").forEach { url.addQueryParameter("fl[]", it) }
        val json = gate.run { http.get(url.build().toString()) }.asJsonObject
        val response = json.get("response")?.takeIf { it.isJsonObject }?.asJsonObject ?: throw IOException("Archive search response changed")
        val total = response.str("numFound")?.toIntOrNull() ?: throw IOException("Archive result count is missing")
        val games = response.array("docs").mapNotNull { row -> if(row.isJsonObject) ArchiveParser.game(row.asJsonObject) else null }
        if(response.array("docs").size() > 0 && games.isEmpty()) throw IOException("Archive game metadata changed")
        return CatalogueBatch(games, if(page.toLong()*50 < total) page+1 else null, total).also { if(page == 1) first = it }
    }
    override suspend fun fetchGameDetails(game: Game): Game {
        require(game.provider == id && ArchiveParser.validId(game.slug))
        val json = gate.run { http.get("https://archive.org/metadata/${game.slug}") }.asJsonObject
        val metadata = json.get("metadata")?.takeIf { it.isJsonObject }?.asJsonObject ?: throw IOException("Archived game metadata unavailable")
        val fresh = ArchiveParser.game(metadata) ?: throw IOException("Archived game was removed")
        require(fresh.id == game.id)
        return fresh.copy(detailsFetchedAt = System.currentTimeMillis())
    }
}
object ArchiveParser {
    fun validId(id: String) = id !in setOf(".", "..") && id.matches(Regex("[a-zA-Z0-9_.-]{1,255}"))
    fun game(json: JsonObject): Game? {
        val slug = json.str("identifier")?.takeIf(::validId) ?: return null
        val title = json.str("title") ?: return null
        val description = json.str("description") ?: json.strings("description").joinToString("\n")
        val creator = json.str("creator") ?: json.strings("creator").takeIf { it.isNotEmpty() }?.joinToString(", ")
        return Game("rpgmaker-archive:$slug", "rpgmaker-archive", slug, title, cleanDescription(description), listOf("PC"),
            "https://archive.org/details/$slug", artwork = "https://archive.org/services/img/$slug", developer = creator)
    }
}
object SourceRegistry {
    fun create(http: JsonHttp) = ProviderRegistry(
        listOf(UniversalDb(http), HomebrewRepository(http), HtmlCatalogue(http, HtmlSite.RMN), RpgMakerArchive(http)) +
            GameBrewCatalogue.all.map { GameBrew(http, catalogue = it) })
}
