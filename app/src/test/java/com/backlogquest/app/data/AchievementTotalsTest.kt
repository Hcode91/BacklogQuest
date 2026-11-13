package com.backlogquest.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementTotalsTest {
    @Test fun `RetroAchievements page adds up earned achievements, counting hardcore once`() {
        val page = JSONObject(
            """{"Count":3,"Total":3,"Results":[
                {"GameID":1,"MaxPossible":40,"NumAwarded":12,"NumAwardedHardcore":12},
                {"GameID":2,"MaxPossible":30,"NumAwarded":7,"NumAwardedHardcore":0},
                {"GameID":3,"MaxPossible":10,"NumAwarded":0,"NumAwardedHardcore":0}]}""",
        )
        assertEquals(19, AchievementTotals.retroEarned(page))
        assertEquals(0, AchievementTotals.retroEarned(JSONObject("{}")))
    }

    @Test fun `Steam game answer counts only unlocked achievements`() {
        val body = """{"playerstats":{"steamID":"1","gameName":"G","achievements":[
            {"apiname":"A","achieved":1,"unlocktime":5},{"apiname":"B","achieved":0,"unlocktime":0},
            {"apiname":"C","achieved":1,"unlocktime":9}],"success":true}}"""
        val result = AchievementTotals.steamGame(200, body)
        assertEquals(2, (result as AchievementTotals.SteamGame.Earned).count)
    }

    @Test fun `Steam game without achievements counts as zero, not as a failure`() {
        val result = AchievementTotals.steamGame(400, """{"playerstats":{"error":"Requested app has no stats","success":false}}""")
        assertEquals(0, (result as AchievementTotals.SteamGame.Earned).count)
    }

    @Test fun `private Steam profile and hiccups are told apart`() {
        val private = AchievementTotals.steamGame(403, """{"playerstats":{"error":"Profile is not public","success":false}}""")
        assertTrue(private is AchievementTotals.SteamGame.Private)
        assertTrue(AchievementTotals.steamGame(429, "<html>Too Many Requests</html>") is AchievementTotals.SteamGame.Retry)
        assertTrue(AchievementTotals.steamGame(500, "") is AchievementTotals.SteamGame.Retry)
    }

    @Test fun `summary uses account totals where known and ticked games otherwise`() {
        fun game(source: String, earned: Int, total: Int) = Game(
            id = source + earned, title = "T", device = "d", status = "backlog", achSource = source,
            achievements = List(total) { Trophy("t$it", earned = it < earned) },
        )
        val games = listOf(game(Achievements.STEAM, 3, 10), game(Achievements.PSN, 5, 20), game(Achievements.RA, 2, 6))

        val before = AchievementTotals.summary(AppState(games = games))
        assertEquals(10, before.total)
        assertFalse(before.parts.any { it.wholeAccount })

        val after = AchievementTotals.summary(
            AppState(games = games, achTotals = mapOf(Achievements.STEAM to 400, Achievements.RA to 0)),
        )
        // Steam 400 (account) + RetroAchievements 0 (account) + PlayStation 5 (ticked here).
        assertEquals(405, after.total)
        assertEquals(listOf("Steam", "RA", "PSN"), after.parts.map { it.short })
        assertTrue(after.parts.first { it.short == "RA" }.wholeAccount)
    }
}
