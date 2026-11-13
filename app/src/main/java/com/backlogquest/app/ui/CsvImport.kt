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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("ADD TO WISHLIST", kind = BtnKind.A) {
                        vm.importCsv(pending.map { it.copy(platform = it.platform.trim()) })
                        pending = emptyList()
                        onDismiss()
                    }
                    PixelButton("CLEAR") { pending = emptyList() }
                }
            }

            if (vm.csvLists.isNotEmpty()) {
                SectionTitle("ALREADY IMPORTED")
                vm.csvLists.forEach { list ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(list.platform.ifBlank { "No platform" }, color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Hint("${"%,d".format(list.games.size)} game${if (list.games.size == 1) "" else "s"}")
                        }
                        PixelButton("REMOVE") { vm.removeCsvList(list.platform) }
                    }
                }
                Hint("Importing a file for a platform that's already here replaces that list.")
            }
            PixelButton("CLOSE", onClick = onDismiss)
        }
    }
}
