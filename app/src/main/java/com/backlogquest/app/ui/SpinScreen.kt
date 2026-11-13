package com.backlogquest.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.backlogquest.app.data.AppState
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.Device
import com.backlogquest.app.data.Game
import com.backlogquest.app.data.RotationState
import com.backlogquest.app.data.WISH_TAGS
import com.backlogquest.app.data.WishItem
import com.backlogquest.app.data.deviceName
import com.backlogquest.app.data.fmtHours
import com.backlogquest.app.data.playedOn
import com.backlogquest.app.data.rotation
import com.backlogquest.app.data.wishTagOk
import com.backlogquest.app.discover.GameProvider
import com.backlogquest.app.discover.ShelfViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import com.backlogquest.app.discover.Game as DiscoverGame

private val MODES = listOf("next" to "What next", "new" to "Something new", "from" to "Where from")

/**
 * The Spin tab. Three ways to let chance decide:
 * what to play next from your backlog, something new to pick up,
 * or which source your next game should come from.
 */
@Composable
fun SpinScreen(
    vm: BacklogViewModel,
    f: Filters,
    onAdd: () -> Unit,
    onOpenWish: (String) -> Unit,
    onOpenDiscover: (DiscoverGame) -> Unit,
    onAddToBacklog: (DiscoverGame) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf("next") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("SPIN")
        ChipRow(MODES, mode) { mode = it }
        when (mode) {
            "new" -> SomethingNew(vm, f, onOpenWish, onOpenDiscover, onAddToBacklog)
            "from" -> WhereFrom(vm, onOpenWish, onOpenDiscover, onAddToBacklog)
            else -> WhatNext(vm, onAdd)
        }
    }
}

// ───────────────────────── The wheel ─────────────────────────

class WheelSlice(val key: String, val label: String, val enabled: Boolean = true)

/**
 * Where the wheel should stop so slice [index] of [count] sits under the pointer at the
 * top, after at least three full turns from [current] (all in degrees).
 */
fun wheelTarget(current: Float, index: Int, count: Int): Float {
    val want = (360f - index * (360f / count)) % 360f
    val base = current + 3 * 360f
    val at = ((base % 360f) + 360f) % 360f
    var extra = want - at
    if (extra < 0f) extra += 360f
    return base + extra
}

/** "GameBrew · Nintendo 3DS" and "GameBrew · PSP" are both the source "GameBrew". */
fun sourceName(providerName: String): String = providerName.substringBefore(" · ").trim()

private val SliceOdd = Color(0xFF322D4B)

@Composable
private fun Wheel(slices: List<WheelSlice>, rotation: Float, landed: String?) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    val small = label.copy(fontSize = 9.5.sp)
    val described = "Wheel with " + slices.joinToString(", ") { it.label }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.widthIn(max = 340.dp).fillMaxWidth(0.94f).aspectRatio(1f)
                .semantics { contentDescription = described },
        ) {
            val n = slices.size
            if (n == 0) return@Canvas
            val step = 360f / n
            val pointer = 20.dp.toPx()
            val radius = size.minDimension / 2 - pointer / 2
            val c = Offset(size.width / 2, size.height / 2 + pointer / 4)
            val box = Size(radius * 2, radius * 2)
            val corner = Offset(c.x - radius, c.y - radius)
            val textRoom = (radius * 0.70f).toInt()
            val inner = radius * 0.24f

            slices.forEachIndexed { i, slice ->
                // Canvas angles start at 3 o'clock; slice 0 starts centred at the top.
                val mid = rotation + i * step - 90f
                val hit = slice.key == landed
                val fill = when {
                    hit -> BQ.B
                    n % 2 == 1 && i == n - 1 -> SliceOdd
                    i % 2 == 0 -> BQ.Edge
                    else -> BQ.Shell2
                }
                drawArc(fill, mid - step / 2, step, useCenter = true, topLeft = corner, size = box)
                if (n > 1) {
                    val edge = Math.toRadians((mid - step / 2).toDouble())
                    drawLine(
                        BQ.Shell, c,
                        Offset(c.x + radius * cos(edge).toFloat(), c.y + radius * sin(edge).toFloat()),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                // Long names drop a size before they're cut short.
                val text = listOf(label, small).map {
                    measurer.measure(
                        slice.label, it, overflow = TextOverflow.Ellipsis, maxLines = 1,
                        constraints = Constraints(maxWidth = textRoom),
                    )
                }.let { (normal, smaller) -> if (normal.hasVisualOverflow) smaller else normal }
                val colour = when {
                    hit -> BQ.Shell
                    slice.enabled -> BQ.Ink
                    else -> BQ.Muted.copy(alpha = 0.55f)
                }
                val slack = (textRoom - text.size.width) / 2f
                val facing = ((mid % 360f) + 360f) % 360f
                // Names on the left half are turned round so none of them read upside down.
                if (facing > 90f && facing < 270f) {
                    rotate(mid + 180f, c) {
                        drawText(text, colour, Offset(c.x - inner - textRoom + slack, c.y - text.size.height / 2f))
                    }
                } else {
                    rotate(mid, c) {
                        drawText(text, colour, Offset(c.x + inner + slack, c.y - text.size.height / 2f))
                    }
                }
            }
            drawCircle(BQ.Edge, radius, c, style = Stroke(3.dp.toPx()))
            drawCircle(BQ.Shell, radius * 0.15f, c)
            drawCircle(BQ.Edge, radius * 0.15f, c, style = Stroke(3.dp.toPx()))
            // The pointer at the top.
            val half = 11.dp.toPx()
            val tip = Path().apply {
                moveTo(c.x - half, 0f)
                lineTo(c.x + half, 0f)
                lineTo(c.x, pointer + 2.dp.toPx())
                close()
            }
            drawPath(tip, BQ.A)
            drawPath(tip, BQ.Shell, style = Stroke(2.dp.toPx()))
        }
    }
}

