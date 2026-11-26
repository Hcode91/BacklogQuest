package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortsTests {
    private val sample = """
        {"generated_at":"2026-11-25T13:24:04Z","games":[
          {"id":"harbourmasters-shipwright","title":"Ocarina of Time (Ship of Harkinian)","platform":"N64",
           "type":"decompilation","url":"https://github.com/HarbourMasters/Shipwright","repo":"HarbourMasters/Shipwright",
           "description":"Ship of Harkinian brings Ocarina of Time to PC.","stars":6167,"last_commit":"2026-11-23T10:52:48Z",
           "archived":false,"release":"9.3.1","release_date":"2026-10-18T21:06:27Z",
           "release_url":"https://github.com/HarbourMasters/Shipwright/releases/tag/9.3.1","play_url":null,
           "ai_flag":false,"score":4.2,"note":"Runs on PC, Switch, Wii U and Android.","browser_playable":false,"inactive":false},
          {"id":"x-sky","title":"Skate 3 (skate3-android)","platform":"switch2","type":"static-recomp","url":"u","repo":"a/skate3-android",
           "description":null,"stars":57,"last_commit":null,"release":null,"score":null,"ai_flag":true,"note":null},
          {"id":"","repo":"","title":"broken"}
        ]}
    """.trimIndent()

    @Test fun `reads the list`() {
        val (date, list) = Ports.parse(sample)
        assertEquals("2026-11-25", date)
        assertEquals(2, list.size)
        val oot = list[0]
        assertEquals("N64", oot.platform)
        assertEquals("2026-11-23", oot.lastCommit)
        assertEquals("9.3.1", oot.release)
        assertEquals("", oot.playUrl)
        assertTrue(oot.android)
        val skate = list[1]
        assertEquals("Switch 2", skate.platform)
        assertEquals("", skate.release)
        assertEquals("", skate.description)
        assertEquals(0.0, skate.score, 0.0)
        assertTrue(skate.ai)
        assertTrue(skate.android)
    }

    @Test fun `first list marks nothing new, later ones mark additions`() {
        val first = Ports.markFirstSeen(emptyMap(), listOf("a", "b"), 100)
        assertEquals(mapOf("a" to 0L, "b" to 0L), first)
        val next = Ports.markFirstSeen(first, listOf("a", "b", "c"), 105)
        assertEquals(105L, next["c"])
        assertEquals(0L, next["a"])
    }

    @Test fun `watched project with a newer release is flagged`() {
        val (_, list) = Ports.parse(sample)
        assertEquals(setOf("harbourmasters-shipwright"), Ports.updated(list, mapOf("harbourmasters-shipwright" to "9.3.0")))
        assertTrue(Ports.updated(list, mapOf("harbourmasters-shipwright" to "9.3.1")).isEmpty())
        assertFalse("x-sky" in Ports.updated(list, mapOf("x-sky" to "")))
    }
}
