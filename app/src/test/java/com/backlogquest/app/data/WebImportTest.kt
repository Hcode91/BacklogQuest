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

    private val textList = """
        <html><head><title>Games - Example</title></head><body>
          <nav><a href="/">Home</a><a href="/games/">Games</a><a href="/forums/">Forums</a><a href="/about">About</a><a href="/login">Log in</a></nav>
          <p>Welcome! <a href="https://twitter.com/example">Follow us</a></p>
          <table>
            <tr><td><a href="/games/101/">Alpha Quest</a></td><td><a href="/creators/5/">alice</a></td><td><a href="/games/101/">Download</a></td></tr>
            <tr><td><a href="/games/102/">Beta Racer</a></td><td><a href="/creators/6/">bob</a></td><td><a href="/games/102/">Download</a></td></tr>
            <tr><td><a href="/games/103/">Gamma Blaster</a></td><td><a href="/creators/7/">carol</a></td><td><a href="/games/103/">Download</a></td></tr>
            <tr><td><a href="/games/104/">Delta Dash</a></td><td><a href="/creators/8/">dave</a></td><td><a href="/games/104/">Download</a></td></tr>
            <tr><td><a href="/games/105/">Epsilon</a></td><td><a href="/creators/9/">erin</a></td><td><a href="/games/105/">Download</a></td></tr>
          </table>
          <div class="pager"><a href="?p=2">2</a><a href="?p=3">3</a><a href="?p=4">4</a><a href="?p=5">5</a><a href="?p=2">Next ›</a></div>
          <footer><a href="/privacy">Privacy</a><a href="/terms">Terms</a><a href="/contact">Contact</a><a href="/help">Help</a></footer>
        </body></html>
    """.trimIndent()

    @Test fun `a list of plain text links is found, without menus, paging or the author column`() {
        val games = WebImport.listGames(textList, "https://example.com/games/")
        assertEquals(listOf("Alpha Quest", "Beta Racer", "Gamma Blaster", "Delta Dash", "Epsilon"), games.map { it.title })
        assertEquals("https://example.com/games/101/", games[0].url)
        assertTrue(games.all { it.image.isBlank() })
    }

    @Test fun `a cover on one link and the name on another are joined up`() {
        val cards = (1..5).joinToString("") { i ->
            """<div class="row"><a class="pic" href="/g/$i/"><img src="/c/$i.jpg"></a><a class="name" href="/g/$i/">Game number $i</a></div>"""
        }
        val games = WebImport.listGames("<html><body>$cards</body></html>", "https://example.com/list")
        assertEquals(5, games.size)
        assertEquals("Game number 1", games[0].title)
        assertEquals("https://example.com/c/1.jpg", games[0].image)
    }

    @Test fun `games on their own sub-sites with lazy-loaded covers are found (itch-io style)`() {
        val cells = listOf("alpha" to "Alpha Quest", "beta" to "Beta Racer", "gamma" to "Gamma Blaster", "delta" to "Delta Dash", "eps" to "Epsilon")
            .joinToString("") { (slug, name) ->
                """<div class="game_cell has_cover"><div class="game_thumb"><a class="thumb_link game_link" href="https://$slug-dev.itch.io/$slug">""" +
                    """<img class="lazy_loaded" data-lazy_src="https://img.itch.zone/$slug.png">""" +
                    (if (slug == "beta") """<div class="gif_label">GIF</div>""" else "") + """</a></div>""" +
                    """<div class="game_cell_data"><div class="game_title"><a class="title game_link" href="https://$slug-dev.itch.io/$slug">$name</a></div>""" +
                    """<div class="game_author"><a href="https://$slug-dev.itch.io">$slug-dev</a></div></div></div>"""
            }
        val tags = listOf("pixel-art", "retro", "2d", "cute", "puzzle", "rpg").joinToString("") {
            """<a class="tag_btn" href="https://itch.io/games/free/tag-$it">${it.replaceFirstChar { c -> c.uppercase() }}</a>"""
        }
        val html = """<html><body><div class="tags">$tags</div><div class="grid">$cells</div></body></html>"""
        val games = WebImport.listGames(html, "https://itch.io/games/free/tag-gameboy")
        assertEquals(listOf("Alpha Quest", "Beta Racer", "Gamma Blaster", "Delta Dash", "Epsilon"), games.map { it.title })
        assertEquals("https://alpha-dev.itch.io/alpha", games[0].url)
        assertEquals("https://img.itch.zone/alpha.png", games[0].image)
    }

    @Test fun `sub-addresses belong to the same site`() {
        assertEquals("itch.io", WebImport.siteOf("https://objetdiscret.itch.io/tai-fab-museum"))
        assertEquals("itch.io", WebImport.siteOf("https://itch.io/games"))
        assertEquals("bbc.co.uk", WebImport.siteOf("https://www.bbc.co.uk/news"))
        assertEquals("example.com", WebImport.siteOf("https://example.com/x"))
    }

    @Test fun `links to the same sort of page share a pattern, other parts of the site don't`() {
        assertEquals(
            WebImport.pathPattern("https://www.mobygames.com/game/115374/airsea-attack/"),
            WebImport.pathPattern("https://www.mobygames.com/game/115332/auto-sports-10/"),
        )
        assertEquals("example.com/", WebImport.pathPattern("https://example.com/about"))
        assertTrue(WebImport.pathPattern("https://example.com/games/101/") != WebImport.pathPattern("https://example.com/creators/5/"))
    }

    @Test fun `a page with only a few pictures is not treated as a list`() {
        val page = """<html><head><title>One Game</title></head><body>
            <nav><a href="/">Home</a><a href="/news">News</a><a href="/shop">Shop</a><a href="/about">About</a></nav>
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
