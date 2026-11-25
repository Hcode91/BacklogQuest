package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExophaseTest {
    @Test fun `username comes from a name or a profile link`() {
        assertEquals("Hcode91", Exophase.userName("Hcode91"))
        assertEquals("Hcode91", Exophase.userName("  https://www.exophase.com/user/Hcode91/  "))
        assertEquals("some.name", Exophase.userName("exophase.com/user/some.name"))
        assertNull(Exophase.userName("not a name"))
        assertNull(Exophase.userName(""))
    }

    @Test fun `Android and Google Play games are looked up on Exophase`() {
        fun game(platform: String) = Game(id = "g", title = "T", device = "d", status = "backlog", platform = platform)
        assertEquals(Achievements.EXO, Achievements.pickSource(game("Android"), "Phone"))
        assertEquals(Achievements.EXO, Achievements.pickSource(game(""), "Google Play"))
        assertEquals(Achievements.PSN, Achievements.pickSource(game("PS4"), "Phone"))
    }
}
