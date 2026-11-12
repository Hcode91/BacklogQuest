package com.backlogquest.app.discover

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AdditionalCatalogueTests {
    private fun fixture(name: String)=javaClass.classLoader!!.getResource(name)!!.readText()
    @Test fun `PC RPGMaker grouping and new consoles register correct sources`() {
        val registry=SourceRegistry.create(JsonHttp(JsonHttp.client()))
        assertEquals(setOf("rpgmaker", "rpgmaker-archive"),registry.forConsole("PC").filter { it.section=="RPGMaker" }.map { it.id }.toSet())
        assertEquals(setOf("VITA", "PS3", "PS4"),Consoles.all.filter { it.id in setOf("VITA","PS3","PS4") }.map { it.id }.toSet())
        assertTrue(registry.forConsole("DS").any { it.id=="gamebrew-ds" })
        assertTrue(registry.forConsole("GB").any { it.id=="gamebrew-gb" })
        assertTrue(registry.forConsole("PSP").any { it.id=="gamebrew-psp" })
        assertTrue(registry.forConsole("PS3").isEmpty())
        assertTrue(registry.forConsole("PS4").isEmpty())
        assertTrue(registry.providers.all { it.nativeBrowsing })
    }
    @Test fun `RPGMaker real table metadata and details normalize`() {
        val page=HtmlCatalogueParser.catalogue(fixture("rpgmaker-catalogue.html"),HtmlSite.RMN,1)
        assertEquals(2,page.games.size); assertEquals(2,page.nextPage)
        val g=page.games.first()
        assertEquals("Dragon Child",g.title); assertEquals("DragonMedic",g.developer)
        assertEquals("RPG",g.genres.single()); assertEquals(listOf("PC"),g.consoles)
        val detail=HtmlCatalogueParser.details(fixture("rpgmaker-details.html"),HtmlSite.RMN,g)
        assertTrue(detail.description.contains("vagabond")); assertNotNull(detail.artwork)
        assertTrue(detail.downloads.isEmpty())
    }
    @Test fun `HTML layout failures and unsafe game URLs are rejected`() {
        assertNull(HtmlCatalogueParser.article("https://example.com/games/1/",HtmlSite.RMN))
        assertNull(HtmlCatalogueParser.article("/admin/",HtmlSite.RMN))
        try { HtmlCatalogueParser.catalogue("<h1>Challenge</h1>",HtmlSite.RMN,1);fail("Broken layout accepted") } catch(_: java.io.IOException) { }
    }
    @Test fun `archive real collection API normalizes records and explicit paging`() = runTest {
        MockWebServer().use { server ->
            server.start();val p=RpgMakerArchive(JsonHttp(JsonHttp.client()),server.url("/advancedsearch.php").toString())
            server.enqueue(MockResponse().setBody(fixture("archive-search.json")))
            val page=p.fetchCataloguePage(1)
            assertEquals(3820,page.total); assertEquals(2,page.nextPage)
            assertEquals("Eternity's Point",page.games.first().title)
            assertEquals("RPGMaker",p.section)
            val request=server.takeRequest().requestUrl!!
            assertEquals("collection:rpgmaker-net-game-archive",request.queryParameter("q"))
            assertEquals("50",request.queryParameter("rows"))
            p.fetchGames(false);assertEquals(1,server.requestCount)
        }
        val metadata=JsonParser.parseString(fixture("archive-detail.json")).asJsonObject.getAsJsonObject("metadata")
        val g=ArchiveParser.game(metadata)!!
        assertTrue(g.artwork!!.startsWith("https://archive.org/services/img/"))
        assertFalse(ArchiveParser.validId("../private"));assertFalse(ArchiveParser.validId(".."))
        assertTrue(g.downloads.isEmpty());assertNull(g.release)
    }
    @Test fun `Game Boy system column keeps Color-only games out of monochrome results`() {
        val game=GameBrewParser.catalogue(fixture("gamebrew-gbc-system.html"),"GB","gamebrew-gb").single()
        assertEquals(listOf("GBC"),game.consoles)
        assertEquals(0,cataloguePage(listOf(game),GameQuery("GB")).total)
        assertEquals(1,cataloguePage(listOf(game),GameQuery("GBC")).total)
        assertTrue(SourceRegistry.create(JsonHttp(JsonHttp.client())).forConsole("GBC").any { it.id=="gamebrew-gb" })
    }

}
