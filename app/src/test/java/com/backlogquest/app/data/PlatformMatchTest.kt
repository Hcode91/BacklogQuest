package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformMatchTest {
    private val m = Platforms.Matcher(Platforms.defaultNames + "Pico-8")
    private val st = AppState()

    @Test fun shortNamesAndStoreNames() {
        assertEquals("PlayStation 2", m.of("PS2"))
        assertEquals("PlayStation 2", m.of("playstation 2"))
        assertEquals("PlayStation", m.of("PS1"))
        assertEquals("Mega Drive / Genesis", m.of("Sega Genesis"))
        assertEquals("Nintendo Switch", m.of("Switch"))
        assertEquals("Nintendo Switch 2", m.of("Switch 2"))
        assertEquals("Windows PC", m.of("PC"))
        assertEquals("Pico-8", m.of("pico 8"))
        assertNull(m.of("Commodore PET"))
    }

    @Test fun ownNamesBeatAliases() {
        // "Wii U" is its own platform, not Wii; "PlayStation" isn't PlayStation 2.
        assertEquals("Wii U", m.of("Wii U"))
        assertEquals("Wii", m.of("Wii"))
        assertEquals("PlayStation", m.of("PlayStation"))
    }

    @Test fun blankPlatformUsesTheDevice() {
        val onSwitch2 = Game(id = "1", title = "Mario", device = "switch2", status = "playing")
        assertEquals("Nintendo Switch 2", m.of(onSwitch2, st))
        val ps2OnPhone = onSwitch2.copy(device = "phone", platform = "PS2")
        assertEquals("PlayStation 2", m.of(ps2OnPhone, st))
        assertEquals("Android", m.of(onSwitch2.copy(device = "phone"), st))
    }

    @Test fun onlyYourPlatforms() {
        val few = Platforms.Matcher(listOf("PlayStation 2"))
        assertNull(few.of("Switch"))
        assertTrue(few.matches("PlayStation 2", "ps2"))
    }

    @Test fun wishlistAnyPlatform() {
        val w = WishItem("w1", "Okami", listOf("Wii", "PS2"), 30.0, 0.0, emptyList(), false, "")
        assertTrue(m.has(w, "PlayStation 2"))
        assertTrue(m.has(w, "Wii"))
        assertEquals("Wii", m.of(w))
    }
}
