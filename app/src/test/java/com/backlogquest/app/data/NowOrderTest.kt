package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NowOrderTest {
    private fun game(id: String, status: String, order: Long = 0, since: Long = 0) =
        Game(id = id, title = id, device = "d", status = status, order = order, playingSince = since)

    @Test fun `setting a game to Playing notes the time`() {
        val before = AppState(games = listOf(game("a", "backlog", order = 1), game("b", "playing", since = 50)))
        val after = before.copy(games = before.games.map { if (it.id == "a") it.copy(status = "playing") else it })
        val stamped = stampPlaying(before, after, now = 100)
        assertEquals(100L, stamped.games.first { it.id == "a" }.playingSince)
        assertEquals(50L, stamped.games.first { it.id == "b" }.playingSince)
    }

    @Test fun `a new game added as Playing gets the time too`() {
        val before = AppState(games = emptyList())
        val after = before.copy(games = listOf(game("c", "playing")))
        assertEquals(7L, stampPlaying(before, after, now = 7).games.single().playingSince)
    }

    @Test fun `in the order they were started, newest last`() {
        val games = listOf(game("old", "playing", order = 10), game("new", "playing", since = 300), game("mid", "playing", since = 200))
        assertEquals(listOf("old", "mid", "new"), nowOrder(games).map { it.id })
    }
}
