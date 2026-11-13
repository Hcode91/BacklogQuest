package com.backlogquest.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.EmuEntry
import com.backlogquest.app.data.EmuSystems
import com.backlogquest.app.data.EmuUpdates
import com.backlogquest.app.data.Emulators
import com.backlogquest.app.data.GitHubReleases
import com.backlogquest.app.data.Launcher
import com.backlogquest.app.data.fmtDate
import com.backlogquest.app.data.newId
import com.backlogquest.app.data.today
import kotlinx.coroutines.launch

/** Where an emulator stands: its version, the latest release, and what you can do about it. */
private class EmuStatus(val have: String?, val status: GitHubReleases.Status?, val label: String)

private fun statusOf(vm: BacklogViewModel, e: EmuEntry): EmuStatus {
    val have = vm.installedVersion(e)
    if (e.pkg.isNotBlank() && have == null) return EmuStatus(null, null, "NOT INSTALLED")
    if (e.latest.isBlank()) return EmuStatus(have, null, if (e.repo.isBlank()) "NO GITHUB LINK" else "NOT CHECKED")
    if (have.isNullOrBlank()) return EmuStatus(have, GitHubReleases.Status.UNKNOWN, "ADD YOUR VERSION")
    val latest = GitHubReleases.Release(e.latest, e.latestName, e.latestUrl, e.latestAt, false, emptyList(), "")
    return when (val st = GitHubReleases.compare(have, latest)) {
        GitHubReleases.Status.UP_TO_DATE -> EmuStatus(have, st, "UP TO DATE")
        GitHubReleases.Status.UPDATE -> EmuStatus(have, st, "UPDATE AVAILABLE")
        GitHubReleases.Status.UNKNOWN -> EmuStatus(have, st, "CAN'T TELL")
    }
}

/** An emulator app found on this phone, and what it plays when that's known. */
private class Found(val name: String, val pkg: String, val info: EmuSystems.Info?)

/** Emulators on this phone: ones the app can launch games in, plus any app it recognises by name or package. */
private fun foundOnPhone(ctx: Context): List<Found> {
    val launchable = Emulators.all.mapNotNull { e ->
        Emulators.installed(ctx, e)?.let { Found(e.label, it.packageName, EmuSystems.find(e.label, it.packageName)) }
    }
    val pkgs = launchable.map { it.pkg }.toSet()
    val recognised = Launcher.launchableApps(ctx)
        .filter { it.first !in pkgs }
        .mapNotNull { (pkg, label) -> EmuSystems.find(label, pkg)?.let { Found(label, pkg, it) } }
    return (launchable + recognised).sortedBy { it.name.lowercase() }
}

