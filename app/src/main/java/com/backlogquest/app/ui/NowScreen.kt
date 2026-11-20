package com.backlogquest.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.backlogquest.app.data.Platforms
import com.backlogquest.app.data.newId
import com.backlogquest.app.data.today
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.Game
import com.backlogquest.app.data.deviceName
import com.backlogquest.app.data.fmtNum
import com.backlogquest.app.data.playedOn

/** The first tab: only the games you're playing right now, and how long you've put into each. */
@Composable
fun NowScreen(
    vm: BacklogViewModel,
    onEdit: (String) -> Unit,
    onBeaten: (String) -> Unit,
    onSpin: () -> Unit,
) {
    val s = vm.state
    val playing = s.games.filter { it.status == "playing" }
    // Every platform, and what you're playing on each.
    val matcher = remember(s.platforms) { Platforms.Matcher(s.platforms) }
    val onPlatform = remember(playing, matcher, s.devices) {
        playing.mapNotNull { g -> matcher.of(g, s)?.let { it to g } }.groupBy({ it.first }, { it.second })
    }
    var show by rememberSaveable { mutableStateOf("all") }
    var search by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusPanel(vm) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("NOW PLAYING", Modifier.weight(1f))
                if (playing.isNotEmpty()) Hint("${playing.size} game${if (playing.size == 1) "" else "s"}")
            }
        }
        if (playing.isEmpty() && vm.loaded) {
            item {
                Tile(Modifier.fillMaxWidth()) {
                    Text("Nothing on the go", color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Hint("Spin for your next game, or set one to Playing in Games and it will show up here.")
                    PixelButton("SPIN FOR A GAME", kind = BtnKind.A, onClick = onSpin)
                }
            }
        }
        items(playing, key = { it.id }) { g ->
            NowTile(
                g,
                device = playedOn(s, g).ifBlank { deviceName(s, g.device) },
                label = g.genre.ifBlank { deviceName(s, g.device) },
                onOpen = { onEdit(g.id) },
                onResume = { if (g.launch != null) vm.play(g.id) else onEdit(g.id) },
                onBeaten = { onBeaten(g.id) },
            )
        }
        if (playing.isNotEmpty()) {
            item {
                val shape = RoundedCornerShape(10.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .border(1.dp, BQ.Edge, shape)
                        .clickable(onClick = onSpin)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ready for something else?", Modifier.weight(1f), color = BQ.Ink, fontSize = 14.sp)
                    Text("SPIN ›", color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // ---------- Every platform ----------
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("EVERY PLATFORM", Modifier.weight(1f))
                    Hint("${onPlatform.size} of ${s.platforms.size} on the go")
                }
                Hint("One entry for each of your platforms (Setup → Platforms). Add a game from your backlog or wishlist to any that's empty.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "All", "empty" to "Empty", "playing" to "Playing").forEach { (k, label) ->
                        TabChip(label, show == k) { show = k }
                    }
                }
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    label = { Text("Find a platform") }, singleLine = true,
                    colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val shown = s.platforms.filter { p ->
            (search.isBlank() || p.contains(search.trim(), ignoreCase = true)) && when (show) {
                "empty" -> p !in onPlatform
                "playing" -> p in onPlatform
                else -> true
            }
        }
        val byFamily = shown.groupBy { Platforms.familyOf(it) }
        Platforms.families.forEach { family ->
            val list = byFamily[family] ?: return@forEach
            item(key = "fam:$family") { Hint(family.uppercase(), Modifier.padding(top = 4.dp)) }
            items(list, key = { "plat:$it" }) { p ->
                PlatformRow(
                    p, onPlatform[p].orEmpty(),
                    onOpen = { g -> onEdit(g.id) },
                    onAdd = { picking = p },
                )
            }
        }
        if (shown.isEmpty()) item { Hint("No platforms match.") }
    }
    picking?.let { p -> PlatformPickSheet(vm, p, matcher) { picking = null } }
}

/** One platform: what you're playing on it, or that it's empty, with a way to add a game. */
@Composable
private fun PlatformRow(platform: String, games: List<Game>, onOpen: (Game) -> Unit, onAdd: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, if (games.isEmpty()) BQ.Edge else BQ.B, shape)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(platform, color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (games.isEmpty()) {
                Text("Nothing playing", color = BQ.Muted, fontSize = 12.sp)
            } else {
                games.forEach { g ->
                    Text(
                        "▶ ${g.title} · ${fmtNum(g.hours)}h",
                        Modifier.clickable { onOpen(g) },
                        color = BQ.B, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            if (games.isEmpty()) "+ ADD" else "+",
            Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAdd).padding(horizontal = 10.dp, vertical = 8.dp),
            color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        )
    }
}

/**
 * Choose a game to play on [platform]: from your backlog or wishlist. Games for that platform
 * are listed first; "Everything" lists the rest too, and picking one of those files it under this platform.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlatformPickSheet(vm: BacklogViewModel, platform: String, matcher: Platforms.Matcher, onDismiss: () -> Unit) {
    val s = vm.state
    var everything by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    // The device: one named like the platform (PC, Switch 2), else the phone, where the emulators are.
    var device by remember {
        mutableStateOf(
            s.devices.firstOrNull { matcher.of(it.name) == platform }?.id
                ?: s.devices.firstOrNull { it.name.contains("phone", ignoreCase = true) }?.id
                ?: s.devices.first().id,
        )
    }
    val q = search.trim()
    fun hit(title: String) = q.isEmpty() || title.contains(q, ignoreCase = true)
    val backlog = remember(s.games, everything, q) {
        s.games.filter { it.status == "backlog" && hit(it.title) && (everything || matcher.of(it, s) == platform) }
            .sortedBy { it.title.lowercase() }
    }
    val wishlist = remember(vm.ref.wishlist, s.hiddenWish, everything, q) {
        vm.ref.wishlist.filter { it.id !in s.hiddenWish && hit(it.title) && (everything || matcher.has(it, platform)) }
            .sortedBy { it.title.lowercase() }
    }
    fun done(title: String) {
        vm.showToast("NOW PLAYING\n$title")
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BQ.Shell2,
        contentColor = BQ.Ink,
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("PLAY ON ${platform.uppercase()}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TabChip("For this platform", !everything) { everything = false }
                        TabChip("Everything", everything) { everything = true }
                    }
                    if (everything) Hint("A game that isn't for $platform yet is filed under it when you pick it.")
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        label = { Text("Search") }, singleLine = true,
                        colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item { SectionTitle("FROM YOUR BACKLOG · ${backlog.size}", Modifier.padding(top = 6.dp)) }
            if (backlog.isEmpty()) item { Hint(if (everything) "Nothing in your backlog matches." else "No backlog games for $platform.") }
            items(backlog.take(LIST_MAX), key = { "g:" + it.id }) { g ->
                PickRow(g.title, g.platform.ifBlank { deviceName(s, g.device) }) {
                    val keep = matcher.of(g, s) == platform
                    vm.update { st ->
                        st.copy(games = st.games.map { if (it.id == g.id) it.copy(status = "playing", platform = if (keep) it.platform else platform) else it })
                    }
                    done(g.title)
                }
            }
            if (backlog.size > LIST_MAX) item { Hint("Showing $LIST_MAX of ${backlog.size}. Search to narrow it down.") }

            item {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("FROM YOUR WISHLIST · ${wishlist.size}")
                    if (wishlist.isNotEmpty()) {
                        Text("Play it on", color = BQ.Muted, fontSize = 12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.devices.forEach { d -> TabChip(d.name, device == d.id) { device = d.id } }
                        }
                    }
                }
            }
            if (wishlist.isEmpty()) item { Hint(if (everything) "Nothing in your wishlist matches." else "No wishlist games for $platform.") }
            items(wishlist.take(LIST_MAX), key = { "w:" + it.id }) { w ->
                PickRow(w.title, w.platforms.joinToString(", ")) {
                    val g = Game(
                        id = newId(), title = w.title, device = device, status = "playing",
                        platform = w.platforms.firstOrNull { matcher.of(it) == platform } ?: platform,
                        genre = w.genres.firstOrNull().orEmpty(), cover = vm.wishImage(w.id),
                        est = if (w.hours > 0) w.hours else 0.0,
                        estSource = if (w.hours > 0) "hltb" else "",
                        addedAt = today(), order = System.currentTimeMillis(),
                    )
                    vm.update { st -> st.copy(hiddenWish = st.hiddenWish + w.id, games = st.games + g) }
                    done(w.title)
                }
            }
            if (wishlist.size > LIST_MAX) item { Hint("Showing $LIST_MAX of ${wishlist.size}. Search to narrow it down.") }
            item { PixelButton("CLOSE", Modifier.padding(top = 8.dp), onClick = onDismiss) }
        }
    }
}

/** The most games listed at once in the pick sheet; search finds the rest. */
private const val LIST_MAX = 100

@Composable
private fun PickRow(title: String, about: String, onPick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(BQ.Shell).clickable(onClick = onPick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (about.isNotBlank()) Text(about, color = BQ.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("PLAY ›", color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun NowTile(
    g: Game,
    device: String,
    label: String,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    onBeaten: () -> Unit,
) {
    Tile(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(g.title, label, g.cover, Modifier.width(84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    g.title, color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                StatusLabel(device.uppercase())
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${fmtNum(g.hours)}h",
                        color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp,
                    )
                    Text("played so far", Modifier.padding(bottom = 4.dp), color = BQ.Muted, fontSize = 12.sp, maxLines = 1)
                }
                if (g.est > 0) {
                    Text("About ${estText(g)} to beat", color = BQ.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (g.achievements.isNotEmpty()) {
                    Text(
                        "${g.achievements.count { it.earned }} of ${g.achievements.size} achievements" +
                            if (g.achSource.isBlank()) "" else " · ${g.achSource}",
                        color = BQ.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton(
                if (g.launch != null) "RESUME" else "SET UP START",
                Modifier.weight(1f), kind = BtnKind.B, onClick = onResume,
            )
            PixelButton("BEATEN IT", Modifier.weight(1f), onClick = onBeaten)
        }
    }
}
