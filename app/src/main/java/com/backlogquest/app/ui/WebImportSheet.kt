package com.backlogquest.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.Paging
import com.backlogquest.app.data.WebFound
import com.backlogquest.app.data.WebGame
import com.backlogquest.app.data.Duplicates
import com.backlogquest.app.data.WebImport
import com.backlogquest.app.data.WishImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Add games to the wishlist from a web address: paste one, or share a page to
 * the app from your browser. Shows what was found so you can untick anything
 * that isn't a game before adding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebImportSheet(vm: BacklogViewModel, startAddress: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(startAddress) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<WebFound?>(null) }
    // "list" adds the games found on the page; "page" adds the page itself as one game.
    var mode by remember { mutableStateOf("list") }
    var name by remember { mutableStateOf("") }
    var skipped by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Games without a picture on the list usually have one on their own page.
    var fetchAfter by remember { mutableStateOf(true) }
    var source by remember { mutableStateOf("") }
    var skipKnown by remember { mutableStateOf(true) }
    // Reading every page of a list split over several.
    var pageFrom by remember { mutableStateOf("1") }
    var pageTo by remember { mutableStateOf("") }
    var pagesJob by remember { mutableStateOf<Job?>(null) }
    var pagesNote by remember { mutableStateOf("") }

    fun readPages(result: WebFound, paging: Paging) {
        if (pagesJob != null) return
        val from = pageFrom.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
        val to = pageTo.trim().toIntOrNull()?.takeIf { it >= from }
        problem = ""
        pagesNote = "Reading page $from…"
        var gathered = emptyList<WebGame>()
        var lastKnown = paging.last
        fun keep() {
            if (gathered.isNotEmpty()) {
                // The games already shown stay, with the new pages' games after them.
                val all = (result.games + gathered).distinctBy { it.url }
                found = WebFound(result.name, result.page, all, paging = paging.copy(last = lastKnown))
                skipped = emptySet()
            }
        }
        pagesJob = scope.launch {
            try {
                WebImport.readPages(context, paging, from, to) { page, last, games ->
                    gathered = games
                    lastKnown = last
                    scope.launch { pagesNote = "Read page $page of ${to ?: last}${if (to == null) "+" else ""} · ${"%,d".format(games.size)} games" }
                }
                keep()
                pagesNote = "Done: ${"%,d".format(gathered.size)} games from pages $from–${lastKnown.coerceAtMost(to ?: lastKnown)}."
            } catch (e: CancellationException) {
                keep()
                pagesNote = "Stopped: ${"%,d".format(gathered.size)} games so far."
            } catch (e: WebImport.WebError) {
                keep()
                problem = e.message.orEmpty()
                pagesNote = ""
            } catch (e: Exception) {
                keep()
                problem = "A page couldn't be read. Check your connection and try again."
                pagesNote = ""
            } finally {
                pagesJob = null
            }
        }
    }

    fun read() {
        if (busy || address.isBlank()) return
        busy = true
        problem = ""
        found = null
        progress = "Reading…"
        scope.launch {
            try {
                val result = WebImport.read(context, address) { message -> scope.launch { progress = message } }
                found = result
                pageFrom = "1"
                pageTo = result.paging?.last?.toString().orEmpty()
                pagesNote = ""
                mode = if (result.games.isNotEmpty()) "list" else "page"
                name = if (result.games.isNotEmpty()) result.name else ""
                source = WishImport.suggestSource(result.games.firstOrNull()?.url ?: result.page?.url.orEmpty())
                skipped = emptySet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: WebImport.WebError) {
                problem = e.message.orEmpty()
            } catch (e: Exception) {
                problem = "The page couldn't be read. Check the address and your connection."
            } finally {
                busy = false
            }
        }
    }
    // A page shared from the browser is read straight away.
    LaunchedEffect(Unit) { if (startAddress.isNotBlank()) read() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BQ.Shell2,
        contentColor = BQ.Ink,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("IMPORT FROM WEB")
            Hint(
                "Paste the address of a game's page or a list of games. Internet Archive collections and searches " +
                    "are read in full; on other sites the app looks for the games linked from the page, " +
                    "with or without pictures. " +
                    "You can also share a page here from your browser.",
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Web address") },
                singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(if (busy) "READING…" else "READ PAGE", kind = BtnKind.B, enabled = !busy && address.isNotBlank()) { read() }
                if (busy) Hint(progress, Modifier.weight(1f))
            }
            if (problem.isNotBlank()) Text(problem, color = BQ.CoolFg, fontSize = 13.sp)

            found?.let { result ->
                val page = result.page
                if (result.games.isNotEmpty() && page != null) {
                    ChipRow(
                        listOf("list" to "Games on this page ${"%,d".format(result.games.size)}", "page" to "Just this page"),
                        mode,
                    ) { mode = it }
                }
                val chosen: List<WebGame> =
                    if (mode == "list") result.games.filter { it.url !in skipped } else listOfNotNull(page)

                if (mode == "list") {
                    if (result.total > result.games.size) {
                        Hint("The site lists ${"%,d".format(result.total)}; the first ${"%,d".format(result.games.size)} were read.")
                    }
                    result.paging?.let { paging -> PagesPanel(paging, pageFrom, pageTo, pagesJob != null, pagesNote,
                        onFrom = { pageFrom = it }, onTo = { pageTo = it },
                        onRead = { readPages(result, paging) }, onStop = { pagesJob?.cancel() }) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("${"%,d".format(chosen.size)} SELECTED", Modifier.weight(1f))
                        TabChip("All", false) { skipped = emptySet() }
                        TabChip("None", false) { skipped = result.games.map { it.url }.toSet() }
                    }
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(10.dp)).background(BQ.Shell),
                    ) {
                        items(result.games, key = { it.url }) { g ->
                            FoundRow(g, ticked = g.url !in skipped) {
                                skipped = if (g.url in skipped) skipped - g.url else skipped + g.url
                            }
                        }
                    }
                } else if (page != null) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BQ.Shell)) {
                        FoundRow(page, ticked = true, onClick = null)
                    }
                    if (page.image.isBlank()) Hint("No picture was found on the page.")
                }

                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("Source") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Platform or list") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                Hint(
                    "The source gets a tile in Discover and its own place on the Spin wheel. " +
                        "In your wishlist the games are filed under the platform or list.",
                )
                // Games you already have: in your wishlist on the same platform, or in your games.
                // Worked out in the background once typing pauses: with big lists it takes a moment.
                val checked by produceState<Set<String>?>(null, chosen, name, vm.ref, vm.state.hiddenWish, vm.state.games) {
                    value = null
                    delay(400)
                    val ref = vm.ref
                    val st = vm.state
                    val games = chosen
                    val platform = name.trim()
                    value = withContext(Dispatchers.Default) {
                        val index = Duplicates.Index(ref.wishlist, st.hiddenWish, Duplicates.libraryTitles(st, ref))
                        games.filter { index.known(it.title, platform) }.map { it.url }.toSet()
                    }
                }
                val known = checked.orEmpty()
                if (checked == null) Hint("Checking for games you already have…")
                if (known.isNotEmpty()) {
                    CheckRow(
                        "Skip ${"%,d".format(known.size)} already in your wishlist or games",
                        "Same title on the same platform, or a game in your games list or history.",
                        skipKnown,
                    ) { skipKnown = !skipKnown }
                }
                val toAdd = if (skipKnown) chosen.filter { it.url !in known } else chosen
                // Games without a picture on the list usually have one on their own page.
                val noPicture = toAdd.filter { it.image.isBlank() }
                if (noPicture.isNotEmpty()) {
                    CheckRow(
                        "Fetch pictures from each game's page",
                        "${"%,d".format(noPicture.size)} game${if (noPicture.size == 1) " has" else "s have"} no picture on this list. " +
                            "The app visits each one after adding, about a second apiece; you can stop it from the wishlist.",
                        fetchAfter,
                    ) { fetchAfter = !fetchAfter }
                }
                PixelButton(
                    "ADD ${"%,d".format(toAdd.size)} TO WISHLIST", kind = BtnKind.A,
                    enabled = toAdd.isNotEmpty() && name.isNotBlank() && source.isNotBlank() && checked != null,
                ) {
                    vm.importWeb(source.trim(), name.trim(), toAdd)
                    if (fetchAfter && noPicture.isNotEmpty()) {
                        vm.fetchWishImages(noPicture.map { WebImport.gameId(it.url) })
                    }
                    onDismiss()
                }
            }
            PixelButton("CLOSE", onClick = onDismiss)
        }
    }
}

