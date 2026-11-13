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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.Duplicates
import com.backlogquest.app.data.WishItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists imported games that are already in your wishlist (same title, same
 * platform) or already in your games, so the repeats can be deleted in one go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesSheet(vm: BacklogViewModel, onDismiss: () -> Unit) {
    // Worked out in the background: with a big wishlist it takes a moment.
    var found by remember { mutableStateOf<Pair<List<Duplicates.Group>, List<WishItem>>?>(null) }
    LaunchedEffect(vm.ref, vm.state.hiddenWish, vm.state.games) {
        found = null
        val ref = vm.ref
        val st = vm.state
        val pictures = vm.wishImages.toMap()
        found = withContext(Dispatchers.Default) {
            Duplicates.groups(ref.wishlist, st.hiddenWish, pictures) to
                Duplicates.inLibrary(ref.wishlist, st.hiddenWish, Duplicates.libraryTitles(st, ref))
        }
    }
    val groups = found?.first.orEmpty()
    val owned = found?.second.orEmpty()
    // Ticked for deleting: every repeated copy to start with. Games you already own only if you tick them,
    // in case you want another platform's version.
    var ticked by remember(groups) { mutableStateOf(groups.flatMap { g -> g.extras.map { it.id } }.toSet()) }
    fun toggle(id: String) {
        ticked = if (id in ticked) ticked - id else ticked + id
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
            SectionTitle("DUPLICATES")
            Hint(
                "Imported games that are in your wishlist more than once (same title on the same platform), " +
                    "and imported games you already have. Only imported games are deleted; " +
                    "your data file's own entries are never touched.",
            )
            if (found == null) {
                Text("Checking your wishlist…", color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            } else if (groups.isEmpty() && owned.isEmpty()) {
                Text("No duplicates found.", color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            if (groups.isNotEmpty()) {
                SectionTitle("IN YOUR WISHLIST TWICE · ${"%,d".format(groups.size)}")
                Hint("The copy marked KEEP stays: one from your data file if there is one, else one with a picture.")
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(10.dp)).background(BQ.Shell),
                ) {
                    items(groups, key = { "g:" + it.keep.id }) { g ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            DupRow(g.keep, vm, checked = null)
                            g.extras.forEach { e -> DupRow(e, vm, checked = e.id in ticked) { toggle(e.id) } }
                        }
                    }
                }
            }
            if (owned.isNotEmpty()) {
                SectionTitle("ALREADY IN YOUR GAMES · ${"%,d".format(owned.size)}")
                Hint("Tick the ones to delete. They match a game in your games list or history by title.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabChip("Tick all", false) { ticked = ticked + owned.map { it.id } }
                    TabChip("Untick all", false) { ticked = ticked - owned.map { it.id }.toSet() }
                }
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(10.dp)).background(BQ.Shell),
                ) {
                    items(owned, key = { "o:" + it.id }) { w -> DupRow(w, vm, checked = w.id in ticked) { toggle(w.id) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("DELETE ${"%,d".format(ticked.size)}", kind = BtnKind.A, enabled = ticked.isNotEmpty()) {
                    val n = ticked.size
                    vm.removeImported(ticked)
                    vm.showToast("DELETED ${"%,d".format(n)} GAME${if (n == 1) "" else "S"}")
                    onDismiss()
                }
                PixelButton("CLOSE", onClick = onDismiss)
            }
        }
    }
}

/** One wishlist entry: a tick box (or KEEP), its picture, title, source and platforms. */
@Composable
private fun DupRow(w: WishItem, vm: BacklogViewModel, checked: Boolean?, onToggle: (() -> Unit)? = null) {
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onToggle != null) base.clickable(onClick = onToggle) else base).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (checked == null) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { StatusLabel("KEEP", BQ.OkBg, BQ.Ok) }
        } else {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                Text(if (checked) "☑" else "☐", color = if (checked) BQ.B else BQ.Muted, fontSize = 20.sp)
            }
        }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(5.dp)).background(BQ.Edge)) {
            val picture = vm.wishImage(w.id)
            if (picture.isNotBlank()) {
                AsyncImage(picture, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                w.title, color = if (checked == false) BQ.Muted else BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (w.imported) w.source else "Your wishlist") + " · " + w.platforms.joinToString(", ").ifBlank { "No platform" },
                color = BQ.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
