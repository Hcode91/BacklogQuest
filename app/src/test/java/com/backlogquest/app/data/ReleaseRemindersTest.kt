package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

class ReleaseRemindersTest {
    // 2026-11-28 00:00 UTC, as IGDB gives dates.
    private val nov28 = LocalDate.of(2026, 11, 28).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()

    @Test fun remindsTheDayBeforeAndOnTheDay() {
        assertEquals("TOMORROW", ReleaseReminders.due(nov28, LocalDate.of(2026, 11, 27)))
        assertEquals("TODAY", ReleaseReminders.due(nov28, LocalDate.of(2026, 11, 28)))
        assertNull(ReleaseReminders.due(nov28, LocalDate.of(2026, 11, 25)))
        assertNull(ReleaseReminders.due(nov28, LocalDate.of(2026, 11, 29)))
    }

    @Test fun hiddenGamesAndRepeatsLeftOut() {
        val a = ReleaseWatch(1, "Star Game", "Windows PC", nov28)
        val s = AppState(
            releaseWatch = listOf(a, a, ReleaseWatch(2, "Gone", "Android", nov28)),
            hiddenWish = setOf("igdb:2"),
        )
        assertEquals(listOf(a), ReleaseReminders.watched(s))
    }

    @Test fun savedAndLoaded() {
        val dir = Files.createTempDirectory("bq").toFile()
        val w = listOf(ReleaseWatch(7, "Pocket Hero", "iOS", nov28))
        Store.saveState(dir, AppState(releaseReminders = false, releaseWatch = w))
        val back = Store.loadState(dir)!!
        assertEquals(w, back.releaseWatch)
        assertEquals(false, back.releaseReminders)
        // Older saves: reminders on, nothing watched.
        File(dir, "state.json").writeText("""{"games":[]}""")
        assertEquals(true, Store.loadState(dir)!!.releaseReminders)
    }

    @Test fun bookStartsEmptyAndReadsBack() {
        val dir = Files.createTempDirectory("bq").toFile()
        assertEquals(0, ReleaseReminders.loadBook(dir).announced.size)
        File(dir, "release-reminders.json").writeText("""{"announced":{"1|iOS":$nov28},"dates":{"1|iOS":${nov28 + 86400}}}""")
        val b = ReleaseReminders.loadBook(dir)
        assertEquals(nov28, b.announced["1|iOS"])
        assertEquals(nov28 + 86400, b.dates["1|iOS"])
    }
}
