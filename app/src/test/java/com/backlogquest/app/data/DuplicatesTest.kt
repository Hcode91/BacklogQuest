package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatesTest {
    private fun wish(id: String, title: String, vararg platforms: String, imported: Boolean = true, source: String = "MobyGames") =
        WishItem(
            id, title, platforms.toList(), 0.0, 0.0, emptyList(), false, "",
            imported = imported, source = if (imported) source else "",
        )

    @Test fun `same title on a shared platform is a duplicate, on another platform it isn't`() {
        val items = listOf(
            wish("a", "Pac-Man", "Atari 2600"),
            wish("b", "PacMan", "atari 2600", source = "Internet Archive"),
            wish("c", "Pac-Man", "NES"),
            wish("d", "Tetris", "Game Boy"),
        )
        val groups = Duplicates.groups(items, emptySet(), emptyMap())
        assertEquals(1, groups.size)
        assertEquals("a", groups[0].keep.id)
        assertEquals(listOf("b"), groups[0].extras.map { it.id })
    }

    @Test fun `the copy kept is your data file's, else one with a picture`() {
        val fromFile = Duplicates.groups(
            listOf(wish("x", "Celeste", "PC"), wish("own", "Celeste", "PC", imported = false)), emptySet(), emptyMap(),
        ).single()
        assertEquals("own", fromFile.keep.id)
        assertEquals(listOf("x"), fromFile.extras.map { it.id })

        val withPicture = Duplicates.groups(
            listOf(wish("p", "Celeste", "PC"), wish("q", "Celeste", "PC")), emptySet(), mapOf("q" to "https://img/q.png"),
        ).single()
        assertEquals("q", withPicture.keep.id)
    }

    @Test fun `data file entries alone are never offered, and hidden games are ignored`() {
        val onlyFile = listOf(wish("a", "Doom", "PC", imported = false), wish("b", "Doom", "PC", imported = false))
        assertTrue(Duplicates.groups(onlyFile, emptySet(), emptyMap()).isEmpty())
        val hidden = listOf(wish("a", "Doom", "PC"), wish("b", "Doom", "PC"))
        assertTrue(Duplicates.groups(hidden, setOf("b"), emptyMap()).isEmpty())
    }

    @Test fun `a game with no platform matches any platform`() {
        assertTrue(Duplicates.platformsOverlap(emptyList(), listOf("SNES")))
        assertTrue(Duplicates.platformsOverlap(listOf("Game Boy Advance"), listOf("game boy advance")))
        assertFalse(Duplicates.platformsOverlap(listOf("NES"), listOf("SNES")))
    }

    @Test fun `imported games already in your games are found by title`() {
        val s = AppState(games = listOf(Game(id = "g", title = "Hades II", device = "d", status = "backlog")))
        val ref = RefData(listOf(Completion("Metroid Fusion", "GBA", "2024-01-01")), emptyList(), "")
        val library = Duplicates.libraryTitles(s, ref)
        val owned = Duplicates.inLibrary(
            listOf(wish("1", "Hades II", "PC"), wish("2", "Metroid: Fusion", "GBA"), wish("3", "Celeste", "PC"), wish("4", "Hades II", "PC", imported = false)),
            emptySet(), library,
        )
        assertEquals(listOf("1", "2"), owned.map { it.id }.sorted())
    }

    @Test fun `games about to be imported are checked against what you have`() {
        val items = listOf(wish("a", "Pac-Man", "Atari 2600"), wish("own", "Doom", "PC", imported = false))
        val index = Duplicates.Index(items, emptySet(), setOf(normTitle("Hades II")))
        assertTrue(index.known("PAC-MAN", "Atari 2600"))
        assertFalse(index.known("Pac-Man", "NES"))
        assertTrue(index.known("Doom", "PC"))
        assertTrue(index.known("Hades II", "Switch"))
        assertFalse(index.known("Celeste", "PC"))
        // Importing the MobyGames Atari 2600 list again replaces it, so its own games don't count.
        assertFalse(index.known("Pac-Man", "Atari 2600", replacing = setOf("mobygames" to "atari 2600")))
    }
}