/** Runs one spin at a time: turning the wheel, or flicking through names in the slot. */
private class Spinner(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set
    val turn = Animatable(0f)

    fun go(block: suspend Spinner.() -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    suspend fun wheel(index: Int, count: Int) {
        if (count <= 0) return
        turn.animateTo(wheelTarget(turn.value, index, count), tween(2400, easing = CubicBezierEasing(0.15f, 0.7f, 0.1f, 1f)))
        turn.snapTo(((turn.value % 360f) + 360f) % 360f)
    }

    /** Flicks through names for about a second before the caller shows the real pick. */
    suspend fun flick(names: List<String>, show: (String?) -> Unit) {
        if (names.size > 1) {
            names.forEach {
                show(it)
                delay(70)
            }
        }
        show(null)
    }
}

@Composable
private fun rememberSpinner(): Spinner {
    val scope = rememberCoroutineScope()
    return remember(scope) { Spinner(scope) }
}

private fun backlogPool(s: AppState, device: String): List<Game> =
    s.games.filter { it.device == device && (it.status == "backlog" || it.status == "playing") }

private fun <T> flickNames(pool: List<T>, final: T, name: (T) -> String): List<String> =
    if (pool.size < 2) emptyList() else List(14) { name(pool.random()) } + name(final)

// ───────────────────────── What next ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhatNext(vm: BacklogViewModel, onAdd: () -> Unit) {
    val s = vm.state
    val rot = remember(s) { rotation(s) }
    val spinner = rememberSpinner()
    var pickedDevice by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedGame by rememberSaveable { mutableStateOf<String?>(null) }
    var slotText by remember { mutableStateOf<String?>(null) }

    val onWheel = s.devices.filter { backlogPool(s, it.id).isNotEmpty() }
    val eligible = onWheel.filter { (rot[it.id]?.cool ?: 0) == 0 }
    val game = pickedGame?.let { id -> s.games.firstOrNull { it.id == id } }

    Hint("Spin for a device, then for a game on it. Devices on cooldown sit out, by your two-for-two rule.")
    if (onWheel.isNotEmpty()) {
        Wheel(onWheel.map { WheelSlice(it.id, it.name, it in eligible) }, spinner.turn.value, pickedDevice)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelButton("SPIN DEVICE", Modifier.weight(1f), kind = BtnKind.B, enabled = eligible.isNotEmpty() && !spinner.busy) {
            val d = eligible.random()
            spinner.go {
                pickedDevice = null
                pickedGame = null
                slotText = null
                wheel(onWheel.indexOf(d), onWheel.size)
                pickedDevice = d.id
                slotText = "${d.name} it is. Now spin a game."
            }
        }
        val dev = pickedDevice
        PixelButton(
            "SPIN GAME", Modifier.weight(1f), kind = BtnKind.A,
            enabled = dev != null && backlogPool(s, dev).isNotEmpty() && !spinner.busy,
        ) {
            val pool = backlogPool(s, dev.orEmpty())
            val g = pool.random()
            spinner.go {
                flick(flickNames(pool, g) { it.title }) { slotText = it }
                pickedGame = g.id
            }
        }
    }
    Slot(
        slotText ?: game?.title ?: pickedDevice?.let { "Spin for a game on ${deviceName(s, it)}" } ?: "Spin or choose a device",
        dim = spinner.busy || (slotText == null && game == null),
    )
    if (game != null) BacklogPick(vm, game)
    if (onWheel.isEmpty() && vm.loaded) {
        Panel {
            Hint("Your backlog is empty. Add games in GAMES and the wheel will pick from them.")
            PixelButton("ADD A GAME", kind = BtnKind.B, onClick = onAdd)
        }
    }
    if (s.devices.isNotEmpty()) {
        SectionTitle("OR CHOOSE A DEVICE")
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2,
        ) {
            s.devices.forEach { d ->
                DeviceTile(
                    d, rot[d.id], backlogPool(s, d.id).size, selected = pickedDevice == d.id,
                    modifier = Modifier.weight(1f),
                ) {
                    pickedDevice = d.id
                    pickedGame = null
                    slotText = null
                }
            }
        }
    }
}

