package com.backlogquest.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.WishItem
import com.backlogquest.app.discover.Consoles
import com.backlogquest.app.discover.DownloadRecord
import com.backlogquest.app.discover.Game
import com.backlogquest.app.discover.GameQuery
import com.backlogquest.app.discover.PagedGameProvider
import com.backlogquest.app.discover.ShelfDownloads
import com.backlogquest.app.discover.ShelfViewModel
import com.backlogquest.app.discover.Sort
import com.backlogquest.app.discover.UrlSafety
import com.backlogquest.app.discover.cataloguePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where you are inside the Discover tab: a section (Browse, Search, Saved…) and
 * whatever you've opened on top of it. It lives in [BacklogApp], so switching to
 * another tab and back doesn't lose your place.
 *
 * Places are plain strings: "home", "console/GBA", "catalogue/GBA/hub", "game/<id>".
 */
class DiscoverNav(start: List<String> = listOf(HOME)) {
    val stack = mutableStateListOf<String>().apply { addAll(start.ifEmpty { listOf(HOME) }) }
    val current: String get() = stack.last()
    val section: String get() = stack.first()
    val canGoBack: Boolean get() = stack.size > 1

    fun open(place: String) {
        if (current != place) stack.add(place)
    }

    /** Steps back one place and returns the place that was left. */
    fun back(): String? = if (canGoBack) stack.removeAt(stack.lastIndex) else null

    fun switchSection(section: String) {
        stack.clear()
        stack.add(section)
    }

    companion object {
        const val HOME = "home"
        const val SEARCH = "search"
        const val SAVED = "saved"
        const val DOWNLOADS = "downloads"
        const val SETTINGS = "settings"

        val SECTIONS = listOf(
            HOME to "Browse", SEARCH to "Search", SAVED to "Saved",
            DOWNLOADS to "Downloads", SETTINGS to "Settings",
        )

        val Saver = listSaver<DiscoverNav, String>(save = { it.stack.toList() }, restore = { DiscoverNav(it) })
    }
}

/**
 * The Discover tab: browse homebrew and free-game catalogues by console.
 * This is the RetroShelf app, merged in and dressed in the Backlog Quest look.
 */
@Composable
fun DiscoverScreen(
    nav: DiscoverNav,
    saved: SaveableStateHolder,
    toast: (String) -> Unit,
    onAddToBacklog: (Game) -> Unit,
    bq: BacklogViewModel,
    onOpenWish: (String) -> Unit,
) {
    // Created the first time the tab is opened, then kept for as long as the app is open.
    val vm: ShelfViewModel = viewModel()
    LaunchedEffect(vm) { vm.messages.collect { toast(it) } }

    val goBack: () -> Unit = { nav.back()?.let { saved.removeState(it) } }
    BackHandler(enabled = nav.canGoBack, onBack = goBack)

    DiscoverTheme {
        Column(Modifier.fillMaxSize()) {
            DiscoverHeader(nav, vm, goBack)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val place = nav.current
                saved.SaveableStateProvider(place) {
                    when {
                        place == DiscoverNav.HOME -> HomeScreen(vm, nav, bq)
                        place.startsWith("mine/") -> MySourceScreen(Uri.decode(place.removePrefix("mine/")), bq, onOpenWish)
                        place == DiscoverNav.SEARCH -> SearchScreen(vm, nav)
                        place == DiscoverNav.SAVED -> SavedScreen(vm, nav)
                        place == DiscoverNav.DOWNLOADS -> DownloadsScreen(vm)
                        place == DiscoverNav.SETTINGS -> DiscoverSettingsScreen(vm)
                        place.startsWith("console/") -> SourcesScreen(place.removePrefix("console/"), vm, nav)
                        place.startsWith("catalogue/") -> {
                            val (console, provider) = place.removePrefix("catalogue/").split("/", limit = 2)
                            CatalogueScreen(console, provider, vm, nav)
                        }
                        place.startsWith("game/") -> DetailsScreen(place.removePrefix("game/"), vm, onAddToBacklog)
                    }
                }
            }
        }
    }
}

