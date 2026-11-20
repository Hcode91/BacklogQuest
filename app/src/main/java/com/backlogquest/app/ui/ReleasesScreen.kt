package com.backlogquest.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.Igdb
import com.backlogquest.app.data.normTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * The Upcoming and Just released tabs: games coming out (or just out) on your platforms, from IGDB.
 * Each game shows its date, your platforms it's on, and whether it's already in your lists.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReleasesScreen(vm: BacklogViewModel, upcoming: Boolean) {
    val s = vm.state
    val ctx = LocalContext.current
    val windows = if (upcoming) listOf(30 to "Next 30 days", 90 to "3 months", 365 to "12 months")
    else listOf(7 to "Last 7 days", 30 to "30 days", 90 to "3 months")
    var days by rememberSaveable(upcoming) { mutableStateOf(if (upcoming) 90 else 30) }
    var watchedOnly by rememberSaveable(upcoming) { mutableStateOf(true) }
    var platform by rememberSaveable(upcoming) { mutableStateOf("") }
    val key = vm.releaseKey(upcoming, days, watchedOnly)
    val hasKeys = s.igdbClientId.isNotBlank() && s.igdbSecret.isNotBlank()
    LaunchedEffect(key, hasKeys) { if (hasKeys) vm.loadReleases(upcoming, days, watchedOnly) }

    val list = vm.releaseLists[key]
    val busy = vm.releasesBusy == key
    val error = vm.releasesError[key]
    // Games you already have or want, by title.
    val known = remember(s.games, vm.ref.wishlist, s.hiddenWish) {
        val owned = s.games.associate { normTitle(it.title) to "IN YOUR GAMES" }
        val wished = vm.ref.wishlist.filter { it.id !in s.hiddenWish }.associate { normTitle(it.title) to "ON WISHLIST" }
        wished + owned
    }
    val platformsFound = remember(list) { list?.items?.flatMap { it.platforms }?.groupingBy { it }?.eachCount()?.entries?.sortedByDescending { it.value }?.map { it.key }.orEmpty() }
    val shown = remember(list, platform) { list?.items?.filter { platform.isBlank() || platform in it.platforms }.orEmpty() }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val byDay = remember(shown) { shown.groupBy { Instant.ofEpochSecond(it.date).atZone(zone).toLocalDate() } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(if (upcoming) "UPCOMING" else "JUST RELEASED", Modifier.weight(1f))
                    if (list != null) Hint("${shown.size} game${if (shown.size == 1) "" else "s"}")
                }
                Hint(
                    if (upcoming) "Games coming out on your platforms (Setup → Platforms), from IGDB."
                    else "Games that came out recently on your platforms, from IGDB.",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    windows.forEach { (d, label) -> TabChip(label, days == d) { days = d } }
                }
                CheckRow(
                    "Only games people are watching",
                    "Hides games nobody follows on IGDB, which is most of the small store releases.",
                    watchedOnly,
                ) { watchedOnly = !watchedOnly }
                if (platformsFound.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TabChip("All", platform.isBlank()) { platform = "" }
                        platformsFound.forEach { p -> TabChip(p, platform == p) { platform = if (platform == p) "" else p } }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (hasKeys) PixelButton(if (busy) "LOADING…" else "REFRESH", enabled = !busy) {
                        vm.loadReleases(upcoming, days, watchedOnly, force = true)
                    }
                    if (busy && list != null) Hint("Updating…")
                }
            }
        }

        if (!hasKeys) item {
            Panel {
                Text("Connect IGDB", color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Hint(
                    "Release dates come from IGDB, which uses a free Twitch developer app. Add its client ID and " +
                        "secret in Setup → Releases; they stay on this phone.",
                )
            }
        }
        if (error != null) item { Panel { Text(error, color = BQ.Ink, fontSize = 14.sp) } }
        if (busy && list == null) item { Hint("Asking IGDB…") }
        if (list != null && shown.isEmpty()) item {
            Hint(if (watchedOnly) "Nothing found. Try a longer time, or untick \"Only games people are watching\"." else "Nothing found for this time.")
        }

        byDay.forEach { (day, games) ->
            item(key = "d:$day") {
                Text(dayLabel(day, today), Modifier.padding(top = 6.dp), color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            items(games, key = { "g:${it.gameId}" }) { r ->
                ReleaseRow(r, known[normTitle(r.title)]) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url))) }
                }
            }
        }
        if (list != null && list.items.size >= 1000) item {
            Hint("Showing the first ${list.items.size}. Pick a shorter time or a platform to see the rest.")
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

private fun dayLabel(day: LocalDate, today: LocalDate): String {
    val d = ChronoUnit.DAYS.between(today, day)
    val name = DAY.format(day).uppercase()
    return when (d) {
        0L -> "TODAY · $name"
        1L -> "TOMORROW · $name"
        -1L -> "YESTERDAY · $name"
        else -> if (d > 0) "IN $d DAYS · $name" else "${-d} DAYS AGO · $name"
    }
}

@Composable
private fun ReleaseRow(r: Igdb.Release, mark: String?, onOpen: () -> Unit) {
    Tile(Modifier.fillMaxWidth(), onClick = onOpen, padding = 8.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(r.title, r.platforms.firstOrNull().orEmpty(), r.cover, Modifier.width(56.dp), small = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.title, color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(r.platforms.joinToString(" · "), color = BQ.B, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val about = listOfNotNull(
                    r.genres.take(2).joinToString(", ").ifBlank { null },
                    if (r.hype > 0) "${r.hype} watching" else null,
                ).joinToString(" · ")
                if (about.isNotBlank()) Text(about, color = BQ.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (mark != null) Text(mark, color = BQ.A, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}
