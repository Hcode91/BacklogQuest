package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchAllTest {
    @Test fun gameNativeIds() {
        assertEquals("STEAM" to 1145360, Emulators.gameNativeId("1145360"))
        assertEquals("GOG" to 1207658924, Emulators.gameNativeId("GOG:1207658924"))
        assertEquals("EPIC" to -1, Emulators.gameNativeId("epic:abc"))
    }

    @Test fun everyEmulatorOnThePhoneHasALaunchEntryOrIsKnown() {
        // Your Pocket emulators and the forks all have launch entries.
        listOf("pocketplug", "pocketchroma", "pocketpenguin", "pocketsprout", "pocketreel", "pocketcarioca",
            "pocketouya", "pocketcentury", "pocketpager", "pocketr5900", "pockettrophy", "pocketpear", "gamenative", "rpcsx")
            .forEach { assertTrue(it, Emulators.byKey(it) != null) }
    }

    @Test fun keysAreUnique() {
        val keys = Emulators.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
