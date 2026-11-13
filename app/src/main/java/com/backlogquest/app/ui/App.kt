package com.backlogquest.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backlogquest.app.data.AchievementTotals
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.Game
import com.backlogquest.app.data.newId
import com.backlogquest.app.data.normTitle
import com.backlogquest.app.data.today
import com.backlogquest.app.data.totals
import com.backlogquest.app.discover.Consoles
import com.backlogquest.app.discover.Game as DiscoverGame
import kotlinx.coroutines.delay

enum class Tab(val icon: String, val label: String) {
    NOW("▶", "NOW"),
    SPIN("◔", "SPIN"),
    GAMES("≡", "GAMES"),
    DISCOVER("◎", "DISCOVER"),
    EMULATORS("◫", "EMULATORS"),
    PROFILE("★", "PROFILE"),
    SETUP("⚙", "SETUP"),
}

/** Filters shared between the Play and Games screens, as in the web version. */
class Filters {
    var time by mutableStateOf("any")
    var wishTag by mutableStateOf("all")
    var sort by mutableStateOf("az")
    var libTab by mutableStateOf("backlog")
    var grid by mutableStateOf(true)
    var search by mutableStateOf("")
}

/** What the edit sheet is showing: a new game, or an existing one by id. */
sealed class EditTarget {
    data class New(val status: String) : EditTarget()
    /** [beaten] opens the sheet already switched to Beaten, ready to confirm. */
    data class Existing(val id: String, val beaten: Boolean = false) : EditTarget()
    /** A game from your imported history, by its position in the history list. */
    data class History(val index: Int) : EditTarget()
}

@Composable
fun BacklogApp(vm: BacklogViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.NOW) }
    val filters = remember { Filters() }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var wishOpen by remember { mutableStateOf<String?>(null) }
    // Kept here, above the tabs, so Discover remembers where you were when you come back to it.
    val discoverNav = rememberSaveable(saver = DiscoverNav.Saver) { DiscoverNav() }
    val discoverSaved = rememberSaveableStateHolder()

    BackHandler(enabled = tab != Tab.NOW) { tab = Tab.NOW }

    /** Copies a Discover game into your backlog and opens it so you can pick the device. */
    val addFromDiscover: (DiscoverGame) -> Unit = { found ->
        val s = vm.state
        val already = s.games.firstOrNull {
            normTitle(it.title) == normTitle(found.title) && it.status in setOf("backlog", "playing")
        }
        if (already != null) {
            vm.showToast("ALREADY IN YOUR BACKLOG\n${already.title}")
            editing = EditTarget.Existing(already.id)
        } else {
            val console = found.consoles.firstOrNull().orEmpty()
            val game = Game(
                id = newId(), title = found.title, device = s.devices.first().id, status = "backlog",
                genre = found.genres.firstOrNull().orEmpty(),
                desc = found.description.take(400),
                cover = found.artwork.orEmpty(),
                platform = Consoles.all.firstOrNull { it.id == console }?.name ?: console,
                source = "Discover",
                addedAt = today(), order = System.currentTimeMillis(),
            )
            vm.update { it.copy(games = it.games + game) }
            vm.showToast("ADDED TO BACKLOG\n${game.title}")
            editing = EditTarget.Existing(game.id)
        }
    }

    Box(Modifier.fillMaxSize().background(BQ.Shell)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                when (tab) {
                    Tab.NOW -> NowScreen(
                        vm,
                        onEdit = { editing = EditTarget.Existing(it) },
                        onBeaten = { editing = EditTarget.Existing(it, beaten = true) },
                        onSpin = { tab = Tab.SPIN },
                    )
                    Tab.SPIN -> SpinScreen(
                        vm, filters,
                        onAdd = { editing = EditTarget.New("backlog") },
                        onOpenWish = { wishOpen = it },
                        onOpenDiscover = { game ->
                            discoverNav.switchSection(DiscoverNav.HOME)
                            discoverNav.open("game/${game.id}")
                            tab = Tab.DISCOVER
                        },
                        onAddToBacklog = addFromDiscover,
                    )
                    Tab.GAMES -> GamesScreen(
                        vm, filters,
                        onAdd = { editing = EditTarget.New(if (filters.libTab == "beaten") "beaten" else "backlog") },
                        onEdit = { editing = EditTarget.Existing(it) },
                        onEditHistory = { editing = EditTarget.History(it) },
                        onOpenWish = { wishOpen = it },
                    )
                    Tab.DISCOVER -> DiscoverScreen(
                        discoverNav, discoverSaved, toast = { vm.toast = it }, onAddToBacklog = addFromDiscover,
                        bq = vm, onOpenWish = { wishOpen = it },
                    )
                    Tab.EMULATORS -> EmulatorsScreen(vm)
                    Tab.PROFILE -> ProfileScreen(vm)
                    Tab.SETUP -> SetupScreen(vm)
                }
            }
            BottomNav(tab) { tab = it }
        }
        ToastHost(vm)
    }

    editing?.let { target -> EditSheet(vm, target, onDismiss = { editing = null }) }
    wishOpen?.let { id -> WishSheet(vm, id, onDismiss = { wishOpen = null }) }
    // Opened from Games → Wishlist, or by sharing a page to the app from a browser.
    vm.webImport?.let { address -> WebImportSheet(vm, address, onDismiss = { vm.webImport = null }) }
    if (vm.csvImportOpen) CsvImportSheet(vm) { vm.csvImportOpen = false }
    if (vm.duplicatesOpen) DuplicatesSheet(vm) { vm.duplicatesOpen = false }
    vm.lastCrash?.let { details -> CrashDialog(details, onDone = vm::dismissCrash) }
    SessionDialog(vm)
}