@Composable
private fun DeviceTile(
    d: Device, r: RotationState?, games: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit,
) {
    val cool = (r?.cool ?: 0) > 0
    val usable = !cool && games > 0
    Surface(
        onClick = onClick,
        enabled = usable,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF2F2A20) else BQ.Shell2,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) BQ.B else BQ.Edge),
        modifier = modifier.alpha(if (usable) 1f else 0.5f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(d.name, color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            when {
                d.exempt -> StatusLabel("EXEMPT", BQ.OwnBg, BQ.B)
                cool -> StatusLabel("COOLDOWN · ${r?.cool} TO GO", BQ.CoolBg, BQ.CoolFg)
                else -> StatusLabel("READY" + if ((r?.count ?: 0) > 0) " · 1 DONE" else "", BQ.OkBg, BQ.Ok)
            }
            Hint("$games game${if (games == 1) "" else "s"} ready")
        }
    }
}

// ───────────────────────── Something new ─────────────────────────

/** Saved Discover catalogues, by source id, with how many games each holds. */
@Composable
private fun rememberSavedCatalogues(shelf: ShelfViewModel, key: Any?): Map<String, Int> {
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(key) {
        counts = try {
            shelf.graph.db.shelf().cachedCounts().associate { it.provider to it.games }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
    }
    return counts
}

private fun wishPool(vm: BacklogViewModel, tag: String): List<WishItem> {
    val hidden = vm.state.hiddenWish
    val t = if (tag == "unreleased") "all" else tag
    return vm.ref.wishlist.filter { it.id !in hidden && "unreleased" !in it.tags && wishTagOk(it, t) }
}

@Composable
private fun SomethingNew(
    vm: BacklogViewModel,
    f: Filters,
    onOpenWish: (String) -> Unit,
    onOpenDiscover: (DiscoverGame) -> Unit,
    onAddToBacklog: (DiscoverGame) -> Unit,
) {
    val shelf: ShelfViewModel = viewModel()
    val disabled by shelf.disabled.collectAsStateWithLifecycle()
    val spinner = rememberSpinner()
    var pool by rememberSaveable { mutableStateOf("all") }
    // Filters for your imported CSV lists.
    var genre by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf("") }
    var wishPick by rememberSaveable { mutableStateOf<String?>(null) }
    var shelfPick by remember { mutableStateOf<DiscoverGame?>(null) }
    var slotText by remember { mutableStateOf<String?>(null) }

    val saved = rememberSavedCatalogues(shelf, disabled)
    val catalogues = shelf.graph.registry.providers.map { it.id }.filter { it !in disabled && (saved[it] ?: 0) > 0 }
    val catalogueGames = catalogues.sumOf { saved[it] ?: 0 }
    val wishes = remember(vm.ref, vm.state.hiddenWish, f.wishTag) { wishPool(vm, f.wishTag) }
    val imported = remember(vm.ref, vm.state.hiddenWish) { wishPool(vm, "imported") }
    val platforms = remember(imported) { imported.flatMap { it.platforms }.distinct().sorted() }
    val onPlatform = remember(imported, platform) {
        if (platform.isBlank()) imported else imported.filter { platform in it.platforms }
    }
    // Genres on the chosen platform, most common first.
    val genres = remember(onPlatform) {
        onPlatform.flatMap { it.genres }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    }
    val importedPool = remember(onPlatform, genre) {
        if (genre.isBlank()) onPlatform else onPlatform.filter { genre in it.genres }
    }
    val wish = wishPick?.let { id -> vm.ref.wishlist.firstOrNull { it.id == id } }

    Hint("One spin for a game you don't own yet: from your wishlist, or from the homebrew and free-game catalogues in Discover.")
    ChipRow(
        listOf(
            "all" to "Everything",
            "wishlist" to "Wishlist ${"%,d".format(wishes.size)}",
            "discover" to "Homebrew & free ${"%,d".format(catalogueGames)}",
        ) + if (imported.isEmpty()) emptyList() else listOf("imported" to "My lists ${"%,d".format(imported.size)}"),
        pool,
    ) { pool = it }
    if (pool == "imported") {
        // Your CSV lists can be narrowed to one platform and one genre.
        if (platforms.size > 1) {
            ChipRow(listOf("" to "Any platform") + platforms.map { it to it }, platform) {
                platform = it
                genre = ""
            }
        }
        ChipRow(listOf("" to "Any genre") + genres.map { it.key to "${it.key} ${it.value}" }, genre) { genre = it }
        Hint("${"%,d".format(importedPool.size)} game${if (importedPool.size == 1) "" else "s"} match.")
    } else if (pool != "discover") {
        ChipRow(WISH_TAGS, f.wishTag) { f.wishTag = it }
    }
    if ((pool == "all" || pool == "discover") && catalogueGames == 0) {
        Hint("No Discover catalogues are saved on your phone yet. Open one in Discover and its games join this spin.")
    }

    val canWish = (pool == "all" || pool == "wishlist") && wishes.isNotEmpty()
    val canShelf = (pool == "all" || pool == "discover") && catalogues.isNotEmpty()
    val canImported = pool == "imported" && importedPool.isNotEmpty()
    PixelButton(
        "SPIN", Modifier.fillMaxWidth(), kind = BtnKind.A,
        enabled = (canWish || canShelf || canImported) && !spinner.busy,
    ) {
        // With both on the table it's a coin toss, so the huge wishlist doesn't drown out the catalogues.
        val fromShelf = if (canWish && canShelf) Random.nextBoolean() else canShelf
        spinner.go {
            if (canImported) {
                val pick = importedPool.random()
                flick(flickNames(importedPool, pick) { it.title }) { slotText = it }
                wishPick = pick.id
                shelfPick = null
            } else if (fromShelf) {
                val rows = shelf.graph.db.shelf().randomCached(catalogues, 15).map(shelf.graph.repository::decode)
                val pick = rows.lastOrNull() ?: return@go
                flick(rows.map { it.title }) { slotText = it }
                shelfPick = pick
                wishPick = null
            } else {
                val pick = wishes.random()
                flick(flickNames(wishes, pick) { it.title }) { slotText = it }
                wishPick = pick.id
                shelfPick = null
            }
        }
    }
    Slot(
        slotText ?: wish?.title ?: shelfPick?.title ?: "Spin for something new",
        dim = spinner.busy || (slotText == null && wish == null && shelfPick == null),
    )
    if (wish != null) WishPick(vm, wish, onOpenWish)
    shelfPick?.let { g -> ShelfPick(g, shelf, onOpenDiscover, onAddToBacklog) }
}

