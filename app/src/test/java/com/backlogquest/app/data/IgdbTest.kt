package com.backlogquest.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IgdbTest {
    private val igdb = listOf(
        Igdb.IgdbPlatform(6, "PC (Microsoft Windows)", "PC", ""),
        Igdb.IgdbPlatform(34, "Android", "Android", ""),
        Igdb.IgdbPlatform(39, "iOS", "iOS", ""),
        Igdb.IgdbPlatform(508, "Nintendo Switch 2", "NS2", ""),
        Igdb.IgdbPlatform(130, "Nintendo Switch", "Switch", ""),
        Igdb.IgdbPlatform(167, "PlayStation 5", "PS5", ""),
        Igdb.IgdbPlatform(29, "Sega Mega Drive/Genesis", "Genesis/MegaDrive", ""),
        Igdb.IgdbPlatform(19, "Super Nintendo Entertainment System", "SNES", "Super Famicom"),
        Igdb.IgdbPlatform(15, "Commodore C64/128/MAX", "C64", ""),
    )

    @Test fun platformsMatchYourNames() {
        val map = Igdb.mapPlatforms(igdb, Platforms.defaultNames)
        assertEquals("Windows PC", map[6])
        assertEquals("Android", map[34])
        assertEquals("iOS", map[39])
        assertEquals("Nintendo Switch 2", map[508])
        assertEquals("Nintendo Switch", map[130])
        assertEquals("Mega Drive / Genesis", map[29])
        assertEquals("SNES", map[19])
        assertEquals("Commodore 64", map[15])
        // Not one of yours.
        assertTrue(167L !in map)
    }

    @Test fun onlyYourPlatforms() {
        val map = Igdb.mapPlatforms(igdb, listOf("Android", "iOS"))
        assertEquals(setOf(34L, 39L), map.keys)
    }

    private val rows = JSONArray(
        """
        [
          {"id":1,"date":1764288000,"human":"Nov 28, 2026","platform":6,
           "game":{"id":100,"name":"Star Game","slug":"star-game","cover":{"id":5,"image_id":"co1abc"},
                   "genres":[{"id":1,"name":"Adventure"},{"id":2,"name":"Puzzle"}],"hypes":42,"game_type":0}},
          {"id":2,"date":1764374400,"human":"Nov 29, 2026","platform":34,
           "game":{"id":100,"name":"Star Game","slug":"star-game","cover":{"id":5,"image_id":"co1abc"},"hypes":42,"game_type":0}},
          {"id":3,"date":1764201600,"human":"Nov 27, 2026","platform":39,
           "game":{"id":200,"name":"Pocket Hero","slug":"pocket-hero","hypes":3,"game_type":{"id":0}}},
          {"id":4,"date":1764201600,"human":"Nov 27, 2026","platform":6,
           "game":{"id":300,"name":"Star Game: Extra Levels","slug":"sg-dlc","game_type":1}},
          {"id":5,"date":1764201600,"human":"Nov 27, 2026","platform":167,
           "game":{"id":400,"name":"Not Yours","slug":"nope","game_type":0}}
        ]
        """,
    )
    private val mine = mapOf(6L to "Windows PC", 34L to "Android", 39L to "iOS")

    @Test fun oneEntryPerGameWithAllItsPlatforms() {
        val list = Igdb.parse(rows, mine, newestFirst = false)
        assertEquals(listOf("Pocket Hero", "Star Game"), list.map { it.title })
        val star = list[1]
        assertEquals(listOf("Windows PC", "Android"), star.platforms)
        assertEquals(1764288000L, star.date)
        assertEquals("Nov 28, 2026", star.human)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/co1abc.jpg", star.cover)
        assertEquals(listOf("Adventure", "Puzzle"), star.genres)
        assertEquals(42, star.hype)
        assertEquals("https://www.igdb.com/games/star-game", star.url)
        assertEquals("", list[0].cover)
    }

    @Test fun dlcAndOtherPlatformsLeftOut() {
        val titles = Igdb.parse(rows, mine, newestFirst = true).map { it.title }
        assertTrue("Star Game: Extra Levels" !in titles)
        assertTrue("Not Yours" !in titles)
        assertEquals(listOf("Star Game", "Pocket Hero"), titles)
    }
}