/** Fills in the Material colours the Discover widgets use, so menus, switches and bars match the console look. */
@Composable
private fun DiscoverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            tertiary = BQ.Ok,
            error = BQ.CoolFg,
            primaryContainer = BQ.Edge,
            onPrimaryContainer = BQ.Ink,
            secondaryContainer = BQ.Edge,
            onSecondaryContainer = BQ.Ink,
            surfaceContainerLowest = BQ.Shell,
            surfaceContainerLow = BQ.Shell2,
            surfaceContainer = BQ.Shell2,
            surfaceContainerHigh = BQ.Shell2,
            surfaceContainerHighest = BQ.Edge,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides BQ.Ink, content = content)
    }
}

@Composable
private fun DiscoverHeader(nav: DiscoverNav, vm: ShelfViewModel, goBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (nav.canGoBack) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton("◀ BACK", onClick = goBack)
                SectionTitle(placeTitle(nav.current, vm), Modifier.weight(1f))
            }
        } else {
            SectionTitle("DISCOVER")
            ChipRow(DiscoverNav.SECTIONS, nav.section) { nav.switchSection(it) }
        }
    }
}

private fun placeTitle(place: String, vm: ShelfViewModel): String = when {
    place.startsWith("console/") ->
        Consoles.all.firstOrNull { it.id == place.removePrefix("console/") }?.name?.uppercase() ?: "SOURCES"
    place.startsWith("catalogue/") ->
        vm.graph.registry.providers.firstOrNull { it.id == place.substringAfterLast('/') }?.name?.uppercase() ?: "CATALOGUE"
    place.startsWith("mine/") -> Uri.decode(place.removePrefix("mine/")).uppercase()
    else -> "GAME DETAILS"
}

private fun openGame(game: Game, vm: ShelfViewModel, nav: DiscoverNav) {
    vm.view(game)
    nav.open("game/${game.id}")
}

private val gridPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)

// ───────────────────────── Browse ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(vm: ShelfViewModel, nav: DiscoverNav, bq: BacklogViewModel) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val mine = remember(bq.ref, bq.state.hiddenWish) { bq.mySources() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp),
        contentPadding = gridPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Intro("Small games. New worlds.", "Choose a console, pick a community source, and find something new for the backlog.")
        }
        // Your own sources: lists you've imported from CSV files or web pages.
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("MY SOURCES", Modifier.padding(top = 8.dp)) }
        items(mine, key = { "mine-${it.first}" }) { (name, games) ->
            Card(onClick = { nav.open("mine/" + Uri.encode(name)) }) {
                Text(name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${"%,d".format(games.size)} game${if (games.size == 1) "" else "s"}", color = BQ.B, fontFamily = BQ.Mono, fontSize = 12.sp)
                val platforms = games.flatMap { it.platforms }.distinct()
                if (platforms.isNotEmpty()) {
                    Text(platforms.joinToString(", "), color = BQ.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mine.isEmpty()) Hint("Add your own: import a CSV list or a web page and it appears here as a source.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("IMPORT CSV") { bq.csvImportOpen = true }
                    PixelButton("IMPORT FROM WEB") { bq.webImport = "" }
                    if (mine.isNotEmpty()) PixelButton("FIND DUPLICATES") { bq.duplicatesOpen = true }
                }
            }
        }
        Consoles.all.groupBy { it.family }.forEach { (family, consoles) ->
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(family.uppercase(), Modifier.padding(top = 8.dp)) }
            items(consoles, key = { it.id }) { console ->
                val count = vm.graph.registry.forConsole(console.id).size
                Card(onClick = { nav.open("console/${console.id}") }) {
                    Text(console.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (count) {
                            0 -> "No sources yet"
                            1 -> "1 source"
                            else -> "$count sources"
                        },
                        color = if (count == 0) BQ.Muted else BQ.B, fontFamily = BQ.Mono, fontSize = 12.sp,
                    )
                }
            }
        }
        if (recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("RECENTLY VIEWED", Modifier.padding(top = 8.dp)) }
            items(recent.take(8), key = { "recent-${it.id}" }) { game -> GameCard(game, false) { openGame(game, vm, nav) } }
        }
    }
}

