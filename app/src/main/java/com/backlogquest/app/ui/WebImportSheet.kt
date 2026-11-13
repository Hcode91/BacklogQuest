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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.WebFound
import com.backlogquest.app.data.WebGame
import com.backlogquest.app.data.WebImport
import com.backlogquest.app.data.WishImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
                val index = remember(vm.ref, vm.state.hiddenWish, vm.state.games) { vm.duplicateIndex() }
                val known = remember(chosen, name, index) {
                    chosen.filter { index.known(it.title, name.trim()) }.map { it.url }.toSet()
                }
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
                    enabled = toAdd.isNotEmpty() && name.isNotBlank() && source.isNotBlank(),
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
