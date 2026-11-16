package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PagingTest {
    private fun page(nav: String, games: String = "") = "<html><body><div class=list>$games</div><div class=pages>$nav</div></body></html>"

    private val games = (1..12).joinToString("") { "<div class=card><a href=\"/game/$it/title-$it/\">Game $it</a></div>" }

    @Test fun pathPagesFromTheFirstPage() {
        val html = page(
            """<a href="/browse/platform/dos/page/2/">2</a><a href="/browse/platform/dos/page/3/">3</a>
               <a href="/browse/platform/dos/page/4/">4</a><span>…</span><a href="/browse/platform/dos/page/57/">57</a>
               <a href="/browse/platform/dos/page/2/" rel="next">Next ›</a>""",
            games,
        )
        val p = WebImport.paging(html, "https://www.example.com/browse/platform/dos/")
        assertNotNull(p)
        p!!
        assertEquals("https://www.example.com/browse/platform/dos/page/{n}/", p.template)
        assertEquals(1, p.current)
        assertEquals(57, p.last)
        assertEquals("https://www.example.com/browse/platform/dos/", p.url(1))
        assertEquals("https://www.example.com/browse/platform/dos/page/5/", p.url(5))
    }

    @Test fun knowsWhichPageItIsOn() {
        val html = page(
            """<a href="/list?page=1">1</a><a href="/list?page=2">2</a><a href="/list?page=4">4</a><a href="/list?page=9">Last</a>""",
            games,
        )
        val p = WebImport.paging(html, "https://example.com/list?page=3")!!
        assertEquals("https://example.com/list?page={n}", p.template)
        assertEquals(3, p.current)
        assertEquals(9, p.last)
        assertEquals("https://example.com/list?page=1", p.url(1))
    }

    @Test fun offsets() {
        val html = page(
            """<a href="/search?q=x&start=40">2</a><a href="/search?q=x&start=80">3</a><a href="/search?q=x&start=120">4</a>""",
            games,
        )
        val p = WebImport.paging(html, "https://example.com/search?q=x")!!
        assertEquals(40, p.step)
        assertEquals(0, p.first)
        assertEquals(4, p.last)
        assertEquals("https://example.com/search?q=x&start=80", p.url(3))
    }

    @Test fun gameIdsArentPages() {
        // Numbers in game links, with names as their text, aren't paging.
        assertNull(WebImport.paging(page("", games), "https://example.com/games/"))
    }

    @Test fun otherSitesIgnored() {
        val html = page("""<a href="https://other.com/p/2">2</a><a href="https://other.com/p/3">3</a>""", games)
        assertNull(WebImport.paging(html, "https://example.com/games/"))
    }

    @Test fun platformNumbersInTheAddressAreLeftAlone() {
        val html = page(
            """<a href="/platform/c64/page/2/">2</a><a href="/platform/c64/page/3/">3</a><a href="/platform/c64/page/2/">»</a>""",
            games,
        )
        val p = WebImport.paging(html, "https://example.com/platform/c64/")!!
        assertEquals("https://example.com/platform/c64/page/{n}/", p.template)
        assertEquals(3, p.last)
    }

    @Test fun oneNumberNeedsANextLink() {
        assertNull(WebImport.paging(page("""<a href="/list/page/2/">2</a>""", games), "https://example.com/list/"))
        val p = WebImport.paging(page("""<a href="/list/page/2/">2</a><a href="/list/page/2/">Next</a>""", games), "https://example.com/list/")
        assertEquals(2, p!!.last)
    }
}
