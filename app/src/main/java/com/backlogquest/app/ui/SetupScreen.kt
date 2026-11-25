package com.backlogquest.app.ui

import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.backlogquest.app.data.Platforms
import com.backlogquest.app.data.ReleaseReminders
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.DEFAULT_DATA_URL
import com.backlogquest.app.data.Device
import com.backlogquest.app.data.GameStore
import com.backlogquest.app.data.fmtDate
import com.backlogquest.app.data.newId

private val SETUP_TILES = listOf(
    Triple("data", "YOUR DATA", "Where your history, wishlist and catalog come from"),
    Triple("file", "GAMES FROM THE FILE", "Replace your games with the ones in the data file"),
    Triple("count", "EXTRA COUNT", "Completions missing from your history"),
    Triple("autofill", "AUTO-FILL", "Box art, genre and length for new games"),
    Triple("playtime", "PLAY TIME", "Measure sessions and read hours from Steam"),
    Triple("achievements", "ACHIEVEMENTS", "Steam, RetroAchievements and PSN details"),
    Triple("prices", "PRICES", "Wishlist prices from IsThereAnyDeal"),
    Triple("stores", "IMPORT FROM STORES", "Bring in your libraries"),
    Triple("devices", "DEVICES", "What you play on, and the two-for-two rule"),
    Triple("platforms", "PLATFORMS", "Every system you play, so none get left out"),
    Triple("releases", "RELEASES", "IGDB keys for the Upcoming and New tabs"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: BacklogViewModel) {
    var importing by remember { mutableStateOf<GameStore?>(null) }
    val s = vm.state
    var baselineText by remember { mutableStateOf(if (s.baseline > 0) s.baseline.toString() else "") }
    var urlText by remember { mutableStateOf(s.dataUrl) }
    var keyText by remember { mutableStateOf(s.rawgKey) }
    var raUserText by remember { mutableStateOf(s.raUser) }
    var raKeyText by remember { mutableStateOf(s.raKey) }
    var itadText by remember { mutableStateOf(s.itadKey) }
    var steamKeyText by remember { mutableStateOf(s.steamKey) }
    var steamProfileText by remember { mutableStateOf(s.steamProfile) }
    var psnText by remember { mutableStateOf(s.psnId) }
    var exoText by remember { mutableStateOf(s.exophaseUser) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var igdbIdText by remember { mutableStateOf(s.igdbClientId) }
    val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    var igdbSecretText by remember { mutableStateOf(s.igdbSecret) }
    var newDevice by remember { mutableStateOf("") }
    var newPlatform by remember { mutableStateOf("") }
    var confirmReplace by remember { mutableStateOf(false) }
    // Which part of Setup is open; null shows the tiles.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = open != null) { open = null }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (open == null) {
            item { StatusPanel(vm) }
            item { SectionTitle("SETUP") }
            // Two tiles per row; tap one to open that part of Setup.
            items(SETUP_TILES.chunked(2)) { pair ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (key, title, about) ->
                        Tile(Modifier.weight(1f).fillMaxHeight(), onClick = { open = key }) {
                            Text(title, color = BQ.Ink, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(about, color = BQ.Muted, fontSize = 12.sp)
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton("◀ BACK") { open = null }
                    SectionTitle("SETUP")
                }
            }
        }

        if (open == "data") item {
            Panel {
                SectionTitle("YOUR DATA")
                Hint(
                    "History, wishlist and catalog come from backlog-data.json in your GitHub repo. " +
                        "Tap Sync whenever that file changes.",
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Data file address") },
                    singleLine = true,
                    colors = bqFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton(if (vm.syncing) "SYNCING…" else "SYNC NOW", kind = BtnKind.B, enabled = !vm.syncing) {
                        val clean = urlText.trim().ifBlank { DEFAULT_DATA_URL }
                        urlText = clean
                        vm.update { it.copy(dataUrl = clean) }
                        vm.sync()
                    }
                    if (urlText.trim() != DEFAULT_DATA_URL) {
                        PixelButton("RESET ADDRESS") { urlText = DEFAULT_DATA_URL }
                    }
                }
                val info = buildString {
                    if (vm.syncMessage.isNotBlank()) append(vm.syncMessage)
                    else if (s.lastSync.isNotBlank()) append("Last synced ${fmtDate(s.lastSync)}.")
                    if (vm.ref.exportedAt.isNotBlank()) append(" Data file made ${fmtDate(vm.ref.exportedAt)}.")
                }
                if (info.isNotBlank()) Hint(info.trim())
            }
        }

        if (open == "file") item {
            Panel {
                SectionTitle("YOUR GAMES FROM THE FILE")
                Hint(
                    "Your own games list lives on this phone. On a fresh install it's copied from the data file. " +
                        "To start over from the file, replace it here. This can't be undone.",
                )
                if (vm.fileState == null) {
                    Hint("Sync first to load the data file.")
                } else if (confirmReplace) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton("CONFIRM REPLACE", kind = BtnKind.A) {
                            vm.replaceGamesFromFile()
                            baselineText = vm.state.baseline.let { if (it > 0) it.toString() else "" }
                            confirmReplace = false
                        }
                        PixelButton("CANCEL") { confirmReplace = false }
                    }
                } else {
                    PixelButton("REPLACE MY GAMES") { confirmReplace = true }
                }
            }
        }

        if (open == "count") item {
            Panel {
                OutlinedTextField(
                    value = baselineText,
                    onValueChange = { text ->
                        baselineText = text.filter { it.isDigit() }
                        vm.update { it.copy(baseline = baselineText.toIntOrNull() ?: 0) }
                    },
                    label = { Text("Extra completions not in your history") },
                    singleLine = true,
                    colors = bqFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint(
                    "Your ${"%,d".format(vm.ref.history.size)} imported HowLongToBeat completions already count. " +
                        "Use this only for games missing from that list.",
                )
            }
        }

        if (open == "autofill") item {
            Panel {
                SectionTitle("AUTO-FILL")
                Hint(
                    "Auto-fill gets times to beat from HowLongToBeat with no setup. For genre, description " +
                        "and covers it also uses RAWG, a free game database: get a free API key at " +
                        "rawg.io/apidocs and paste it here.",
                )
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { text ->
                        keyText = text
                        vm.update { it.copy(rawgKey = text.trim()) }
                    },
                    label = { Text("RAWG API key") },
                    singleLine = true,
                    colors = bqFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (open == "playtime") item {
            Panel {
                SectionTitle("PLAY TIME")
                val ctx = androidx.compose.ui.platform.LocalContext.current
                var usage by remember { mutableStateOf(com.backlogquest.app.data.PlayTime.hasUsageAccess(ctx)) }
                Hint(
                    "Games started from Backlog Quest (the Play button or a home-screen icon) ask to add the time " +
                        "you played when you come back. Allow Usage access so it counts only time actually spent " +
                        "in the game or emulator.",
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (usage) "✓ Usage access allowed" else "Usage access not allowed",
                        color = if (usage) BQ.Ok else BQ.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                    if (!usage) {
                        PixelButton("ALLOW", kind = BtnKind.B) {
                            ctx.startActivity(com.backlogquest.app.data.PlayTime.usageAccessIntent())
                        }
                    } else {
                        PixelButton("CHECK") { usage = com.backlogquest.app.data.PlayTime.hasUsageAccess(ctx) }
                    }
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(2000)
                        usage = com.backlogquest.app.data.PlayTime.hasUsageAccess(ctx)
                    }
                }
                Hint("Steam games: copy your hours from Steam (needs your Steam key and profile below).")
                PixelButton("SYNC STEAM PLAY TIME", kind = BtnKind.B) { vm.syncSteamHours() }
            }
        }

        if (open == "achievements") item {
            Panel {
                SectionTitle("ACHIEVEMENTS")
                Hint(
                    "Steam: add your free Steam Web API key (steamcommunity.com/dev/apikey) for the most " +
                        "reliable lists. Add your Steam profile too (link, custom name or SteamID) and achievements " +
                        "you've unlocked are ticked automatically; your profile's game details must be public. " +
                        "Without a key, Steam games use TrueSteamAchievements.",
                )
                OutlinedTextField(
                    value = steamKeyText,
                    onValueChange = { t -> steamKeyText = t; vm.update { it.copy(steamKey = t.trim()) } },
                    label = { Text("Steam Web API key") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = steamProfileText,
                    onValueChange = { t -> steamProfileText = t; vm.update { it.copy(steamProfile = t.trim()) } },
                    label = { Text("Steam profile (optional)") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                Hint(
                    "Other platforms: Auto-fill uses PSNProfiles for " +
                        "PlayStation, TrueAchievements for Xbox, and RetroAchievements for retro platforms " +
                        "(set the game's platform, e.g. PS2 or SNES). RetroAchievements needs your username and " +
                        "web API key, from retroachievements.org → Settings. With them, achievements you've " +
                        "already earned there are ticked automatically.",
                )
                OutlinedTextField(
                    value = raUserText,
                    onValueChange = { t -> raUserText = t; vm.update { it.copy(raUser = t.trim()) } },
                    label = { Text("RetroAchievements username") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = raKeyText,
                    onValueChange = { t -> raKeyText = t; vm.update { it.copy(raKey = t.trim()) } },
                    label = { Text("RetroAchievements web API key") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                Hint(
                    "PlayStation total: add your PSN ID and your trophy count is read from your public " +
                        "PSNProfiles page (psnprofiles.com/your-id). It shows what that site last recorded, " +
                        "so tap Update on your profile there after earning trophies. No password or token needed.",
                )
                OutlinedTextField(
                    value = psnText,
                    onValueChange = { t -> psnText = t; vm.update { it.copy(psnId = t.trim()) } },
                    label = { Text("PSN ID (optional)") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                Hint(
                    "Google Play: add your Exophase username and Android games get their achievement lists, " +
                        "with your unlocks ticked, from your public Exophase profile. Link Google Play on " +
                        "exophase.com first, and keep your Play Games profile public (Play Games → Settings → " +
                        "Play Games profile → Public). Exophase only notices games you've played recently, so " +
                        "open a game once if it's missing.",
                )
                OutlinedTextField(
                    value = exoText,
                    onValueChange = { t -> exoText = t; vm.update { it.copy(exophaseUser = t.trim()) } },
                    label = { Text("Exophase username or profile link (optional)") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (open == "prices") item {
            Panel {
                SectionTitle("PRICES")
                Hint(
                    "Wishlist prices come from IsThereAnyDeal: the cheapest UK price across Steam, GOG, Epic, " +
                        "Humble and more, plus the lowest it's ever been. It covers PC stores only. Get a free " +
                        "API key at isthereanydeal.com → Apps → Register app, and paste the API key (not the " +
                        "OAuth client ID) here.",
                )
                OutlinedTextField(
                    value = itadText,
                    onValueChange = { t -> itadText = t; vm.update { it.copy(itadKey = t.trim()) } },
                    label = { Text("IsThereAnyDeal API key") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (open == "releases") item {
            Panel {
                SectionTitle("RELEASES")
                Hint(
                    "The Upcoming and New tabs get release dates from IGDB, the database most game-tracking apps " +
                        "use. It's free, through a Twitch developer app: sign in at dev.twitch.tv/console (two-step " +
                        "sign-in has to be on), choose Register Your Application, give it any name, set the OAuth " +
                        "redirect URL to http://localhost and the category to Application Integration, then Create. " +
                        "Open it, copy the Client ID, press New Secret and copy that too. Both stay on this phone.",
                )
                OutlinedTextField(
                    value = igdbIdText,
                    onValueChange = { t -> igdbIdText = t; vm.update { it.copy(igdbClientId = t.trim(), igdbToken = "", igdbTokenExpires = 0) } },
                    label = { Text("Twitch client ID") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = igdbSecretText,
                    onValueChange = { t -> igdbSecretText = t; vm.update { it.copy(igdbSecret = t.trim(), igdbToken = "", igdbTokenExpires = 0) } },
                    label = { Text("Twitch client secret") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                Hint("Only your platforms are asked about. Change them in Setup → Platforms.")
                CheckRow(
                    "Remind me the day before",
                    "When a game you wishlisted from the Upcoming tab comes out tomorrow, you get a notification. " +
                        "Dates are checked on IGDB twice a day, so a delay moves the reminder too.",
                    s.releaseReminders,
                ) {
                    val on = !s.releaseReminders
                    vm.setReleaseReminders(on)
                    if (on && android.os.Build.VERSION.SDK_INT >= 33 && !ReleaseReminders.canNotify(context)) {
                        askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (s.releaseReminders && !ReleaseReminders.canNotify(context)) {
                    Hint("Notifications are off for BacklogQuest, so reminders can't be shown. Turn them on in Android's settings for the app.")
                }
                if (s.releaseWatch.isNotEmpty()) Hint("Reminders set: ${s.releaseWatch.size}")
            }
        }

        if (open == "stores") item {
            Panel {
                SectionTitle("IMPORT FROM STORES")
                Hint(
                    "Sign in to a store to add your games to your backlog. You pick which ones before " +
                        "anything is added, and games already in your lists or history are skipped. " +
                        "PlayStation and Xbox list the games you've bought or played.",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameStore.values().forEach { st -> PixelButton(st.label.uppercase(), kind = BtnKind.B) { importing = st } }
                }
                PixelButton("SIGN OUT OF ALL STORES") {
                    CookieManager.getInstance().removeAllCookies(null)
                    WebStorage.getInstance().deleteAllData()
                    vm.showToast("Signed out of all stores")
                }
            }
        }

        if (open == "devices") item {
            Panel {
                SectionTitle("DEVICES")
                Hint("Exempt devices never go on cooldown and don't count toward the two-for-two rule.")
                s.devices.forEach { d ->
                    DeviceRow(vm, d)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newDevice,
                        onValueChange = { newDevice = it },
                        label = { Text("Add a device") },
                        placeholder = { Text("e.g. PS5, Steam Deck") },
                        singleLine = true,
                        colors = bqFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    PixelButton("ADD", kind = BtnKind.B, enabled = newDevice.isNotBlank()) {
                        val name = newDevice.trim()
                        vm.update { it.copy(devices = it.devices + Device(newId(), name, false)) }
                        newDevice = ""
                    }
                }
            }
        }
        if (open == "platforms") item {
            PlatformsPanel(vm, newPlatform, { newPlatform = it })
        }
    }
    importing?.let { st -> StoreImportDialog(vm, st) { importing = null } }
}

/** Your platforms, grouped as in the built-in list, with what plays each and how many games you have for it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformsPanel(vm: BacklogViewModel, newName: String, onNewName: (String) -> Unit) {
    val s = vm.state
    val counts = remember(s.games) {
        s.games.map { it.platform.trim().lowercase() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
    }
    val missing = Platforms.defaultNames.filter { d -> s.platforms.none { it.equals(d, ignoreCase = true) } }
    fun add(name: String) {
        val n = name.trim()
        if (n.isNotEmpty() && s.platforms.none { it.equals(n, ignoreCase = true) }) vm.update { it.copy(platforms = it.platforms + n) }
    }
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Panel {
        SectionTitle("MY PLATFORMS · ${s.platforms.size}")
        Hint(
            "Every system you play: the ones your emulators cover, plus your own devices' platforms. " +
                "They're offered when you set a game's platform. Remove any you don't use, or add your own.",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newName, onValueChange = { onNewName(it.take(40)) },
                label = { Text("Add a platform") }, placeholder = { Text("e.g. Steam Deck, Pico-8") },
                singleLine = true, colors = bqFieldColors(), modifier = Modifier.weight(1f),
            )
            PixelButton("ADD", kind = BtnKind.B, enabled = newName.isNotBlank()) { add(newName); onNewName("") }
        }
        if (missing.isNotEmpty()) {
            PixelButton("ADD BACK ${missing.size} REMOVED") { vm.update { it.copy(platforms = it.platforms + missing) } }
        }
    }
    val byFamily = s.platforms.groupBy { Platforms.familyOf(it) }
    Platforms.families.forEach { family ->
        val list = byFamily[family] ?: return@forEach
        Panel {
            SectionTitle("${family.uppercase()} · ${list.size}")
            list.forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(name, color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val with = Platforms.info(name)?.playedWith.orEmpty()
                        val games = counts[name.lowercase()] ?: 0
                        val about = listOfNotNull(
                            with.takeIf { it.isNotEmpty() }?.joinToString(", "),
                            if (games > 0) "$games game${if (games == 1) "" else "s"}" else null,
                        ).joinToString(" · ")
                        if (about.isNotEmpty()) Text(about, color = BQ.Muted, fontSize = 12.sp)
                    }
                    PixelButton("✕") { vm.update { st -> st.copy(platforms = st.platforms.filter { it != name }) } }
                }
            }
        }
    }
    }
}

@Composable
private fun DeviceRow(vm: BacklogViewModel, d: Device) {
    var name by remember(d.id) { mutableStateOf(d.name) }
    val inUse = vm.state.games.any { it.device == d.id }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { text ->
                name = text
                if (text.isNotBlank()) {
                    vm.update { st -> st.copy(devices = st.devices.map { if (it.id == d.id) it.copy(name = text.trim()) else it }) }
                }
            },
            label = { Text("Name") },
            singleLine = true,
            colors = bqFieldColors(),
            modifier = Modifier.weight(1f),
        )
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(
                checked = d.exempt,
                onCheckedChange = { on ->
                    vm.update { st -> st.copy(devices = st.devices.map { if (it.id == d.id) it.copy(exempt = on) else it }) }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = BQ.B, checkedThumbColor = BQ.Shell),
            )
            Text("Exempt", color = BQ.Muted, fontSize = 11.sp)
        }
        PixelButton("✕", enabled = !inUse && vm.state.devices.size > 1) {
            vm.update { st -> st.copy(devices = st.devices.filter { it.id != d.id }) }
        }
    }
}
