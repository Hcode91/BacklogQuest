package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PlatformsTest {
    @Test fun namesAreUnique() {
        val names = Platforms.defaultNames.map { it.lowercase() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun includesTheAskedForOnes() {
        listOf("Legacy Android", "Windows Phone", "iOS", "Nokia N-Gage", "PlayStation 2", "Emerson Arcadia 2001", "DOS PC", "Apple II")
            .forEach { assertTrue(it, it in Platforms.defaultNames) }
    }

    @Test fun everyRecognisedEmulatorHasAPlatform() {
        // Each emulator in the systems list plays at least one platform in the list (by its name in "playedWith").
        val played = Platforms.defaults.flatMap { it.playedWith }.map { EmuSystems.norm(it) }.toSet()
        val skip = setOf("qemu", "winlator") // a general PC emulator, and a PC layer not on your phone
        EmuSystems.all.filter { it.key !in skip }.forEach { info ->
            assertTrue(info.key, info.names.any { EmuSystems.norm(it) in played })
        }
    }

    @Test fun newBuiltInsJoinAnOldList() {
        val dir = Files.createTempDirectory("bq").toFile()
        // Saved by the first version: no record of the built-in list, and Amstrad CPC removed.
        val old = PLATFORMS_V1.filter { it != "Amstrad CPC" } + "Pico-8"
        File(dir, "state.json").writeText(org.json.JSONObject().put("platforms", org.json.JSONArray(old)).toString())
        val loaded = Store.loadState(dir)!!.platforms
        assertTrue("PocketPlug machines added", "Mephisto Academy" in loaded && "Zeebo" in loaded)
        assertTrue("removed stays removed", "Amstrad CPC" !in loaded)
        assertTrue("own kept", "Pico-8" in loaded)
        // Saved again, nothing new is added next time.
        Store.saveState(dir, AppState(platforms = loaded.filter { it != "Zeebo" }))
        assertTrue("Zeebo" !in Store.loadState(dir)!!.platforms)
    }

    @Test fun familiesAndOwn() {
        assertEquals("Phones", Platforms.familyOf("ios"))
        assertEquals("Your own", Platforms.familyOf("Pico-8"))
    }

    @Test fun oldSaveGetsTheList() {
        val dir = Files.createTempDirectory("bq").toFile()
        File(dir, "state.json").writeText("""{"baseline":0,"games":[]}""")
        assertEquals(Platforms.defaultNames, Store.loadState(dir)!!.platforms)
        Store.saveState(dir, AppState(platforms = listOf("PlayStation 2", "Pico-8")))
        assertEquals(listOf("PlayStation 2", "Pico-8"), Store.loadState(dir)!!.platforms)
    }
}