/** Keep track of your emulators and update them from their GitHub releases. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmulatorsScreen(vm: BacklogViewModel) {
    val ctx = LocalContext.current
    val s = vm.state
    var editing by remember { mutableStateOf<EmuEntry?>(null) }
    // Installed versions are read again when you come back to the app (after an update, say).
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed++ }
    val found = remember(resumed, s.emulators) {
        val added = s.emulators.map { it.pkg }.toSet()
        foundOnPhone(ctx).filter { it.pkg !in added }
    }
    // Once a day, look for new releases when the tab opens.
    LaunchedEffect(Unit) {
        if (s.emulators.any { it.repo.isNotBlank() && it.checkedAt != today() }) vm.checkEmulators(quiet = true)
    }
    editing?.let { e -> EmuSheet(vm, e) { editing = null } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("EMULATORS")
                Hint("Keep track of your emulators. Add each one's GitHub link to see whether you have the latest version, and update from here.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("+ ADD EMULATOR", kind = BtnKind.B) { editing = EmuEntry(newId(), "") }
                    if (s.emulators.any { it.repo.isNotBlank() }) {
                        PixelButton(if (vm.emuBusy) "CHECKING…" else "CHECK FOR UPDATES", enabled = !vm.emuBusy) { vm.checkEmulators() }
                    }
                }
                if (vm.emuMessage.isNotBlank()) Hint(vm.emuMessage)
            }
        }
        if (found.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("ON THIS PHONE")
                    Hint("Emulators found on this phone, and what they play. Tap one to add it.")
                    found.forEach { f ->
                        Tile(Modifier.fillMaxWidth(), onClick = { editing = EmuEntry(newId(), f.name, pkg = f.pkg) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    f.name, Modifier.weight(1f), color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text("+ ADD", color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            f.info?.let { Hint(it.summary + if (it.unsure) " (not confirmed)" else "") }
                        }
                    }
                }
            }
        }
        if (s.emulators.isEmpty()) {
            item {
                Panel {
                    Text("No emulators yet", color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Hint("Add the emulators you use. For ones on this phone, the app reads the version you have; for others (on your PC, say) you type it in.")
                }
            }
        }
        items(s.emulators.sortedBy { it.name.lowercase() }, key = { it.id }) { e ->
            // Read again whenever you come back to the app, so a finished update shows straight away.
            val st = remember(e, resumed) { statusOf(vm, e) }
            EmuTile(vm, e, st, onOpen = { editing = e })
        }
    }
}

@Composable
private fun EmuTile(vm: BacklogViewModel, e: EmuEntry, st: EmuStatus, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val onPhone = e.pkg.isNotBlank()
    val progress = vm.emuProgress[e.id]
    Tile(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                e.name.ifBlank { "Unnamed" }, Modifier.weight(1f), color = BQ.Ink, fontWeight = FontWeight.Bold,
                fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            when (st.status) {
                GitHubReleases.Status.UP_TO_DATE -> StatusLabel(st.label, BQ.OkBg, BQ.Ok)
                GitHubReleases.Status.UPDATE -> StatusLabel(st.label, BQ.OwnBg, BQ.B)
                else -> StatusLabel(st.label)
            }
        }
        val plays = EmuSystems.summaryFor(e)
        if (plays.isNotBlank()) Text(plays, color = BQ.Ink, fontSize = 13.sp)
        Hint(if (onPhone) "On this phone" else e.where.ifBlank { "Not on this phone" })
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("YOU HAVE", color = BQ.Muted, fontFamily = BQ.Mono, fontSize = 10.sp)
                Text(
                    st.have?.ifBlank { "Unknown" } ?: "Not installed",
                    color = BQ.Ink, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("LATEST", color = BQ.Muted, fontFamily = BQ.Mono, fontSize = 10.sp)
                Text(
                    e.latest.ifBlank { "—" }, color = if (st.status == GitHubReleases.Status.UPDATE) BQ.B else BQ.Ink,
                    fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (e.latestAt.isNotBlank()) Text(fmtDate(e.latestAt), color = BQ.Muted, fontSize = 11.sp)
            }
        }
        if (e.checkError.isNotBlank()) Text(e.checkError, color = BQ.CoolFg, fontSize = 12.sp)
        if (progress != null) {
            LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth(), color = BQ.B, trackColor = BQ.Edge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hint("Downloading… $progress%", Modifier.weight(1f))
                PixelButton("CANCEL") { vm.cancelEmuUpdate(e.id) }
            }
        } else if (st.status == GitHubReleases.Status.UPDATE || st.status == GitHubReleases.Status.UNKNOWN) {
            val release = vm.emuReleases[e.id]
            val apk = release?.let { GitHubReleases.pickApk(it, EmuUpdates.abis(), e.assetHint) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    // On this phone, with the right download known: update in one tap.
                    onPhone && apk != null && st.status == GitHubReleases.Status.UPDATE ->
                        PixelButton("UPDATE TO ${e.latest}", Modifier.weight(1f), kind = BtnKind.A) { vm.installEmuUpdate(e, apk) }
                    // On this phone, but the download needs choosing (or the release isn't loaded yet).
                    onPhone -> PixelButton("CHOOSE DOWNLOAD", Modifier.weight(1f), kind = BtnKind.B, onClick = onOpen)
                    else -> {}
                }
                if (e.latestUrl.isNotBlank()) {
                    PixelButton("RELEASE ↗", if (onPhone) Modifier else Modifier.weight(1f)) { openSafely(vm, ctx, e.latestUrl) }
                }
            }
        }
    }
}

private fun openSafely(vm: BacklogViewModel, ctx: Context, url: String) {
    try {
        EmuUpdates.openPage(ctx, url)
    } catch (err: Exception) {
        vm.showToast("No browser available")
    }
}

/** Add or edit an emulator, check its GitHub releases, and pick a download. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EmuSheet(vm: BacklogViewModel, start: EmuEntry, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = vm.state.emulators.none { it.id == start.id }
    var draft by remember { mutableStateOf(start) }
    var link by remember { mutableStateOf(if (start.repo.isBlank()) "" else "https://github.com/${start.repo}") }
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf("") }
    val release = vm.emuReleases[draft.id]
    // Apps on this phone to link to: known emulators first, then everything else you can open.
    val apps = remember {
        val known = foundOnPhone(ctx).map { it.name to it.pkg }
        val knownPkgs = known.map { it.second }.toSet()
        known + Launcher.launchableApps(ctx).filter { it.first !in knownPkgs }.map { it.second to it.first }
    }
    // What it plays: from the built-in list, unless you've typed your own.
    val info = EmuSystems.find(draft.name, draft.pkg)
    var systemsText by remember { mutableStateOf(start.systems) }
    fun savedSystems() = systemsText.trim().let { if (it == info?.summary) "" else it }
    val have = if (draft.pkg.isNotBlank()) EmuUpdates.installedVersion(ctx, draft.pkg) else null

    fun check() {
        val repo = GitHubReleases.repoFrom(link)
        if (repo == null) {
            problem = "That doesn't look like a GitHub link. It should look like github.com/owner/project."
            return
        }
        draft = draft.copy(repo = repo)
        checking = true
        problem = ""
        scope.launch {
            try {
                val r = GitHubReleases.latest(repo, draft.prerelease)
                vm.emuReleases[draft.id] = r
                draft = draft.copy(
                    latest = r.version, latestName = r.name, latestUrl = r.page, latestAt = r.published,
                    checkedAt = today(), checkError = "",
                )
                // Saved emulators get the result straight away; new ones when you save.
                if (!isNew) vm.noteRelease(draft.id, r)
            } catch (err: GitHubReleases.ReleaseError) {
                problem = err.message.orEmpty()
            } finally {
                checking = false
            }
        }
    }
    // Opening a saved emulator whose release isn't loaded yet fetches it, so its downloads can be listed.
    LaunchedEffect(Unit) { if (!isNew && start.repo.isNotBlank() && release == null) check() }

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
            SectionTitle(if (isNew) "ADD EMULATOR" else draft.name.uppercase())
            OutlinedTextField(
                value = draft.name, onValueChange = { draft = draft.copy(name = it) },
                label = { Text("Name") }, singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
            )

            // What it plays.
            SectionTitle("PLAYS")
            if (info != null) {
                Panel {
                    Text(info.summary, color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    info.details.forEach { line -> Text("• $line", color = BQ.Muted, fontSize = 12.sp) }
                    if (info.unsure) Hint("Not confirmed: this app couldn't be checked, so this is a best guess.")
                }
            }
            OutlinedTextField(
                value = systemsText, onValueChange = { systemsText = it },
                label = { Text(if (info == null) "Systems it plays" else "Your own description (optional)") },
                placeholder = { Text(info?.summary ?: "e.g. PlayStation 2") },
                colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
            )

            // Where it is: an app on this phone, or somewhere else.
            SectionTitle("WHERE YOU HAVE IT")
            AppPicker(
                label = if (draft.pkg.isBlank()) "Not on this phone" else Launcher.appLabel(ctx, draft.pkg),
                apps = apps,
            ) { pkg -> draft = draft.copy(pkg = pkg, name = draft.name.ifBlank { if (pkg.isBlank()) "" else Launcher.appLabel(ctx, pkg) }) }
            if (draft.pkg.isNotBlank()) {
                Hint(if (have == null) "That app isn't installed any more." else "Installed version: ${have.ifBlank { "unknown" }}")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.state.devices.forEach { d ->
                        TabChip(d.name, draft.where == d.name) { draft = draft.copy(where = if (draft.where == d.name) "" else d.name) }
                    }
                }
                OutlinedTextField(
                    value = draft.version, onValueChange = { draft = draft.copy(version = it) },
                    label = { Text("Version you have") }, placeholder = { Text("e.g. 2.3.1") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            }

            // Where updates come from.
            SectionTitle("GITHUB")
            OutlinedTextField(
                value = link, onValueChange = { link = it },
                label = { Text("GitHub link") }, placeholder = { Text("https://github.com/owner/project") },
                singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            CheckRow(
                "Include pre-releases and nightlies",
                "Count test builds as the latest version, for emulators that mostly publish those.",
                draft.prerelease,
            ) { draft = draft.copy(prerelease = !draft.prerelease) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(if (checking) "CHECKING…" else "CHECK GITHUB", kind = BtnKind.B, enabled = !checking && link.isNotBlank()) { check() }
                if (problem.isNotBlank()) Text(problem, Modifier.weight(1f), color = BQ.CoolFg, fontSize = 12.sp)
            }

            release?.let { r ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("LATEST", color = BQ.Muted, fontFamily = BQ.Mono, fontSize = 10.sp)
                            Text(r.version, color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Hint(listOfNotNull(r.name.takeIf { it.isNotBlank() && it != r.version }, fmtDate(r.published), "pre-release".takeIf { r.prerelease }).joinToString(" · "))
                        }
                        PixelButton("RELEASE ↗") { openSafely(vm, ctx, r.page) }
                    }
                    val notes = GitHubReleases.plainNotes(r.notes)
                    if (notes.isNotBlank()) Text(notes, color = BQ.Muted, fontSize = 12.sp, maxLines = 10, overflow = TextOverflow.Ellipsis)
                }
                val apks = GitHubReleases.apks(r)
                val best = GitHubReleases.pickApk(r, EmuUpdates.abis(), draft.assetHint)
                SectionTitle("DOWNLOADS")
                if (r.assets.isEmpty()) Hint("This release has no downloads attached. Open the release page to see where to get it.")
                if (draft.pkg.isNotBlank() && apks.isNotEmpty()) {
                    Hint(
                        if (best != null) "Suggested for this phone: ${best.name}"
                        else "Several Android builds. Pick the one for your phone; type part of its name below to remember it.",
                    )
                }
                r.assets.forEach { a ->
                    val isApk = a.name.endsWith(".apk", ignoreCase = true)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                a.name, color = if (a == best) BQ.B else BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            if (a.size > 0) Hint("%.1f MB".format(a.size / 1048576.0))
                        }
                        if (isApk && draft.pkg.isNotBlank()) {
                            PixelButton("INSTALL", kind = if (a == best) BtnKind.A else BtnKind.PLAIN) {
                                if (isNew) vm.saveEmulator(draft.copy(name = draft.name.ifBlank { "Emulator" }, systems = savedSystems()))
                                vm.installEmuUpdate(draft, a)
                                onDismiss()
                            }
                        } else {
                            PixelButton("OPEN ↗") { openSafely(vm, ctx, a.url) }
                        }
                    }
                }
                if (draft.pkg.isNotBlank() && apks.size > 1) {
                    OutlinedTextField(
                        value = draft.assetHint, onValueChange = { draft = draft.copy(assetHint = it) },
                        label = { Text("Download to use (part of its name)") }, placeholder = { Text("e.g. arm64") },
                        singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (draft.pkg.isNotBlank() && apks.isNotEmpty()) {
                    Hint("An update installs over your copy only if both come from the same place. If you got it from the Play Store, Android may refuse a GitHub build.")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("SAVE", kind = BtnKind.A, enabled = draft.name.isNotBlank()) {
                    val repo = GitHubReleases.repoFrom(link) ?: if (link.isBlank()) "" else draft.repo
                    vm.saveEmulator(draft.copy(name = draft.name.trim(), repo = repo, systems = savedSystems()))
                    onDismiss()
                }
                if (!isNew) {
                    PixelButton("DELETE") {
                        vm.deleteEmulator(draft.id)
                        onDismiss()
                    }
                }
                PixelButton("CLOSE", onClick = onDismiss)
            }
        }
    }
}

/** A dropdown of apps on this phone, with "Not on this phone" at the top. */
@Composable
private fun AppPicker(label: String, apps: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TabChip("$label ▾", false) { open = true }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("Not on this phone", color = BQ.Ink) }, onClick = { onPick(""); open = false })
            apps.forEach { (name, pkg) ->
                DropdownMenuItem(text = { Text(name, color = BQ.Ink) }, onClick = { onPick(pkg); open = false })
            }
        }
    }
}
