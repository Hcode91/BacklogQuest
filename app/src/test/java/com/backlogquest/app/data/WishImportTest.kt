package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WishImportTest {
    private val sample = """
        ID,Title,Release Year,Moby Score,URL,Genres
        115374,Air/Sea Attack,1977,,https://www.mobygames.com/game/115374/airsea-attack/,"Action, Arcade, Side view, Shooter, Naval / watercraft, Fixed / flip-screen, Direct control"
        137461,Cassette 21: Intelligence IV / Attack (Reversi),1981,,https://www.mobygames.com/game/137461/cassette-21-intelligence-iv-attack-reversi/,"Strategy / tactics, Top-down, Board game"
        138466,Desafio al Computador,1982,,https://www.mobygames.com/game/138466/desafio-al-computador/,Puzzle
        999,"Quoted ""Name"", The",1980,,not-a-link,
    """.trimIndent() + "\n"

    @Test fun `platform comes from the file name`() {
        assertEquals(
            "1292 Advanced Programmable Video System",
            WishImport.platformFromFileName("All_1292_Advanced_Programmable_Video_System_Games_April_2025.csv"),
        )
        assertEquals("Game Boy Advance", WishImport.platformFromFileName("All_Game_Boy_Advance_Games_April_2025.csv"))
        assertEquals("Neo Geo", WishImport.platformFromFileName("Neo Geo.csv"))
    }

    @Test fun `title, link and genres are read from each row`() {
        val list = WishImport.parse(sample, "1292 APVS", "file.csv").single()
        assertEquals("1292 APVS", list.platform)
        assertEquals(4, list.games.size)
        val first = list.games.first()
        assertEquals("Air/Sea Attack", first.title)
        assertEquals("https://www.mobygames.com/game/115374/airsea-attack/", first.url)
        assertEquals(
            listOf("Action", "Arcade", "Side view", "Shooter", "Naval / watercraft", "Fixed / flip-screen", "Direct control"),
            first.genres,
        )
        assertEquals(listOf("Puzzle"), list.games[2].genres)
    }

    @Test fun `quotes, commas inside quotes and bad links are handled`() {
        val last = WishImport.parse(sample, "P", "f").single().games.last()
        assertEquals("Quoted \"Name\", The", last.title)
        assertEquals("", last.url)
        assertTrue(last.genres.isEmpty())
        assertEquals(listOf(listOf("a", "b\nc", "d")), WishImport.rows("\uFEFFa,\"b\nc\",d\r\n\r\n"))
    }

    @Test fun `a file without a title column is refused with a reason`() {
        val error = runCatching { WishImport.parse("Foo,Bar\n1,2\n", "P", "f") }.exceptionOrNull()
        assertTrue(error is WishImport.CsvError)
    }

    @Test fun `a Platform column wins over the file name`() {
        val lists = WishImport.parse("Title,Platform\nA,NES\nB,SNES\nC,\n", "From File", "f")
        assertEquals(listOf("NES", "SNES", "From File"), lists.map { it.platform })
    }

    @Test fun `imported games become wishlist entries, and a game on two lists is one entry`() {
        val a = WishImport.parse(sample, "System A", "a.csv")
        val b = WishImport.parse("ID,Title,URL,Genres\n115374,Air/Sea Attack,,\"Action, Naval\"\n", "System B", "b.csv")
        val items = WishImport.wishItems(a + b)
        assertEquals(4, items.size)
        val shared = items.first { it.id == "csv:115374" }
        assertEquals(listOf("System A", "System B"), shared.platforms)
        assertTrue("Naval" in shared.genres && "Arcade" in shared.genres)
        assertTrue(shared.imported)
        assertEquals("https://www.mobygames.com/game/115374/airsea-attack/", shared.url)
        assertTrue(wishTagOk(shared, "imported"))
    }

    @Test fun `importing a platform again replaces its list`() {
        val first = WishImport.parse(sample, "System A", "a.csv").map { it.copy(source = "MobyGames") }
        val again = WishImport.parse("Title\nOnly One\n", "system a", "a2.csv").map { it.copy(source = "mobygames") }
        val merged = WishImport.merge(first, again)
        assertEquals(1, merged.size)
        assertEquals(listOf("Only One"), merged.single().games.map { it.title })
    }

    @Test fun `the same platform from two different sources is kept as two lists`() {
        val moby = WishImport.parse(sample, "Game Boy", "a.csv").map { it.copy(source = "MobyGames") }
        val other = WishImport.parse("Title\nOnly One\n", "Game Boy", "b.csv").map { it.copy(source = "My picks") }
        assertEquals(2, WishImport.merge(moby, other).size)
    }

    @Test fun `sources are suggested from the links, and older lists without one get one`() {
        assertEquals("MobyGames", WishImport.suggestSource("https://www.mobygames.com/game/115374/airsea-attack/"))
        assertEquals("Internet Archive", WishImport.suggestSource("https://archive.org/details/abc"))
        assertEquals("itch.io", WishImport.suggestSource("https://someone.itch.io/game"))
        assertEquals("example.com", WishImport.suggestSource("https://shop.example.com/x"))
        val old = WishImport.parse(sample, "System A", "a.csv").single()
        assertEquals("MobyGames", WishImport.sourceOf(old))
        assertEquals("Named", WishImport.sourceOf(old.copy(source = "Named")))
        val noLinks = WishImport.parse("Title\nA\n", "Neo Geo", "n.csv").single()
        assertEquals("Neo Geo", WishImport.sourceOf(noLinks))
    }

    @Test fun `imported games carry their source, and sources group them biggest first`() {
        val moby = WishImport.parse(sample, "System A", "a.csv").map { it.copy(source = "MobyGames") }
        val mine = WishImport.parse("Title\nSolo\n", "PC", "b.csv").map { it.copy(source = "My picks") }
        val items = WishImport.wishItems(moby + mine)
        assertTrue(items.filter { it.title != "Solo" }.all { it.source == "MobyGames" })
        val grouped = WishImport.sources(items, hidden = setOf("csv:999"))
        assertEquals(listOf("MobyGames" to 3, "My picks" to 1), grouped.map { it.first to it.second.size })
    }

    @Test fun `picture is the page's preview image, else its first cover or screenshot`() {
        val page = "https://www.mobygames.com/game/1/x/"
        assertEquals(
            "https://cdn.mobygames.com/covers/1-x-front.jpg",
            WishImport.imageFrom("""<head><meta property="og:image" content="https://cdn.mobygames.com/covers/1-x-front.jpg"></head>""", page),
        )
        assertEquals(
            "https://cdn.mobygames.com/screenshots/2-x.png",
            WishImport.imageFrom(
                """<head><meta property="og:image" content="https://www.mobygames.com/static/img/logo.png"></head>
                   <body><img src="/static/img/icon.png"><img src="https://cdn.mobygames.com/screenshots/2-x.png"></body>""",
                page,
            ),
        )
        assertNull(WishImport.imageFrom("<html><body><p>No pictures here</p></body></html>", page))
    }
}
