package com.backlogquest.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backlogquest.app.data.Port
import com.backlogquest.app.data.Ports
import com.backlogquest.app.data.PortsStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The Ports tab: every decompilation, recompilation, source port and engine remake from
 * the Awesome Decomp Games list, in one place. Watch a project to be told when it has a
 * new release; projects that joined the list in the last week show as NEW.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { PortsStore.open(ctx.applicationContext) }

    val all = PortsStore.ports
    val watched = PortsStore.watched
    val firstSeen = PortsStore.firstSeen
    var view by rememberSaveable { mutableStateOf("all") }
    var type by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("score") }
    var search by rememberSaveable { mutableStateOf("") }
    var hideInactive by rememberSaveable { mutableStateOf(true) }
    var hideAi by rememberSaveable { mutableStateOf(false) }
    var limit by remember { mutableIntStateOf(PAGE) }

    val weekAgo = LocalDate.now().toEpochDay() - 7
    val isNew = { p: Port -> (firstSeen[p.id] ?: 0L) > weekAgo }
    val updates = remember(all, watched) { Ports.updated(all, watched) }

    // Everything but the view, type and platform choices, so their counts stay honest.
    val base = remember(all, search, hideInactive, hideAi) {
        val q = search.trim().lowercase()
        all.filter { p ->
            (!hideInactive || !p.inactive) && (!hideAi || !p.ai) &&
                (q.isEmpty() || q in p.title.lowercase() || q in p.repo.lowercase() || q in p.platform.lowercase())
        }
    }
    val views = listOf(
        "all" to "All",
        "watching" to "Watching",
        "new" to "New this week",
        "android" to "Android",
        "browser" to "Play in browser",
    )
    fun inView(p: Port, v: String) = when (v) {
        "watching" -> p.id in watched
        "new" -> isNew(p)
        "android" -> p.android
        "browser" -> p.browser
        else -> true
    }
    val viewCounts = remember(base, watched, firstSeen) { views.associate { (k, _) -> k to base.count { inView(it, k) } } }
    val inThisView = remember(base, view, watched, firstSeen) { base.filter { inView(it, view) } }
    val typeCounts = remember(inThisView) { inThisView.groupingBy { it.type }.eachCount() }
    val platforms = remember(inThisView, type) {
        inThisView.filter { type.isBlank() || it.type == type }
            .groupingBy { it.platform }.eachCount().entries.sortedByDescending { it.value }
    }
    val shown = remember(inThisView, type, platform, sort, updates) {
        val list = inThisView.filter { (type.isBlank() || it.type == type) && (platform.isBlank() || it.platform == platform) }
        val sorted = when (sort) {
            "stars" -> list.sortedByDescending { it.stars }
            "updated" -> list.sortedByDescending { it.lastCommit }
            "release" -> list.sortedByDescending { it.releaseDate }
            "az" -> list.sortedBy { it.title.lowercase() }
            else -> list.sortedByDescending { it.score }
        }
        // Watched projects with a new release always come first.
        sorted.sortedByDescending { it.id in updates }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("PORTS", Modifier.weight(1f))
                    if (all.isNotEmpty()) Hint("${shown.size} of ${all.size}")
                }
                Hint(
                    "Decomps, recomps, source ports and engine remakes, from the Awesome Decomp Games list" +
                        (if (PortsStore.listDate.isNotBlank()) " (updated ${PortsStore.listDate})." else ".") +
                        " Tap a project to open it. Watch one to be told here when it has a new release.",
                )
                if (updates.isNotEmpty()) {
                    Text(
                        "★ ${updates.size} watched project${if (updates.size == 1) " has" else "s have"} a new release",
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable { view = "watching" }.padding(vertical = 4.dp),
                        color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it; limit = PAGE },
                    placeholder = { Text("Search games, repos or platforms") },
                    singleLine = true,
                    colors = bqFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                ChipRow(views.map { (k, l) -> k to "$l ${viewCounts[k] ?: 0}" }, view) { view = it; limit = PAGE }
                ChipRow(
                    listOf("" to "All types") + Ports.TYPES.filterKeys { (typeCounts[it] ?: 0) > 0 }
                        .map { (k, l) -> k to "$l ${typeCounts[k]}" },
                    type,
                ) { type = it; platform = ""; limit = PAGE }
                if (platforms.size > 1) {
                    ChipRow(listOf("" to "All platforms") + platforms.map { it.key to "${it.key} ${it.value}" }, platform) {
                        platform = it; limit = PAGE
                    }
                }
                ChipRow(
                    listOf("score" to "Best", "stars" to "Most stars", "updated" to "Recently updated", "release" to "Newest release", "az" to "A–Z"),
                    sort,
                ) { sort = it }
                CheckRow("Hide inactive projects", "No commits for two years.", hideInactive) { hideInactive = !hideInactive; limit = PAGE }
                CheckRow("Hide AI-made projects", "Ones the list's sources flag as mostly made with AI.", hideAi) { hideAi = !hideAi; limit = PAGE }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton(if (PortsStore.busy) "LOADING…" else "REFRESH", enabled = !PortsStore.busy) {
                        scope.launch { PortsStore.refresh(ctx.applicationContext) }
                    }
                    if (PortsStore.busy && all.isNotEmpty()) Hint("Updating…")
                }
            }
        }

        PortsStore.error?.let { e -> item { Panel { Text(e, color = BQ.Ink, fontSize = 14.sp) } } }
        if (all.isEmpty() && PortsStore.busy) item { Hint("Downloading the list (about 5 MB, once a day)…") }
        if (all.isNotEmpty() && shown.isEmpty()) item {
            Hint(if (view == "watching") "You're not watching anything here yet. Tap ☆ WATCH on a project." else "Nothing matches.")
        }

        items(shown.take(limit), key = { it.id }) { p ->
            PortRow(
                p,
                watching = p.id in watched,
                hasUpdate = p.id in updates,
                isNew = isNew(p),
                onOpen = {
                    PortsStore.markSeen(ctx.applicationContext, p)
                    open(ctx, p.url)
                },
                onRelease = {
                    PortsStore.markSeen(ctx.applicationContext, p)
                    open(ctx, p.releaseUrl)
                },
                onPlay = { open(ctx, p.playUrl) },
                onWatch = { PortsStore.toggleWatch(ctx.applicationContext, p) },
            )
        }
        if (shown.size > limit) item {
            PixelButton("SHOW MORE (${shown.size - limit} LEFT)", Modifier.fillMaxWidth()) { limit += PAGE }
        }
        if (all.isNotEmpty()) item {
            Text(
                "List: Awesome Decomp Games (CC0) · open the website",
                Modifier.clickable { open(ctx, Ports.SITE) }.padding(vertical = 8.dp),
                color = BQ.Muted, fontSize = 12.sp,
            )
        }
    }
}