@Composable
private fun SourcesScreen(console: String, vm: ShelfViewModel, nav: DiscoverNav) {
    val providers = vm.graph.registry.forConsole(console)
    val disabled by vm.disabled.collectAsStateWithLifecycle()
    val statuses by vm.status.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = gridPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (providers.isEmpty()) item {
            EmptyState("No source for this console yet", "A verified homebrew catalogue can be added for it later.")
        }
        providers.groupBy { it.section }.forEach { (section, sources) ->
            item { SectionTitle(section.uppercase()) }
            items(sources, key = { it.id }) { p ->
                val off = p.id in disabled
                Card(onClick = { if (!off) nav.open("catalogue/$console/${p.id}") }) {
                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(p.description, color = BQ.Muted, fontSize = 13.sp)
                    Text(
                        if (off) "Turned off in Discover settings" else statuses[p.id] ?: p.connectionHint,
                        color = if (off) BQ.Muted else BQ.B, fontFamily = BQ.Mono, fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

// ───────────────────────── Catalogue ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogueScreen(console: String, provider: String, vm: ShelfViewModel, nav: DiscoverNav) {
    LaunchedEffect(provider) { vm.load(provider) }
    val state by vm.catalogue.collectAsStateWithLifecycle()
    val source = vm.graph.registry.get(provider)
    var search by rememberSaveable(console, provider) { mutableStateOf("") }
    var genre by rememberSaveable(console, provider) { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(Sort.TITLE) }
    var list by rememberSaveable { mutableStateOf(false) }
    var shown by rememberSaveable(console, provider) { mutableIntStateOf(PAGE) }

    // Everything that matches, worked out on the phone from the saved catalogue (no extra requests).
    val matches = remember(state.games, search, genre, sort, console) {
        val all = mutableListOf<Game>()
        var page = 0
        do {
            val chunk = cataloguePage(state.games, GameQuery(console, search, genre, sort, page++, 100))
            all.addAll(chunk.games)
        } while (chunk.hasMore)
        all.toList()
    }
    val genres = remember(state.games, console) {
        state.games.filter { console in it.consoles }.flatMap { it.genres }.distinct().sorted()
    }
    val visible = matches.take(shown)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; shown = PAGE },
                placeholder = { Text(if (source is PagedGameProvider) "Search saved listings" else "Search this catalogue") },
                singleLine = true,
                colors = bqFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Choice("Sort: ${sort.name.lowercase()}", Sort.entries.map { it.name.lowercase() }) {
                    sort = Sort.valueOf(it.uppercase()); shown = PAGE
                }
                Box(Modifier.weight(1f)) {
                    Choice(genre.ifBlank { "All genres" }, listOf("All genres") + genres) {
                        genre = if (it == "All genres") "" else it; shown = PAGE
                    }
                }
                TabChip(if (list) "▦ Grid" else "≡ List", false) { list = !list }
            }
            if (source is PagedGameProvider) {
                Hint("${state.games.size} saved${state.total?.let { " of $it listings" }.orEmpty()}. Search and filters use saved entries.")
            }
            if (state.offline) Text("Offline · showing the saved catalogue. Pull down to retry.", color = BQ.B, fontSize = 13.sp)
            state.error?.let {
                Text(it, color = BQ.CoolFg, fontSize = 13.sp)
                PixelButton("RETRY", kind = BtnKind.A) { vm.load(provider, true) }
            }
        }
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { shown = PAGE; vm.load(provider, true) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyVerticalGrid(
                columns = if (list) GridCells.Fixed(1) else GridCells.Adaptive(148.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.loading) item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        "Connecting to catalogue",
                        if (provider == "hub") "The first GBA sync can take a minute. After that it's saved on your phone."
                        else "Fetching this source. Saved entries are reused next time.",
                    )
                }
                if (!state.loading && state.error == null && visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("No games found", "Try another search, genre or source.")
                }
                items(visible, key = { it.id }) { game -> GameCard(game, list) { openGame(game, vm, nav) } }
                if (visible.size < matches.size) item(span = { GridItemSpan(maxLineSpan) }) {
                    PixelButton("LOAD MORE · ${visible.size} OF ${matches.size}", Modifier.fillMaxWidth()) { shown += PAGE }
                }
                if (!state.loading && state.nextPage != null) item(span = { GridItemSpan(maxLineSpan) }) {
                    PixelButton(
                        if (state.loadingMore) "LOADING OLDER LISTINGS…" else "LOAD OLDER LISTINGS FROM WEBSITE",
                        Modifier.fillMaxWidth(), enabled = !state.loadingMore,
                    ) { vm.loadMore(provider) }
                }
            }
        }
    }
}

