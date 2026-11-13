package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebImportTest {
    @Test fun `the link is picked out of shared text`() {
        assertEquals("https://archive.org/details/abc", WebImport.firstUrl("Look at this: https://archive.org/details/abc."))
        assertEquals("https://example.com/a?b=1", WebImport.firstUrl("https://example.com/a?b=1"))
        assertNull(WebImport.firstUrl("no link here"))
    }

    @Test fun `Internet Archive addresses are recognised as items, collections or searches`() {
        val details = WebImport.archiveLink("https://archive.org/details/rpgmaker-net-game-archive?tab=collection")
        assertEquals("rpgmaker-net-game-archive", (details as WebImport.ArchiveLink.Details).id)
        val search = WebImport.archiveLink("https://archive.org/search?query=subject%3A%22homebrew%22+game+boy")
        assertEquals("subject:\"homebrew\" game boy", (search as WebImport.ArchiveLink.Search).query)
        assertNull(WebImport.archiveLink("https://example.com/details/thing"))
        assertNull(WebImport.archiveLink("https://archive.org/about/"))
    }

    @Test fun `Archive search answer gives each item's name, link, picture and subjects`() {
        val body = """{"responseHeader":{"status":0},"response":{"numFound":3820,"start":0,"docs":[
            {"identifier":"rmn-game-310-legacies-of-dondoran-2","title":"Legacies of Dondoran 2","subject":["RPG","RPG Maker 2003"]},
            {"identifier":"rmn-game-334-battle-of-justice","title":"Battle Of Justice","subject":"Adventure; Fantasy"},
            {"identifier":"untitled-item"}]}}"""
        val (games, total) = WebImport.archiveResults(body)
        assertEquals(3820, total)
        assertEquals(3, games.size)
        assertEquals("Legacies of Dondoran 2", games[0].title)
        assertEquals("https://archive.org/details/rmn-game-310-legacies-of-dondoran-2", games[0].url)
        assertEquals("https://archive.org/services/img/rmn-game-310-legacies-of-dondoran-2", games[0].image)
        assertEquals(listOf("RPG", "RPG Maker 2003"), games[0].genres)
        assertEquals(listOf("Adventure", "Fantasy"), games[1].genres)
        assertEquals("untitled-item", games[2].title)
    }

    private val listPage = """
        <html><head><title>Homebrew games - Example</title>
        <meta property="og:title" content="Homebrew games"><meta property="og:image" content="https://example.com/share.jpg"></head>
        <body>
          <nav><a href="/"><img src="/static/logo.png" alt="Example"></a><a href="/about">About</a></nav>
          <div class="grid">
            <div class="card"><a class="thumb" href="/game/alpha"><img src="/img/alpha.jpg" alt="Alpha Quest"></a><h3><a href="/game/alpha">Alpha Quest</a></h3></div>
            <div class="card"><a class="thumb" href="/game/beta"><img data-src="/img/beta.jpg" src="data:image/gif;base64,AAAA"></a><h3><a href="/game/beta">Beta Racer</a></h3></div>
            <div class="card"><a class="thumb" href="/game/gamma"><img src="/img/gamma.jpg" alt=""></a><h3><a href="/game/gamma">Gamma Blaster</a></h3></div>
            <div class="card"><a class="thumb" href="/game/delta"><img srcset="/img/delta.jpg 1x, /img/delta@2x.jpg 2x" alt="Delta Dash"></a></div>
            <div class="card"><a class="thumb" href="/game/alpha"><img src="/img/alpha.jpg" alt="Alpha Quest"></a></div>
          </div>
          <footer><a href="https://ads.example.net/x"><img src="https://ads.example.net/banner.png" alt="Advert"></a></footer>
        </body></html>
    """.trimIndent()

    @Test fun `a list page gives its run of games, without the logo, adverts or repeats`() {
        val games = WebImport.listGames(listPage, "https://example.com/homebrew")
        assertEquals(listOf("Alpha Quest", "Beta Racer", "Gamma Blaster", "Delta Dash"), games.map { it.title })
        assertEquals("https://example.com/game/beta", games[1].url)
        assertEquals("https://example.com/img/beta.jpg", games[1].image)
        assertEquals("https://example.com/img/delta.jpg", games[3].image)
    }

    @Test fun `a page with only a few pictures is not treated as a list`() {
        val page = """<html><head><title>One Game</title></head><body>
            <a href="/a"><img src="/1.jpg" alt="Shot one"></a><a href="/b"><img src="/2.jpg" alt="Shot two"></a></body></html>"""
        assertTrue(WebImport.listGames(page, "https://example.com/game").isEmpty())
    }

    @Test fun `any page can be added as one game with its title and picture`() {
        val game = WebImport.pageGame(listPage, "https://example.com/homebrew")!!
        assertEquals("Homebrew games", game.title)
        assertEquals("https://example.com/share.jpg", game.image)
        assertEquals("https://example.com/homebrew", game.url)
        assertNull(WebImport.pageGame("<html><body></body></html>", "https://example.com/x"))
    }

    @Test fun `the same address always gets the same wishlist id`() {
        assertEquals(WebImport.gameId("https://Archive.org/details/ABC/"), WebImport.gameId("http://archive.org/details/abc"))
    }
}