private const val PAGE = 150

private fun open(ctx: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** "today", "3 days ago", "2 months ago" for a "2026-11-23" date. */
private fun ago(date: String): String {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return ""
    val days = ChronoUnit.DAYS.between(d, LocalDate.now())
    return when {
        days <= 0 -> "today"
        days == 1L -> "yesterday"
        days < 31 -> "$days days ago"
        days < 365 -> "${days / 30} month${if (days / 30 == 1L) "" else "s"} ago"
        else -> "${days / 365} year${if (days / 365 == 1L) "" else "s"} ago"
    }
}

private fun stars(n: Int): String = when {
    n >= 10_000 -> "${n / 1000}k"
    n >= 1000 -> "%.1fk".format(n / 1000.0)
    else -> "$n"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortRow(
    p: Port,
    watching: Boolean,
    hasUpdate: Boolean,
    isNew: Boolean,
    onOpen: () -> Unit,
    onRelease: () -> Unit,
    onPlay: () -> Unit,
    onWatch: () -> Unit,
) {
    Tile(Modifier.fillMaxWidth(), onClick = onOpen, padding = 10.dp) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.title, color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${p.platform} · ${Ports.typeLabel(p.type)}", color = BQ.B, fontSize = 12.sp, maxLines = 1)
            }
            Text(
                if (watching) "★ WATCHING" else "☆ WATCH",
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onWatch).padding(horizontal = 6.dp, vertical = 6.dp),
                color = if (watching) BQ.B else BQ.Muted, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            )
        }
        val about = p.note.ifBlank { p.description }
        if (about.isNotBlank()) Text(about, color = BQ.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (hasUpdate) StatusLabel("NEW RELEASE", BQ.B, BQ.Shell)
            if (isNew) StatusLabel("NEW", BQ.OkBg, BQ.Ok)
            if (p.android) StatusLabel("ANDROID", BQ.OkBg, BQ.Ok)
            if (p.browser) StatusLabel("BROWSER")
            if (p.ai) StatusLabel("AI", BQ.CoolBg, BQ.CoolFg)
            if (p.inactive || p.archived) StatusLabel(if (p.archived) "ARCHIVED" else "INACTIVE")
        }
        val meta = listOfNotNull(
            "★ ${stars(p.stars)}",
            p.lastCommit.takeIf { it.isNotBlank() }?.let { "updated ${ago(it)}" },
            p.release.takeIf { it.isNotBlank() }?.let { r -> r + (p.releaseDate.takeIf { it.isNotBlank() }?.let { " (${ago(it)})" } ?: "") },
        ).joinToString(" · ")
        Text(meta, color = BQ.Muted, fontFamily = BQ.Mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (p.releaseUrl.isNotBlank() || p.playUrl.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (p.releaseUrl.isNotBlank()) ActionText("DOWNLOADS ›", onRelease)
                if (p.playUrl.isNotBlank()) ActionText("PLAY IN BROWSER ›", onPlay)
            }
        }
    }
}

@Composable
private fun ActionText(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
    )
}