private const val PAGE = 24

@Composable
private fun SearchScreen(vm: ShelfViewModel, nav: DiscoverNav) {
    val disabled by vm.disabled.collectAsStateWithLifecycle()
    val choices = vm.graph.registry.providers
        .filter { it.id !in disabled && it.nativeBrowsing }
        .flatMap { p -> p.supportedConsoles.map { c -> Triple("${p.name} · $c", c, p.id) } }
    var selection by rememberSaveable { mutableStateOf("") }
    val picked = choices.firstOrNull { it.first == selection } ?: choices.firstOrNull()
    if (picked == null) {
        EmptyState("No sources turned on", "Turn a source on in Discover settings to search its catalogue.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Hint("Searching")
            Choice(picked.first, choices.map { it.first }) { selection = it }
        }
        CatalogueScreen(picked.second, picked.third, vm, nav)
    }
}

@Composable
private fun SavedScreen(vm: ShelfViewModel, nav: DiscoverNav) {
    val games by vm.favorites.collectAsStateWithLifecycle()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp),
        contentPadding = gridPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (games.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyState("Nothing saved yet", "Tap ♥ SAVE on a game's page to keep it here.")
        }
        items(games, key = { it.id }) { g -> GameCard(g, false) { openGame(g, vm, nav) } }
    }
}

// ───────────────────────── Game details ─────────────────────────

