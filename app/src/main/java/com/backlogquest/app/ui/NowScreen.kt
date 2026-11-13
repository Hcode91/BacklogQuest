package com.backlogquest.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backlogquest.app.data.BacklogViewModel
import com.backlogquest.app.data.Game
import com.backlogquest.app.data.deviceName
import com.backlogquest.app.data.fmtNum
import com.backlogquest.app.data.playedOn

/** The first tab: only the games you're playing right now, and how long you've put into each. */
@Composable
fun NowScreen(
    vm: BacklogViewModel,
    onEdit: (String) -> Unit,
    onBeaten: (String) -> Unit,
    onSpin: () -> Unit,
) {
    val s = vm.state
    val playing = s.games.filter { it.status == "playing" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusPanel(vm) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("NOW PLAYING", Modifier.weight(1f))
                if (playing.isNotEmpty()) Hint("${playing.size} game${if (playing.size == 1) "" else "s"}")
            }
        }
        if (playing.isEmpty() && vm.loaded) {
            item {
                Tile(Modifier.fillMaxWidth()) {
                    Text("Nothing on the go", color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Hint("Spin for your next game, or set one to Playing in Games and it will show up here.")
                    PixelButton("SPIN FOR A GAME", kind = BtnKind.A, onClick = onSpin)
                }
            }
        }
        items(playing, key = { it.id }) { g ->
            NowTile(
                g,
                device = playedOn(s, g).ifBlank { deviceName(s, g.device) },
                label = g.genre.ifBlank { deviceName(s, g.device) },
                onOpen = { onEdit(g.id) },
                onResume = { if (g.launch != null) vm.play(g.id) else onEdit(g.id) },
                onBeaten = { onBeaten(g.id) },
            )
        }
        if (playing.isNotEmpty()) {
            item {
                val shape = RoundedCornerShape(10.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .border(1.dp, BQ.Edge, shape)
                        .clickable(onClick = onSpin)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ready for something else?", Modifier.weight(1f), color = BQ.Ink, fontSize = 14.sp)
                    Text("SPIN ›", color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun NowTile(
    g: Game,
    device: String,
    label: String,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    onBeaten: () -> Unit,
) {
    Tile(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(g.title, label, g.cover, Modifier.width(84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    g.title, color = BQ.Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                StatusLabel(device.uppercase())
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${fmtNum(g.hours)}h",
                        color = BQ.B, fontFamily = BQ.Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp,
                    )
                    Text("played so far", Modifier.padding(bottom = 4.dp), color = BQ.Muted, fontSize = 12.sp, maxLines = 1)
                }
                if (g.est > 0) {
                    Text("About ${estText(g)} to beat", color = BQ.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (g.achievements.isNotEmpty()) {
                    Text(
                        "${g.achievements.count { it.earned }} of ${g.achievements.size} achievements" +
                            if (g.achSource.isBlank()) "" else " · ${g.achSource}",
                        color = BQ.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton(
                if (g.launch != null) "RESUME" else "SET UP START",
                Modifier.weight(1f), kind = BtnKind.B, onClick = onResume,
            )
            PixelButton("BEATEN IT", Modifier.weight(1f), onClick = onBeaten)
        }
    }
}
