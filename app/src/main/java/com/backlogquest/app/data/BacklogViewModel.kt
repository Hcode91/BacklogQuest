package com.backlogquest.app.data

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class BacklogViewModel(app: Application) : AndroidViewModel(app) {
    private val dir: File = app.filesDir
    private val saveLock = Mutex()

    var state by mutableStateOf(AppState())
        private set
    var ref by mutableStateOf(RefData.EMPTY)
        private set
    /** Your games as they are in the data file, for "replace my games". */
    var fileState by mutableStateOf<ImportedState?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var syncing by mutableStateOf(false)
        private set
    var syncMessage by mutableStateOf("")
        private set
    /** A message shown briefly at the top of the screen. */
    var toast by mutableStateOf<String?>(null)
    var priceBusy by mutableStateOf(false)
        private set
    var priceMessage by mutableStateOf("")
        private set

    /** Every wishlist title: your own wishlist games plus the data file's wishlist. */
    fun wishlistTitles(): List<String> =
        state.games.filter { it.status == "wishlist" }.map { it.title } +
            ref.wishlist.filter { it.id !in state.hiddenWish }.map { it.title }

    /** Fetches current prices for the given titles from IsThereAnyDeal and saves them. */
    fun updatePrices(titles: List<String>) {
        if (priceBusy || titles.isEmpty()) return
        priceBusy = true
        priceMessage = "Getting prices…"
        viewModelScope.launch {
            try {
                val found = Prices.lookup(state.itadKey, titles) { m -> viewModelScope.launch { priceMessage = m } }
                update { it.copy(prices = it.prices + found) }
                priceMessage = if (titles.size == 1) {
                    if (found.isEmpty()) "IsThereAnyDeal doesn't sell this game (it covers PC stores)." else ""
                } else "Prices found for ${found.size} of ${titles.distinct().size} games."
            } catch (e: Prices.PriceError) {
                priceMessage = e.message ?: "Couldn't get prices."
            } catch (e: Exception) {
                priceMessage = "Couldn't get prices. Check your connection and try again."
            } finally {
                priceBusy = false
            }
        }
    }

    fun priceFor(title: String): Price? = state.prices[normTitle(title)]

    // ---------------- Playing games ----------------

    /** A finished play session waiting for you to confirm the time. */
    class SessionResult(val gameId: String, val title: String, val minutes: Int, val measured: Boolean)

    var sessionResult by mutableStateOf<SessionResult?>(null)
    private var pendingPlay: String? = null
    private val sessionPrefs get() = getApplication<Application>().getSharedPreferences("sessions", 0)

    /** Starts a game and remembers the session so its play time can be added when you're back. */
    fun play(gameId: String) {
        if (!loaded) {
            pendingPlay = gameId
            return
        }
        val g = state.games.firstOrNull { it.id == gameId }
        val l = g?.launch
        if (g == null || l == null) {
            showToast("Set up how to start this game first")
            return
        }
        try {
            val pkg = Launcher.start(getApplication(), l)
            sessionPrefs.edit()
                .putString("game", gameId).putString("pkg", pkg).putLong("start", System.currentTimeMillis())
                .apply()
        } catch (e: Launcher.LaunchError) {
            showToast(e.message ?: "Couldn't start the game")
        } catch (e: Exception) {
            showToast("Couldn't start the game")
        }
    }

    /** Called when the app comes back to the front: works out how long you played. */
    fun checkSession() {
        val gameId = sessionPrefs.getString("game", null) ?: return
        val pkg = sessionPrefs.getString("pkg", "").orEmpty()
        val start = sessionPrefs.getLong("start", 0L)
        val now = System.currentTimeMillis()
        if (now - start < 60_000) return // just bounced back; keep waiting
        sessionPrefs.edit().clear().apply()
        val measuredMs = PlayTime.foregroundMs(getApplication(), pkg, start, now)
        val ms = measuredMs ?: (now - start)
        val minutes = (ms / 60_000).toInt()
        if (minutes < 1) return
        val title = state.games.firstOrNull { it.id == gameId }?.title ?: return
        sessionResult = SessionResult(gameId, title, minutes, measuredMs != null)
    }

    fun addPlayTime(gameId: String, minutes: Int) {
        update { s ->
            s.copy(games = s.games.map { g ->
                if (g.id == gameId) g.copy(hours = Math.round((g.hours + minutes / 60.0) * 10) / 10.0) else g
            })
        }
        sessionResult = null
    }

    /** Fills in hours from Steam for your games (keeps whichever is higher). */
    fun syncSteamHours() {
        viewModelScope.launch {
            try {
                val found = PlayTime.steamHours(state)
                var changed = 0
                update { s ->
                    s.copy(games = s.games.map { g ->
                        val h = found[normTitle(g.title)]
                        if (h != null && h > g.hours + 0.05) {
                            changed++
                            g.copy(hours = Math.round(h * 10) / 10.0)
                        } else g
                    })
                }
                showToast("STEAM PLAY TIME\nUpdated $changed game${if (changed == 1) "" else "s"}")
            } catch (e: PlayTime.PlayTimeError) {
                showToast(e.message ?: "Couldn't reach Steam")
            } catch (e: Exception) {
                showToast("Couldn't reach Steam")
            }
        }
    }

    // ---------------- Wishlist games imported from CSV lists ----------------

    /** What the data file gave, before imported CSV lists are added on top. */
    private var fileRef = RefData.EMPTY

    /** The CSV lists you've imported, one per platform. */
    var csvLists by mutableStateOf<List<CsvList>>(emptyList())
        private set

    /** True while the Import CSV sheet is open (it can be opened from Games or Discover). */
    var csvImportOpen by mutableStateOf(false)

    /** Your imported sources, with their games, biggest first. */
    fun mySources(): List<Pair<String, List<WishItem>>> = WishImport.sources(ref.wishlist, state.hiddenWish)

    /** True while the Duplicates sheet is open. */
    var duplicatesOpen by mutableStateOf(false)

    /** For checking games about to be imported against your wishlist and games. */
    fun duplicateIndex(): Duplicates.Index =
        Duplicates.Index(ref.wishlist, state.hiddenWish, Duplicates.libraryTitles(state, ref))

    /** Deletes imported games (from every list they're in) along with their pictures. */
    fun removeImported(ids: Set<String>) {
        if (ids.isEmpty()) return
        csvLists = csvLists.map { l -> l.copy(games = l.games.filter { it.id !in ids }) }.filter { it.games.isNotEmpty() }
        ids.forEach { wishImages.remove(it) }
        rebuildRef()
        saveImports()
    }

    /** Pictures found for imported games, by game id. */
    val wishImages = mutableStateMapOf<String, String>()

    var imageBusy by mutableStateOf(false)
        private set
    var imageMessage by mutableStateOf("")
        private set
    private var imageJob: Job? = null

    /** The picture for a wishlist game, or "" when there isn't one (yet). */
    fun wishImage(id: String): String = wishImages[id].orEmpty().takeIf { it != WishImport.NO_IMAGE }.orEmpty()

    private var refJob: Job? = null

    /**
     * Puts the wishlist back together after imported lists change. With big lists
     * that takes a moment, so it's done in the background; a newer change cancels
     * an older one that hasn't finished.
     */
    private fun rebuildRef() {
        val lists = csvLists
        val file = fileRef
        refJob?.cancel()
        refJob = viewModelScope.launch {
            val items = withContext(Dispatchers.Default) { WishImport.wishItems(lists) + file.wishlist }
            ref = RefData(file.history, items, file.exportedAt)
        }
    }

    /** Saves happen one at a time, in the order they were asked for. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val saver = Dispatchers.IO.limitedParallelism(1)

    private fun saveLists() {
        val lists = csvLists
        viewModelScope.launch(saver) { WishImport.saveLists(dir, lists) }
    }

    private fun saveImages() {
        val images = wishImages.toMap()
        viewModelScope.launch(saver) { WishImport.saveImages(dir, images) }
    }

    private fun saveImports() {
        saveLists()
        saveImages()
    }

    /** What went wrong the last time the app closed unexpectedly, if it did. */
    var lastCrash by mutableStateOf<String?>(null)
        private set

    fun dismissCrash() {
        lastCrash = null
        viewModelScope.launch(Dispatchers.IO) { CrashLog.clear(dir) }
    }

    fun importCsv(lists: List<CsvList>) {
        csvLists = WishImport.merge(csvLists, lists)
        rebuildRef()
        saveImports()
        val games = lists.sumOf { it.games.size }
        val where = lists.map { WishImport.sourceOf(it) }.distinct().joinToString(", ")
        showToast("IMPORTED ${"%,d".format(games)} GAME${if (games == 1) "" else "S"}\n$where")
    }

    /**
     * The address waiting in the Import from web sheet: null when the sheet is
     * closed, "" when it was opened by hand, or a page shared from a browser.
     */
    var webImport by mutableStateOf<String?>(null)

    /** Adds games found on a web page. Unlike a CSV, they join a list of the same name rather than replacing it. */
    fun importWeb(source: String, name: String, games: List<WebGame>) {
        val added = games.map { CsvGame(WebImport.gameId(it.url), it.title, it.url, it.genres) }
        val same = csvLists.firstOrNull {
            WishImport.sourceOf(it).equals(source, ignoreCase = true) && it.platform.equals(name, ignoreCase = true)
        }
        val list = CsvList(
            same?.platform ?: name, same?.file ?: "web",
            (same?.games.orEmpty() + added).distinctBy { it.id }, source = same?.let(WishImport::sourceOf) ?: source,
        )
        csvLists = WishImport.merge(csvLists, listOf(list))
        games.forEach { g -> if (g.image.isNotBlank()) wishImages[WebImport.gameId(g.url)] = g.image }
        rebuildRef()
        saveImports()
        showToast("ADDED ${"%,d".format(added.size)} GAME${if (added.size == 1) "" else "S"}\n${list.source} · ${list.platform}")
    }

    fun removeCsvList(list: CsvList) {
        csvLists = csvLists.filter { it !== list }
        val kept = csvLists.flatMap { l -> l.games.map { it.id } }.toSet()
        wishImages.keys.filter { it !in kept }.forEach { wishImages.remove(it) }
        rebuildRef()
        saveImports()
    }

    /** Looks up a picture for each of [ids] from that game's link, one at a time. */
    fun fetchWishImages(ids: List<String>, again: Boolean = false) {
        if (imageBusy) return
        // Looked up in the lists themselves, which are up to date even while the wishlist is being rebuilt.
        val byId = HashMap<String, CsvGame>()
        csvLists.forEach { l -> l.games.forEach { byId.putIfAbsent(it.id, it) } }
        val todo = ids.distinct().mapNotNull { byId[it] }.filter { it.url.isNotBlank() && (again || it.id !in wishImages) }
        if (todo.isEmpty()) {
            imageMessage = "Nothing to fetch: these games already have a picture or have no link."
            return
        }
        imageBusy = true
        imageJob = viewModelScope.launch {
            var found = 0
            var failedInARow = 0
            var plainWorks = true
            var stoppedEarly = false
            try {
                for ((i, w) in todo.withIndex()) {
                    imageMessage = "Fetching pictures… ${i + 1} of ${todo.size}"
                    when (val r = WishImport.image(getApplication(), w.url, plainWorks) { plainWorks = false }) {
                        is WishImport.Image.Found -> { wishImages[w.id] = r.address; found++; failedInARow = 0 }
                        WishImport.Image.None -> { wishImages[w.id] = WishImport.NO_IMAGE; failedInARow = 0 }
                        WishImport.Image.Failed -> failedInARow++
                    }
                    if (failedInARow >= 3) {
                        stoppedEarly = true
                        break
                    }
                    if ((i + 1) % 25 == 0) saveImages()
                    // A short pause between pages, to go easy on the site.
                    delay(600)
                }
                imageMessage = "Found $found picture${if (found == 1) "" else "s"}." +
                    if (stoppedEarly) " The site stopped answering, so try again later for the rest." else ""
            } finally {
                saveImages()
                imageBusy = false
            }
        }
    }

    fun stopImages() {
        imageJob?.cancel()
        imageMessage = "Stopped."
    }

    // ---------------- Achievement totals ----------------

    var achBusy by mutableStateOf(false)
        private set
    var achMessage by mutableStateOf("")
        private set

    /**
     * Reads how many achievements you've earned across your whole Steam,
     * RetroAchievements and PlayStation accounts. [quiet] is the once-a-day check when the app opens.
     */
    fun refreshAchievementTotals(quiet: Boolean = false) {
        if (achBusy) return
        val s = state
        val steam = AchievementTotals.canReadSteam(s)
        val retro = AchievementTotals.canReadRetro(s)
        val psn = AchievementTotals.canReadPsn(s)
        if (!steam && !retro && !psn) {
            if (!quiet) achMessage = "Add your Steam, RetroAchievements or PSN details in Setup → Achievements first."
            return
        }
        achBusy = true
        if (!quiet) achMessage = "Counting your achievements…"
        viewModelScope.launch {
            val found = mutableMapOf<String, Int>()
            val problems = mutableListOf<String>()
            if (retro) {
                try {
                    found[Achievements.RA] = AchievementTotals.retro(s)
                } catch (e: AchievementTotals.TotalsError) {
                    problems += e.message ?: "RetroAchievements couldn't be reached."
                } catch (e: Exception) {
                    problems += "RetroAchievements couldn't be reached."
                }
            }
            if (psn) {
                try {
                    found[Achievements.PSN] = AchievementTotals.psn(getApplication(), s)
                } catch (e: AchievementTotals.TotalsError) {
                    problems += e.message ?: "PlayStation trophies couldn't be read."
                } catch (e: Exception) {
                    problems += "PlayStation trophies couldn't be read."
                }
            }
            if (steam) {
                try {
                    found[Achievements.STEAM] = AchievementTotals.steam(s, File(dir, "steam-achievements.json")) { m ->
                        if (!quiet) viewModelScope.launch { achMessage = m }
                    }
                } catch (e: AchievementTotals.TotalsError) {
                    problems += e.message ?: "Steam couldn't be reached."
                } catch (e: Exception) {
                    problems += "Steam couldn't be reached."
                }
            }
            if (found.isNotEmpty()) update { it.copy(achTotals = it.achTotals + found, achTotalsAt = today()) }
            if (!quiet || found.isNotEmpty()) {
                achMessage = (listOfNotNull("Updated ${fmtDate(today())}.".takeIf { found.isNotEmpty() }) + problems)
                    .joinToString(" ")
            }
            achBusy = false
        }
    }

    /** Adds a home-screen icon that starts the game straight away. */
    fun addHomeIcon(gameId: String) {
        val g = state.games.firstOrNull { it.id == gameId } ?: return
        viewModelScope.launch {
            val ok = Shortcuts.pin(getApplication(), g)
            if (!ok) showToast("Your home screen doesn't allow adding icons")
        }
    }

    init {
        viewModelScope.launch {
            val (saved, file) = withContext(Dispatchers.IO) {
                Store.loadState(dir) to Store.loadDataFile(dir)
            }
            if (saved != null) state = saved
            if (file != null) {
                fileRef = file.ref
                fileState = file.state
            }
            val (lists, images) = withContext(Dispatchers.IO) { WishImport.load(dir) }
            csvLists = lists
            wishImages.putAll(images)
            // Pictures used to be saved inside the lists file; give them their own.
            if (images.isNotEmpty() && !WishImport.hasImagesFile(dir)) saveImages()
            val file0 = fileRef
            ref = withContext(Dispatchers.Default) { RefData(file0.history, WishImport.wishItems(lists) + file0.wishlist, file0.exportedAt) }
            lastCrash = withContext(Dispatchers.IO) { CrashLog.read(dir) }
            loaded = true
            pendingPlay?.let { pendingPlay = null; play(it) }
            // Once a day, quietly bring the account-wide achievement count up to date.
            if (state.achTotalsAt != today()) refreshAchievementTotals(quiet = true)
            // First time on this phone: fetch your data straight away.
            if (file == null) sync()
        }
    }

    /** Change your state and save it on the phone. */
    fun update(change: (AppState) -> AppState) {
        state = change(state)
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock { Store.saveState(dir, state) }
        }
    }

    fun showToast(text: String) {
        toast = text
    }

    /** Downloads your data file and refreshes history, wishlist and catalog. */
    fun sync() {
        if (syncing) return
        syncing = true
        syncMessage = "Downloading your data…"
        val address = state.dataUrl
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val text = Net.get(address)
                    val p = Store.parseExport(text)
                    Store.saveDataFile(dir, text)
                    p
                }
                fileRef = parsed.ref
                rebuildRef()
                fileState = parsed.state
                val imported = parsed.state
                // On a fresh install, take your games and devices from the file too.
                val firstImport = state.games.isEmpty() && state.baseline == 0 && imported != null
                update { s ->
                    var n = s.copy(lastSync = today())
                    if (firstImport && imported != null) {
                        n = n.copy(
                            baseline = imported.baseline,
                            devices = imported.devices.ifEmpty { s.devices },
                            games = imported.games,
                        )
                    }
                    n
                }
                syncMessage = "Updated. ${"%,d".format(parsed.ref.history.size)} past completions, " +
                    "${"%,d".format(parsed.ref.wishlist.size)} wishlist and catalog games."
            } catch (e: FileNotFoundException) {
                syncMessage = "No data file at that address. Upload backlog-data.json to your repo, " +
                    "then tap Sync."
            } catch (e: Exception) {
                syncMessage = "Couldn't download your data. Check your connection and the address in Setup."
            } finally {
                syncing = false
            }
        }
    }

    /** Replaces your games, devices and extra count with the ones in the data file. */
    fun replaceGamesFromFile() {
        val f = fileState ?: return
        update { s ->
            s.copy(baseline = f.baseline, devices = f.devices.ifEmpty { s.devices }, games = f.games)
        }
        showToast("GAMES REPLACED\nfrom your data file")
    }

    /** Copies picked screenshots into the app. Returns the saved paths, in the order picked. */
    suspend fun importScreenshots(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val folder = File(dir, "screenshots").apply { mkdirs() }
        uris.mapNotNull { uri ->
            try {
                val out = File(folder, "${newId()}.img")
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                } ?: return@mapNotNull null
                out.absolutePath
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Copies a picked image into the app so it keeps working. Returns its path. */
    suspend fun importCover(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val folder = File(dir, "covers").apply { mkdirs() }
            val out = File(folder, "${newId()}.img")
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            out.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