@Composable
private fun DetailsScreen(id: String, vm: ShelfViewModel, onAddToBacklog: (Game) -> Unit) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var detailsLoading by remember(id) { mutableStateOf(false) }
    var detailRefresh by remember(id) { mutableIntStateOf(0) }
    var game by remember(id) { mutableStateOf<Game?>(null) }
    LaunchedEffect(id, detailRefresh) {
        // Show what's saved straight away, then fetch the full page if this source has one.
        val cached = vm.graph.db.shelf().game(id)?.let(vm.graph.repository::decode)
            ?: vm.catalogue.value.games.firstOrNull { it.id == id }
            ?: vm.favorites.value.firstOrNull { it.id == id }
            ?: vm.recent.value.firstOrNull { it.id == id }
            ?: return@LaunchedEffect
        game = cached
        vm.view(cached)
        detailsLoading = vm.graph.registry.get(cached.provider).refreshDetails
        try {
            val full = vm.graph.repository.details(cached, refresh = detailRefresh > 0)
            game = full
            vm.view(full)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            vm.messages.emit("Game details unavailable. Showing saved information.")
        } finally {
            detailsLoading = false
        }
    }
    val context = LocalContext.current
    val g = game
    if (g == null) {
        EmptyState("Loading game", "If this entry was removed from the saved catalogue, go back and reopen it.")
        return
    }
    val source = vm.graph.registry.get(g.provider)
    val isSaved = favorites.any { it.id == g.id }
    LazyColumn(contentPadding = gridPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (detailsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = BQ.B, trackColor = BQ.Edge) }
        item { Artwork(g.artwork, g.title, Modifier.fillMaxWidth().height(240.dp)) }
        item { Text(g.title, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton(if (isSaved) "♥ SAVED" else "♡ SAVE", kind = if (isSaved) BtnKind.A else BtnKind.PLAIN) { vm.favorite(g) }
                if (source.refreshDetails) PixelButton("↻ REFRESH", enabled = !detailsLoading) { detailRefresh++ }
            }
        }
        item { PixelButton("+ ADD TO BACKLOG", Modifier.fillMaxWidth(), kind = BtnKind.B) { onAddToBacklog(g) } }
        item {
            Hint(
                listOfNotNull(
                    g.developer?.let { "Developer: $it" }, g.publisher?.let { "Publisher: $it" },
                    g.release?.let { "Release: $it" }, "Platforms: ${g.consoles.joinToString()}",
                    g.license?.let { "Source license: $it" },
                ).joinToString("\n"),
            )
        }
        item { Text(g.description.ifBlank { "No description provided by this source." }, fontSize = 15.sp) }
        if (g.screenshots.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(g.screenshots) { shot -> Artwork(shot, g.title, Modifier.width(280.dp).height(190.dp)) }
            }
        }
        item { SectionTitle("AVAILABLE FILES") }
        if (g.downloads.isEmpty()) item { Hint("No direct files supplied. Open the original website.") }
        items(g.downloads) { file ->
            Panel {
                Text(file.name, fontWeight = FontWeight.Bold)
                Hint("${file.format} · ${file.size?.let { "${it / 1024} KiB" } ?: "Size not provided"}")
                Hint(file.url)
                if (file.authorization != null) {
                    Hint("Distribution: ${file.authorization}")
                    PixelButton("⬇ DOWNLOAD", kind = BtnKind.B) { vm.download(g, file) }
                } else {
                    Hint("Distribution rights not reviewed. Check the publisher's terms on the original website.")
                }
            }
        }
        item { PixelButton("OPEN ORIGINAL WEBSITE ↗", Modifier.fillMaxWidth()) { openWebsite(context, g.sourceUrl) } }
        item { Hint("${source.attribution}. Backlog Quest does not install or run downloaded files.") }
        source.metadataLicenseUrl?.let { license ->
            item { TabChip("Metadata license: CC BY-SA 4.0 ↗", false) { openWebsite(context, license) } }
        }
    }
}

// ───────────────────────── Downloads & settings ─────────────────────────

@Composable
private fun DownloadsScreen(vm: ShelfViewModel) {
    val rows by vm.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var export by remember { mutableStateOf<DownloadRecord?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { target -> export?.let { vm.export(it, target) } }
        export = null
    }
    LazyColumn(contentPadding = gridPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (rows.isEmpty()) item {
            EmptyState("No downloads yet", "Files you download from Discover appear here with their source and progress.")
        }
        items(rows, key = { it.id }) { row ->
            Panel {
                Text(row.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Hint(row.filename)
                LinearProgressIndicator(
                    progress = { row.progress / 100f }, modifier = Modifier.fillMaxWidth(),
                    color = BQ.B, trackColor = BQ.Edge,
                )
                Text("${row.state} · ${row.progress}%", fontFamily = BQ.Mono, fontSize = 13.sp)
                Hint(row.destination)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (row.state) {
                        "Complete" -> PixelButton("SAVE A COPY…") { export = row; launcher.launch(row.filename) }
                        "Queued", "Downloading", "Paused" -> PixelButton("CANCEL") { vm.cancel(row) }
                        else -> PixelButton("RETRY") { vm.retry(row) }
                    }
                    PixelButton("SOURCE ↗") { openWebsite(context, row.url) }
                }
            }
        }
    }
}