// ───────────────────────── Where from ─────────────────────────

private sealed class Source(val key: String, val label: String) {
    class OnDevice(val device: Device) : Source("device:${device.id}", device.name)
    object Wishlist : Source("wishlist", "Wishlist")
    class Catalogue(name: String, val providers: List<GameProvider>) : Source("catalogue:$name", name)
}

@Composable
private fun WhereFrom(
    vm: BacklogViewModel,
    onOpenWish: (String) -> Unit,
    onOpenDiscover: (DiscoverGame) -> Unit,
    onAddToBacklog: (DiscoverGame) -> Unit,
) {
    val s = vm.state
    val shelf: ShelfViewModel = viewModel()
    val disabled by shelf.disabled.collectAsStateWithLifecycle()
    val rot = remember(s) { rotation(s) }
    val spinner = rememberSpinner()
    var pickedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedGame by rememberSaveable { mutableStateOf<String?>(null) }
    var wishPick by rememberSaveable { mutableStateOf<String?>(null) }
    var shelfPick by remember { mutableStateOf<DiscoverGame?>(null) }
    var slotText by remember { mutableStateOf<String?>(null) }

    val wishes = remember(vm.ref, s.hiddenWish) { wishPool(vm, "all") }
    val sources: List<Source> = remember(s, wishes.size, disabled) {
        val devices = s.devices.filter { backlogPool(s, it.id).isNotEmpty() }.map { Source.OnDevice(it) }
        val wishlist = if (wishes.isNotEmpty()) listOf(Source.Wishlist) else emptyList()
        val catalogues = shelf.graph.registry.providers.filter { it.id !in disabled }
            .groupBy { sourceName(it.name) }.map { (name, group) -> Source.Catalogue(name, group) }
        devices + wishlist + catalogues
    }
    fun usable(src: Source) = src !is Source.OnDevice || (rot[src.device.id]?.cool ?: 0) == 0
    val eligible = sources.filter(::usable)
    val source = sources.firstOrNull { it.key == pickedKey }
    val game = pickedGame?.let { id -> s.games.firstOrNull { it.id == id } }
    val wish = wishPick?.let { id -> vm.ref.wishlist.firstOrNull { it.id == id } }
    val resultTitle = game?.title ?: wish?.title ?: shelfPick?.title

    fun clearGame() {
        pickedGame = null
        wishPick = null
        shelfPick = null
        slotText = null
    }

    Hint("Spin once for a source, then again for a game from it. Your devices, your wishlist and every Discover source are on the wheel.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stage("1 · SOURCE", source?.label ?: "—", done = source != null, Modifier.weight(1f))
        Stage("2 · GAME", resultTitle ?: "—", done = resultTitle != null, Modifier.weight(1f))
    }
    Wheel(sources.map { WheelSlice(it.key, it.label, usable(it)) }, spinner.turn.value, pickedKey)
    PixelButton(
        "SPIN FOR A SOURCE", Modifier.fillMaxWidth(), kind = BtnKind.A,
        enabled = eligible.isNotEmpty() && !spinner.busy,
    ) {
        val pick = eligible.random()
        spinner.go {
            pickedKey = null
            clearGame()
            wheel(sources.indexOf(pick), sources.size)
            pickedKey = pick.key
        }
    }
    if (source != null) {
        PixelButton(
            "SPIN A GAME FROM ${source.label.uppercase()}", Modifier.fillMaxWidth(), kind = BtnKind.B,
            enabled = !spinner.busy,
        ) {
            when (source) {
                is Source.OnDevice -> {
                    val pool = backlogPool(s, source.device.id)
                    val g = pool.random()
                    spinner.go {
                        clearGame()
                        flick(flickNames(pool, g) { it.title }) { slotText = it }
                        pickedGame = g.id
                    }
                }
                Source.Wishlist -> {
                    val w = wishes.random()
                    spinner.go {
                        clearGame()
                        flick(flickNames(wishes, w) { it.title }) { slotText = it }
                        wishPick = w.id
                    }
                }
                is Source.Catalogue -> spinner.go {
                    clearGame()
                    val dao = shelf.graph.db.shelf()
                    val saved = dao.cachedCounts().associate { it.provider to it.games }
                    var ready = source.providers.filter { (saved[it.id] ?: 0) > 0 }
                    if (ready.isEmpty()) {
                        // Nothing from this source is on the phone yet: fetch one of its catalogues now.
                        val p = source.providers.random()
                        slotText = "Fetching ${p.name}…"
                        try {
                            shelf.graph.repository.catalogue(p.id, refresh = false)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            slotText = null
                            vm.showToast("Couldn't reach ${p.name}.\nCheck your connection and spin again.")
                            return@go
                        }
                        ready = listOf(p)
                    }
                    // Pick the catalogue first, so each platform of a source gets a fair turn.
                    val rows = dao.randomCached(listOf(ready.random().id), 15).map(shelf.graph.repository::decode)
                    val pick = rows.lastOrNull()
                    if (pick == null) {
                        slotText = null
                        vm.showToast("${source.label} has no games listed right now.")
                        return@go
                    }
                    flick(rows.map { it.title }) { slotText = it }
                    shelfPick = pick
                }
            }
        }
        Slot(
            slotText ?: resultTitle ?: "Spin for a game from ${source.label}",
            dim = spinner.busy || (slotText == null && resultTitle == null),
        )
    }
    if (game != null) BacklogPick(vm, game)
    if (wish != null) WishPick(vm, wish, onOpenWish)
    shelfPick?.let { g -> ShelfPick(g, shelf, onOpenDiscover, onAddToBacklog) }
}

