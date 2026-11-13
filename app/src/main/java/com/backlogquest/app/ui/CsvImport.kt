package com.backlogquest.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.CsvList
import com.backlogquest.app.data.WishImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pick CSV files, check the platform each one will be filed under, and add them to the wishlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportSheet(vm: BacklogViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Lists read from the chosen files, waiting for you to confirm. The platform names can be edited.
    var pending by remember { mutableStateOf<List<CsvList>>(emptyList()) }
    var problems by remember { mutableStateOf<List<String>>(emptyList()) }
    var reading by remember { mutableStateOf(false) }
    // Where the lists are from, such as MobyGames: a tile in Discover and a place on the Spin wheel.
    var source by remember { mutableStateOf("") }
    var skipKnown by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        reading = true
        scope.launch {
            val read = ArrayList<CsvList>()
            val failed = ArrayList<String>()
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val name = fileName(context, uri)
                    try {
                        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                            ?: throw WishImport.CsvError("The file couldn't be opened.")
                        read += WishImport.parse(text, WishImport.platformFromFileName(name), name)
                    } catch (e: WishImport.CsvError) {
                        failed += "$name: ${e.message}"
                    } catch (e: Exception) {
                        failed += "$name: couldn't be read."
                    }
                }
            }
            pending = WishImport.merge(pending, read)
            if (source.isBlank()) {
                source = pending.firstNotNullOfOrNull { l -> l.games.firstOrNull { it.url.isNotBlank() }?.url }
                    ?.let(WishImport::suggestSource).orEmpty()
            }
            problems = failed
            reading = false
        }
    }

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
            SectionTitle("IMPORT CSV LISTS")
            Hint(
                "Choose one or more CSV files. Each game's title, link and genres are read from the file, " +
                    "and the platform comes from the file's name (or a Platform column, if there is one).",
            )
            PixelButton(if (reading) "READING…" else "CHOOSE FILES", kind = BtnKind.B, enabled = !reading) {
                picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
            }
            problems.forEach { Text(it, color = BQ.CoolFg, fontSize = 13.sp) }

            if (pending.isNotEmpty()) {
                SectionTitle("READY TO ADD")
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("Source") },
                    singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                Hint("Where these lists are from, such as MobyGames. Each source gets a tile in Discover and its own place on the Spin wheel.")
                pending.forEachIndexed { i, list ->
                    Panel {
                        Hint("${list.file} · ${"%,d".format(list.games.size)} game${if (list.games.size == 1) "" else "s"}")
                        OutlinedTextField(
                            value = list.platform,
                            onValueChange = { name ->
                                pending = pending.mapIndexed { j, l -> if (j == i) l.copy(platform = name) else l }
                            },
                            label = { Text("Platform") },
                            singleLine = true, colors = bqFieldColors(), modifier = Modifier.fillMaxWidth(),
                        )
                        list.games.firstOrNull()?.let { g ->
                            Hint("First game: ${g.title}" + if (g.genres.isEmpty()) "" else " · ${g.genres.take(3).joinToString(", ")}")
                        }
                    }
                }
                // Games you already have: in your wishlist on the same platform, or in your games.
                // Lists being imported again don't count, as they're replaced.
                val index = remember(vm.ref, vm.state.hiddenWish, vm.state.games) { vm.duplicateIndex() }
                val known = remember(pending, source, index) {
                    val replacing = pending.map { source.trim().lowercase() to it.platform.trim().lowercase() }.toSet()
                    pending.map { l -> l.games.filter { index.known(it.title, l.platform.trim(), replacing) }.map { it.id }.toSet() }
                }
                val knownCount = known.sumOf { it.size }
                if (knownCount > 0) {
                    CheckRow(
                        "Skip ${"%,d".format(knownCount)} already in your wishlist or games",
                        "Same title on the same platform, or a game in your games list or history.",
                        skipKnown,
                    ) { skipKnown = !skipKnown }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("ADD TO WISHLIST", kind = BtnKind.A, enabled = source.isNotBlank()) {
                        val lists = pending.mapIndexed { i, l ->
                            l.copy(
                                platform = l.platform.trim(), source = source.trim(),
                                games = if (skipKnown) l.games.filter { it.id !in known[i] } else l.games,
                            )
                        }.filter { it.games.isNotEmpty() }
                        if (lists.isEmpty()) vm.showToast("NOTHING NEW TO ADD\nYou already have every game in these lists")
                        else vm.importCsv(lists)
                        pending = emptyList()
                        onDismiss()
                    }
                    PixelButton("CLEAR") { pending = emptyList() }
                }
            }

            if (vm.csvLists.isNotEmpty()) {
                SectionTitle("ALREADY IMPORTED")
                vm.csvLists.sortedWith(compareBy({ WishImport.sourceOf(it).lowercase() }, { it.platform.lowercase() })).forEach { list ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(WishImport.sourceOf(list), color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Hint("${list.platform.ifBlank { "No platform" }} · ${"%,d".format(list.games.size)} game${if (list.games.size == 1) "" else "s"}")
                        }
                        PixelButton("REMOVE") { vm.removeCsvList(list) }
                    }
                }
                Hint("Importing a file for the same source and platform replaces that list.")
            }
            PixelButton("CLOSE", onClick = onDismiss)
        }
    }
}
