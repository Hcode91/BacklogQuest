package com.backlogquest.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class BulkFetchTest {
    @Test fun `the count picks the first games, and 0 means all`() {
        val missing = (1..37).toList()
        assertEquals((1..10).toList(), BulkFetch.first(missing, 10))
        assertEquals(missing, BulkFetch.first(missing, 0))
        assertEquals(missing, BulkFetch.first(missing, 100))
    }

    @Test fun `button labels say how many of how many`() {
        assertEquals("10 OF 37", BulkFetch.label(10, 37))
        assertEquals("37", BulkFetch.label(0, 37))
        assertEquals("5", BulkFetch.label(10, 5))
        assertEquals("1,000 OF 80,000", BulkFetch.label(1000, 80000))
    }

    @Test fun `keys for things not found are stable`() {
        assertEquals("price:pacman", BulkFetch.priceKey("Pac-Man"))
        assertEquals("ach:g1", BulkFetch.achKey("g1"))
        assertEquals("pic:g:g1", BulkFetch.Pic.Own("g1", "T").key)
        assertEquals("pic:h:3:tetris", BulkFetch.Pic.Hist("3:tetris", "Tetris").key)
    }

    @Test fun `a lookup that never answers is given up on, and the batch can move on`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val started = System.currentTimeMillis()
        // Stands in for a site that hangs: a blocking call that ignores cancellation.
        val result = BulkFetch.withinLimit(scope, 300) { Thread.sleep(5_000); "late" }
        val took = System.currentTimeMillis() - started
        assertNull(result)
        assertTrue("took $took ms", took < 2_000)
        assertEquals("quick", BulkFetch.withinLimit(scope, 2_000) { "quick" })
        scope.cancel()
    }

    @Test fun `a lookup that fails still reports its error`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val error = runCatching { BulkFetch.withinLimit(scope, 2_000) { throw IllegalStateException("site said no") } }.exceptionOrNull()
        assertEquals("site said no", error?.message)
        scope.cancel()
    }

    @Test fun `history pictures and not-found notes are saved with your data`() {
        val dir = Files.createTempDirectory("bq").toFile()
        val s = AppState(histCovers = mapOf("3:tetris" to "https://img/t.png"), notFound = setOf("pic:g:g1", "price:pacman"))
        Store.saveState(dir, s)
        val back = Store.loadState(dir)!!
        assertEquals(s.histCovers, back.histCovers)
        assertEquals(s.notFound, back.notFound)
    }
}