/** The list is split over pages: choose which to read, and follow along as they're read. */
@Composable
private fun PagesPanel(
    paging: Paging,
    from: String,
    to: String,
    running: Boolean,
    note: String,
    onFrom: (String) -> Unit,
    onTo: (String) -> Unit,
    onRead: () -> Unit,
    onStop: () -> Unit,
) {
    Panel {
        Text("This list has more pages", color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Hint(
            "This is page ${paging.current}; pages up to ${paging.last} are linked, and there may be more. " +
                "Pages are read one after another with a short pause, and reading stops when a page has no new games.",
        )
        Hint("Address pattern: " + paging.template.replace("{n}", if (paging.step == 1) "(page)" else "(item ${paging.step}×page)"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = from, onValueChange = { v -> onFrom(v.filter(Char::isDigit).take(4)) },
                label = { Text("From page") }, singleLine = true, enabled = !running,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = bqFieldColors(), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = to, onValueChange = { v -> onTo(v.filter(Char::isDigit).take(4)) },
                label = { Text("To page") }, placeholder = { Text("to the end") }, singleLine = true, enabled = !running,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = bqFieldColors(), modifier = Modifier.weight(1f),
            )
        }
        Hint("Leave To page empty to keep going until the pages run out (at most ${WebImport.MAX_PAGES}).")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (running) PixelButton("STOP", onClick = onStop)
            else PixelButton(
                if (to.isBlank()) "READ ALL PAGES" else "READ PAGES ${from.ifBlank { "1" }}–$to",
                kind = BtnKind.B, onClick = onRead,
            )
            if (note.isNotBlank()) Hint(note, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FoundRow(g: WebGame, ticked: Boolean, onClick: (() -> Unit)?) {
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onClick != null) {
            Text(
                if (ticked) "☑" else "☐",
                color = if (ticked) BQ.B else BQ.Muted, fontSize = 20.sp,
            )
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(5.dp)).background(BQ.Edge)) {
            if (g.image.isNotBlank()) {
                AsyncImage(
                    model = g.image, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                g.title, color = if (ticked) BQ.Ink else BQ.Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (g.genres.isNotEmpty()) {
                Text(g.genres.joinToString(", "), color = BQ.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