/** Narrowest a tab gets before the bar starts scrolling sideways instead of squeezing. */
private val MinTabWidth = 76.dp

/**
 * The tab bar. On a wide screen the tabs share the width evenly; on a phone they keep a
 * comfortable size and the bar scrolls sideways, following whichever tab is selected.
 */
@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(BQ.Shell2)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BQ.Edge))
        BoxWithConstraints(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            val tabs = Tab.entries
            val tabWidth = maxOf(maxWidth / tabs.size, MinTabWidth)
            val scroll = rememberScrollState()
            val tabPx = with(LocalDensity.current) { tabWidth.roundToPx() }
            val barPx = constraints.maxWidth
            // Keep the selected tab in view (centred when there's room), e.g. after pressing Back.
            LaunchedEffect(current, tabPx, barPx) {
                scroll.animateScrollTo((current.ordinal * tabPx - (barPx - tabPx) / 2).coerceAtLeast(0))
            }
            Row(Modifier.horizontalScroll(scroll)) {
                tabs.forEach { t ->
                    val on = t == current
                    Surface(
                        onClick = { onSelect(t) },
                        color = Color.Transparent,
                        modifier = Modifier.width(tabWidth),
                    ) {
                        Column(
                            Modifier.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(if (on) BQ.A else BQ.Edge),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(t.icon, color = if (on) Color.White else BQ.Ink, fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                t.label, color = if (on) BQ.Ink else BQ.Muted,
                                fontFamily = BQ.Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            // A fade and an arrow on whichever side still has tabs out of view.
            if (scroll.canScrollBackward) MoreTabsHint(toEnd = false, Modifier.align(Alignment.CenterStart))
            if (scroll.canScrollForward) MoreTabsHint(toEnd = true, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun MoreTabsHint(toEnd: Boolean, modifier: Modifier) {
    val fade = listOf(Color.Transparent, BQ.Shell2)
    Box(
        modifier
            .width(26.dp)
            .height(56.dp)
            .background(Brush.horizontalGradient(if (toEnd) fade else fade.reversed())),
        contentAlignment = if (toEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(if (toEnd) "›" else "‹", color = BQ.B, fontFamily = BQ.Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** "The app closed unexpectedly last time", with the reason and a button to copy it. */
@Composable
private fun CrashDialog(details: String, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val reason = details.lineSequence().dropWhile { it.isNotBlank() }.firstOrNull { it.isNotBlank() }.orEmpty()
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = BQ.Shell2,
        titleContentColor = BQ.Ink,
        textContentColor = BQ.Muted,
        title = { Text("Backlog Quest closed unexpectedly", fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Last time the app was open, it crashed. Copy the details and send them to Claude so it can be fixed.")
                Text(reason, color = BQ.Ink, fontFamily = BQ.Mono, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        },
        confirmButton = {
            PixelButton("COPY DETAILS", kind = BtnKind.B) {
                clipboard.setText(AnnotatedString(details))
                onDone()
            }
        },
        dismissButton = { PixelButton("DISMISS", onClick = onDone) },
    )
}

@Composable
private fun ToastHost(vm: BacklogViewModel) {
    val text = vm.toast ?: return
    LaunchedEffect(text) {
        delay(2600)
        if (vm.toast == text) vm.toast = null
    }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            text,
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(BQ.Lcd)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = BQ.LcdInk, fontFamily = BQ.Mono, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/** The console bezel with the LCD status screen, shown at the top of every screen. */
@Composable
fun StatusPanel(vm: BacklogViewModel) {
    val s = vm.state
    val ref = vm.ref
    val t = remember(s, ref) { totals(s, ref) }
    val earned = remember(s) { AchievementTotals.summary(s) }
    val playing = s.games.filter { it.status == "playing" }
    val backlog = s.games.count { it.status == "backlog" }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 34.dp, bottomStart = 14.dp))
            .background(BQ.Shell2)
            .border(
                2.dp, BQ.Edge,
                RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 34.dp, bottomStart = 14.dp),
            )
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(BQ.A))
            Spacer(Modifier.width(6.dp))
            Text(
                "PLAYER ONE", Modifier.weight(1f),
                color = BQ.Muted, fontFamily = BQ.Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    !vm.loaded -> "LOADING…"
                    vm.syncing -> "SYNCING…"
                    else -> "SAVED ON PHONE"
                },
                color = BQ.Muted, fontFamily = BQ.Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(BQ.Lcd)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("LV ", color = BQ.LcdInk, fontFamily = BQ.Mono, fontSize = 18.sp)
                Text(
                    "${t.level}", Modifier.weight(1f),
                    color = BQ.LcdInk, fontFamily = BQ.Mono, fontSize = 38.sp, fontWeight = FontWeight.Bold,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(t.rank, color = BQ.LcdInk, fontFamily = BQ.Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${"%,d".format(t.xp)} XP", color = BQ.LcdDim, fontFamily = BQ.Mono, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            // XP bar, drawn as chunky segments.
            Box(
                Modifier.fillMaxWidth().height(12.dp).border(2.dp, BQ.LcdInk).padding(2.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(t.pct.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .background(BQ.LcdInk),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${"%,d".format(t.toNext)} XP to level ${t.level + 1}",
                color = BQ.LcdDim, fontFamily = BQ.Mono, fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                LcdStat("${t.beaten}", "BEATEN", Modifier.weight(1f))
                LcdStat("${t.year}", "IN ${today().take(4)}", Modifier.weight(1f))
                LcdStat("$backlog", "BACKLOG", Modifier.weight(1f))
                // Every achievement you've earned, on every platform that has them.
                LcdStat("%,d".format(earned.total), "ACHIEVEMENTS", Modifier.weight(1.5f))
            }
            if (earned.parts.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    earned.parts.joinToString(" · ") { "${it.short} ${"%,d".format(it.count)}" },
                    Modifier.fillMaxWidth(),
                    color = BQ.LcdDim, fontFamily = BQ.Mono, fontSize = 11.sp,
                    textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(BQ.LcdDim))
            Spacer(Modifier.height(6.dp))
            Text(
                "▶ " + if (playing.isEmpty()) "NO ACTIVE QUEST"
                else "NOW PLAYING: " + playing.joinToString(", ") { it.title },
                color = BQ.LcdInk, fontFamily = BQ.Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LcdStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = BQ.LcdInk, fontFamily = BQ.Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = BQ.LcdDim, fontFamily = BQ.Mono, fontSize = 10.sp, maxLines = 1)
    }
}

