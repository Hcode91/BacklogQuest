package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmuSystemsTest {
    @Test fun findsByPackage() {
        assertEquals("retroarch", EmuSystems.find("", "com.retroarch.aarch64")?.key)
        assertEquals("eden", EmuSystems.find("Whatever", "dev.eden.eden_emulator")?.key)
        assertEquals("dolphin", EmuSystems.find("", "org.dolphinemu.dolphinemu")?.key)
        assertEquals("xendroid", EmuSystems.find("", "xendroid.compose")?.key)
    }

    @Test fun packagePrefixNeedsWholeSegment() {
        assertNull(EmuSystems.find("", "org.ppssppfake"))
    }

    @Test fun findsByNameIgnoringCaseSpacesAndSymbols() {
        assertEquals("bachatas4", EmuSystems.find("Bachata S4+")?.key)
        assertEquals("bachatas4", EmuSystems.find("BachataS4")?.key)
        assertEquals("86box", EmuSystems.find("86BoxDroid")?.key)
        assertEquals("m64", EmuSystems.find("M64Plus FZ Pro")?.key)
        assertEquals("dolphin", EmuSystems.find("Dolphin Emulator")?.key)
        assertEquals("rpcsx", EmuSystems.find("RPCSX")?.key)
        assertEquals("droiddeck", EmuSystems.find("DroidDeck")?.key)
    }

    @Test fun ignoresOtherApps() {
        assertNull(EmuSystems.find("Dolphin Browser"))
        assertNull(EmuSystems.find("Obtainium", "dev.imranr.obtainium"))
        assertNull(EmuSystems.find("Shizuku", "moe.shizuku.privileged.api"))
    }

    @Test fun typedSystemsWin() {
        val e = EmuEntry("1", "Flycast", pkg = "com.flycast.emulator")
        assertTrue(EmuSystems.summaryFor(e).startsWith("Sega Dreamcast"))
        assertEquals("Just Dreamcast", EmuSystems.summaryFor(e.copy(systems = "Just Dreamcast")))
        assertEquals("", EmuSystems.summaryFor(EmuEntry("2", "Mystery app")))
    }

    @Test fun everyEntryIsComplete() {
        val keys = EmuSystems.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        EmuSystems.all.forEach { assertTrue(it.key, it.summary.isNotBlank() && it.names.isNotEmpty()) }
        // Every emulator the app can launch games in has its systems listed.
        Emulators.all.forEach { e ->
            val pkg = e.components.first().substringBefore("/")
            assertTrue(e.key, EmuSystems.find(e.label, pkg) != null)
        }
    }

    @Test fun noNameBelongsToTwoEmulators() {
        val names = EmuSystems.all.flatMap { i -> i.names.map { EmuSystems.norm(it) }.distinct() }
        assertEquals(names.size, names.toSet().size)
    }
}