@Composable
private fun DiscoverSettingsScreen(vm: ShelfViewModel) {
    val public by vm.publicDownloads.collectAsStateWithLifecycle()
    val disabled by vm.disabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LazyColumn(contentPadding = gridPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Panel {
                SectionTitle("DOWNLOADS")
                if (ShelfDownloads.publicFolderAvailable) {
                    ToggleRow("Save to Downloads/BacklogQuest", public, vm::setPublic)
                    Hint("When off, files go to app storage and are removed if you uninstall. Use Save a copy in Downloads to put a file anywhere.")
                } else {
                    Hint("On this Android version files go to app storage. Use Save a copy in Downloads to put a file anywhere.")
                }
            }
        }
        item {
            Panel {
                SectionTitle("SAVED DATA")
                Hint("Catalogues and opened game pages stay on your phone until you refresh or clear them. Pull down in a catalogue to check for updates.")
                PixelButton("CLEAR CATALOGUES", onClick = vm::clearCatalogue)
                Hint("Keeps your saved and recently viewed games.")
                PixelButton("CLEAR IMAGES") {
                    scope.launch {
                        withContext(Dispatchers.IO) { context.imageLoader.diskCache?.clear() }
                        context.imageLoader.memoryCache?.clear()
                        vm.messages.emit("Image cache cleared")
                    }
                }
                Hint("Also clears box art in the rest of the app. It downloads again when needed.")
            }
        }
        item {
            Panel {
                SectionTitle("SOURCES")
                vm.graph.registry.providers.forEach { p -> ToggleRow(p.name, p.id !in disabled) { vm.enable(p.id, it) } }
            }
        }
        item {
            Panel {
                SectionTitle("ABOUT DISCOVER")
                Hint("A browser for homebrew and free-game catalogues, merged in from RetroShelf. Game data and images belong to their authors. No games are bundled, and only individually reviewed files get a direct download button.")
                vm.graph.registry.providers.forEach { p ->
                    Hint(p.attribution)
                    TabChip("${p.name} ↗", false) { openWebsite(context, p.website) }
                }
            }
        }
    }
}

// ───────────────────────── My sources ─────────────────────────

