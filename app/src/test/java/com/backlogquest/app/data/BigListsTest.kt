package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BigListsTest {
    /** The original version, kept here to prove the faster one gives the same answers. */
    private fun oldNormTitle(t: String): String =
        t.lowercase().replace("&", "and").replace(Regex("[™®©]"), "").replace(Regex("[^a-z0-9]"), "")

    @Test fun `title matching gives the same answers as before`() {
        listOf(
            "Pac-Man", "Tom & Jerry", "Pokémon™ Red", "The Legend of Zelda®: A Link to the Past", "  ", "",
            "ÆON FLUX", "Cassette 25: Bowling · Ninepins", "R-Type Δ", "Ｆｕｌｌ　Ｗｉｄｔｈ", "İstanbul",
        ).forEach { assertEquals(it, oldNormTitle(it), normTitle(it)) }
    }

    @Test fun `lists and pictures survive saving and loading`() {
        val dir = Files.createTempDirectory("bq").toFile()
        val lists = listOf(
            CsvList("Atari 2600", "a.csv", listOf(CsvGame("csv:1", "Pac \"Man\"", "https://x/1", listOf("Action", "Maze")), CsvGame("csv:2", "Ünïcødé", "", emptyList())), source = "MobyGames"),
            CsvList("PC", "web", listOf(CsvGame("web:x/2", "Two|Pipes", "https://x/2", listOf("A"))), source = "itch.io"),
        )
        val images = mapOf("csv:1" to "https://img/1.png", "web:x/2" to WishImport.NO_IMAGE)
        WishImport.save(dir, lists, images)
        val (back, pics) = WishImport.load(dir)
        assertEquals(lists, back)
        assertEquals(images, pics)
    }

    @Test fun `pictures saved inside the lists file by older versions are still read`() {
        val dir = Files.createTempDirectory("bq").toFile()
        File(dir, "wishlist-imports.json").writeText(
            """{"lists":[{"platform":"NES","file":"n.csv","games":[["csv:9","Old Game","https://x/9","Puzzle"]]}],"images":{"csv:9":"https://img/9.png"}}""",
        )
        val (lists, pics) = WishImport.load(dir)
        assertEquals("Old Game", lists.single().games.single().title)
        assertEquals("", lists.single().source)
        assertEquals("https://img/9.png", pics["csv:9"])
        assertTrue(!WishImport.hasImagesFile(dir))
    }

    @Test fun `a damaged file keeps the lists read before the damage`() {
        val dir = Files.createTempDirectory("bq").toFile()
        File(dir, "wishlist-imports.json").writeText(
            """{"lists":[{"platform":"NES","file":"n.csv","source":"S","games":[["csv:9","Fine","",""]]},{"platform":"SNES","games":[["csv:10","Cut off""",
        )
        assertEquals(listOf("NES"), WishImport.load(dir).first.map { it.platform })
    }
}
