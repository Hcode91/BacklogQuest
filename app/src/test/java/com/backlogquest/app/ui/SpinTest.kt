package com.backlogquest.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpinTest {
    /** Which slice sits under the pointer for a given wheel rotation. */
    private fun sliceAtTop(rotation: Float, count: Int): Int {
        val step = 360f / count
        // Slice i is centred at rotation + i * step from the top.
        return (0 until count).minBy { i ->
            val a = (((rotation + i * step) % 360f) + 360f) % 360f
            minOf(a, 360f - a)
        }
    }

    @Test fun `wheel stops with the chosen slice under the pointer`() {
        for (count in 1..17) for (index in 0 until count) for (start in listOf(0f, 37.5f, 200f, 359f)) {
            val target = wheelTarget(start, index, count)
            assertEquals("count=$count index=$index start=$start", index, sliceAtTop(target, count))
        }
    }

    @Test fun `wheel always makes at least three full turns forwards`() {
        for (start in listOf(0f, 90f, 271f)) for (index in 0 until 9) {
            val turned = wheelTarget(start, index, 9) - start
            assertTrue("turned $turned", turned >= 1080f && turned < 1440f + 0.01f)
        }
    }

    @Test fun `sources with several platforms share one place on the wheel`() {
        assertEquals("GameBrew", sourceName("GameBrew · Nintendo 3DS"))
        assertEquals("Homebrew Hub", sourceName("Homebrew Hub · GBA repository"))
        assertEquals("Universal-DB", sourceName("Universal-DB"))
    }
}