@Composable
private fun Stage(label: String, value: String, done: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.clip(shape).background(BQ.Shell2).border(1.dp, if (done) BQ.B else BQ.Edge, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = BQ.Muted, fontFamily = BQ.Mono, fontSize = 10.sp)
        Text(
            value, color = if (done) BQ.B else BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ───────────────────────── Result cards ─────────────────────────

@Composable
private fun BacklogPick(vm: BacklogViewModel, game: Game) {
    val s = vm.state
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(game.title, game.genre.ifBlank { deviceName(s, game.device) }, game.cover, Modifier.width(96.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOf(playedOn(s, game), estText(game), game.genre).filter { it.isNotBlank() }.joinToString(" · "),
                    color = BQ.Ink,
                )
                if (game.desc.isNotBlank()) Hint(game.desc)
                if (game.status == "playing") {
                    StatusLabel("ALREADY PLAYING", BQ.OkBg, BQ.Ok)
                } else {
                    PixelButton("START QUEST", kind = BtnKind.A) {
                        vm.update { st ->
                            st.copy(games = st.games.map { if (it.id == game.id) it.copy(status = "playing") else it })
                        }
                        vm.showToast("QUEST STARTED\n${game.title}")
                    }
                }
            }
        }
    }
}

@Composable
private fun WishPick(vm: BacklogViewModel, wish: WishItem, onOpenWish: (String) -> Unit) {
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(wish.title, wish.platforms.firstOrNull().orEmpty(), vm.wishImage(wish.id), Modifier.width(96.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusLabel(if (wish.imported) "FROM YOUR LISTS" else "FROM YOUR WISHLIST", BQ.OwnBg, BQ.B)
                if (wish.genres.isNotEmpty()) Hint(wish.genres.joinToString(", "))
                if (wish.hours > 0) {
                    Text(
                        "${fmtHours(wish.hours)} main story",
                        color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold,
                    )
                }
                Hint(platformList(wish.platforms))
                TagRow(wish.tags)
                PixelButton("DETAILS", kind = BtnKind.A) { onOpenWish(wish.id) }
            }
        }
    }
}

@Composable
private fun ShelfPick(
    g: DiscoverGame,
    shelf: ShelfViewModel,
    onOpenDiscover: (DiscoverGame) -> Unit,
    onAddToBacklog: (DiscoverGame) -> Unit,
) {
    val from = shelf.graph.registry.providers.firstOrNull { it.id == g.provider }?.name ?: "Discover"
    Panel {
        StatusLabel("FROM ${from.uppercase()}", BQ.OwnBg, BQ.B)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(g.title, g.consoles.joinToString(), g.artwork.orEmpty(), Modifier.width(84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(g.title, color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                g.developer?.let { Text(it, color = BQ.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (g.description.isNotBlank()) {
                    Text(g.description, color = BQ.Muted, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton("ADD TO BACKLOG", Modifier.weight(1.4f), kind = BtnKind.B) { onAddToBacklog(g) }
            PixelButton("OPEN PAGE", Modifier.weight(1f)) { onOpenDiscover(g) }
        }
    }
}