/** One of your imported sources: its games as tiles, with platform, genre and search filters. */
@Composable
private fun MySourceScreen(source: String, bq: BacklogViewModel, onOpenWish: (String) -> Unit) {
    val games = remember(bq.ref, bq.state.hiddenWish, source) {
        bq.mySources().firstOrNull { it.first == source }?.second.orEmpty().sortedBy { it.title.lowercase() }
    }
    var platform by rememberSaveable(source) { mutableStateOf("") }
    var genre by rememberSaveable(source) { mutableStateOf("") }
    var search by rememberSaveable(source) { mutableStateOf("") }
    var shown by rememberSaveable(source) { mutableIntStateOf(PAGE) }
    val platforms = remember(games) { games.flatMap { it.platforms }.distinct().sorted() }
    val onPlatform = remember(games, platform) { if (platform.isBlank()) games else games.filter { platform in it.platforms } }
    // Genres on the chosen platform, most common first.
    val genres = remember(onPlatform) {
        onPlatform.flatMap { it.genres }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
    }
    val matches = remember(onPlatform, genre, search) {
        onPlatform.filter { (genre.isBlank() || genre in it.genres) && (search.isBlank() || it.title.contains(search.trim(), ignoreCase = true)) }
    }
    val visible = matches.take(shown)
    val needPictures = remember(matches, bq.wishImages.size) {
        matches.filter { it.url.isNotBlank() && it.id !in bq.wishImages }.map { it.id }
    }

    if (games.isEmpty()) {
        EmptyState("Nothing here", "This source has no games left. Import a list to add some.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; shown = PAGE },
                placeholder = { Text("Search $source") },
                singleLine = true,
                colors = bqFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (platforms.size > 1) {
                    Choice(platform.ifBlank { "All platforms" }, listOf("All platforms") + platforms) {
                        platform = if (it == "All platforms") "" else it; genre = ""; shown = PAGE
                    }
                }
                if (genres.isNotEmpty()) {
                    Choice(genre.ifBlank { "All genres" }, listOf("All genres") + genres) {
                        genre = if (it == "All genres") "" else it; shown = PAGE
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint("${"%,d".format(matches.size)} game${if (matches.size == 1) "" else "s"}", Modifier.weight(1f))
                if (bq.imageBusy) {
                    PixelButton("STOP", kind = BtnKind.A) { bq.stopImages() }
                } else if (needPictures.isNotEmpty()) {
                    PixelButton("FETCH ${"%,d".format(needPictures.size)} PICTURES", kind = BtnKind.B) { bq.fetchWishImages(needPictures) }
                }
            }
            if (bq.imageMessage.isNotBlank()) Hint(bq.imageMessage)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(148.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState("No games found", "Try another search, platform or genre.")
            }
            items(visible, key = { it.id }) { w -> MineCard(w, bq.wishImage(w.id)) { onOpenWish(w.id) } }
            if (visible.size < matches.size) item(span = { GridItemSpan(maxLineSpan) }) {
                PixelButton("LOAD MORE · ${visible.size} OF ${matches.size}", Modifier.fillMaxWidth()) { shown += PAGE }
            }
        }
    }
}

@Composable
private fun MineCard(w: WishItem, picture: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(BQ.Shell2).border(1.dp, BQ.Edge, shape).clickable(onClick = onClick)) {
        Artwork(picture, w.title, Modifier.fillMaxWidth().height(150.dp))
        Column(Modifier.padding(12.dp)) {
            Text(w.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (w.platforms.isNotEmpty()) {
                Text(w.platforms.joinToString(", "), color = BQ.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            w.genres.firstOrNull()?.let {
                Text(it, color = BQ.B, fontFamily = BQ.Mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ───────────────────────── Shared pieces ─────────────────────────

@Composable
private fun Intro(title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(BQ.Lcd).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = BQ.LcdInk, fontFamily = BQ.Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text, color = BQ.LcdDim, fontFamily = BQ.Mono, fontSize = 13.sp)
    }
}

/** A tappable panel in the console look. */
@Composable
private fun Card(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BQ.Shell2)
            .border(1.dp, BQ.Edge, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun Artwork(url: String?, title: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(BQ.Edge), contentAlignment = Alignment.Center) {
        // Shown behind the picture, so it's what you see when there's no artwork.
        Text(initialsOf(title), color = BQ.Muted, fontFamily = BQ.Mono, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url, contentDescription = "$title artwork",
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun GameCard(game: Game, list: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val frame = Modifier.fillMaxWidth().clip(shape).background(BQ.Shell2).border(1.dp, BQ.Edge, shape).clickable(onClick = onClick)
    if (list) {
        Row(frame, verticalAlignment = Alignment.CenterVertically) {
            Artwork(game.artwork, game.title, Modifier.size(104.dp))
            Column(Modifier.weight(1f).padding(12.dp)) { GameText(game) }
        }
    } else {
        Column(frame) {
            Artwork(game.artwork, game.title, Modifier.fillMaxWidth().height(150.dp))
            Column(Modifier.padding(12.dp)) { GameText(game) }
        }
    }
}

@Composable
private fun GameText(game: Game) {
    Text(game.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    game.developer?.let { Text(it, color = BQ.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    if (game.description.isNotBlank()) {
        Text(game.description, color = BQ.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Text(
        game.genres.firstOrNull() ?: game.consoles.joinToString(),
        color = BQ.B, fontFamily = BQ.Mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptyState(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title.uppercase())
        Hint(description)
    }
}

/** A chip that opens a menu of choices. */
@Composable
private fun Choice(label: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TabChip("$label ▾", false) { expanded = true }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { value ->
                DropdownMenuItem(text = { Text(value, color = BQ.Ink) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = BQ.B, checkedThumbColor = BQ.Shell),
        )
    }
}

private fun openWebsite(context: Context, url: String) {
    if (!UrlSafety.valid(url)) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
    }
}
