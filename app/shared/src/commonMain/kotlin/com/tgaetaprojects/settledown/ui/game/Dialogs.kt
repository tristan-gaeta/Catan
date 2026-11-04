package com.tgaetaprojects.settledown.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.engine.ResourceCounts

/** Choose exactly [owed] cards from [hand]. It can't be dismissed: the game waits for the discard. */
@Composable
fun DiscardDialog(player: PlayerColor, hand: ResourceCounts, owed: Int, onDiscard: (ResourceCounts) -> Unit) {
    var picked by remember(player, hand) { mutableStateOf(ResourceCounts.EMPTY) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("${player.label}, discard $owed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("A 7 was rolled and you hold ${hand.total} cards.")
                Spacer(Modifier.height(4.dp))
                for (r in Resource.entries) {
                    ResourceStepper(
                        r, picked[r], max = minOf(hand[r], picked[r] + owed - picked.total), held = hand[r],
                    ) {
                        picked = picked.with(r, it)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onDiscard(picked) }, enabled = picked.total == owed) {
                Text("Discard ${picked.total} of $owed")
            }
        },
    )
}

/** Pick who to rob after moving the robber next to several opponents. */
@Composable
fun VictimDialog(victims: Set<PlayerColor>, onPick: (PlayerColor) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rob whom?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (victim in victims) {
                    TextButton(onClick = { onPick(victim) }) {
                        PlayerDot(victim)
                        Spacer(Modifier.width(12.dp))
                        Text(victim.label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Trade with the bank at the actor's best rate for each resource. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaritimeTradeDialog(
    hand: ResourceCounts,
    bank: ResourceCounts,
    rate: (Resource) -> Int,
    onTrade: (give: Resource, get: Resource) -> Unit,
    onDismiss: () -> Unit,
) {
    var give by remember { mutableStateOf<Resource?>(null) }
    var get by remember { mutableStateOf<Resource?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trade with the bank") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Give", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in Resource.entries) {
                        FilterChip(
                            selected = give == r,
                            onClick = { give = r; if (get == r) get = null },
                            enabled = hand[r] >= rate(r),
                            label = { Text("${rate(r)} ${r.label}") },
                        )
                    }
                }
                Text("Get", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in Resource.entries) {
                        FilterChip(
                            selected = get == r,
                            onClick = { get = r },
                            enabled = r != give && bank[r] > 0,
                            label = { Text("1 ${r.label}") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            val g = give
            val w = get
            Button(onClick = { onTrade(g!!, w!!) }, enabled = g != null && w != null) { Text("Trade") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Year of Plenty: take any two resources from the bank. */
@Composable
fun YearOfPlentyDialog(bank: ResourceCounts, onPick: (Resource, Resource) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(ResourceCounts.EMPTY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Year of plenty") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Take any 2 resources from the bank.")
                for (r in Resource.entries) {
                    ResourceStepper(r, picked[r], max = minOf(bank[r], picked[r] + 2 - picked.total)) {
                        picked = picked.with(r, it)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val first = picked.cardAt(0)
                    onPick(first, (picked - first).cardAt(0))
                },
                enabled = picked.total == 2,
            ) { Text("Take") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Monopoly: name a resource and take every card of it from the other players. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonopolyDialog(onPick: (Resource) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf<Resource?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monopoly") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Every other player gives you all their cards of one resource.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in Resource.entries) {
                        FilterChip(selected = picked == r, onClick = { picked = r }, label = { Text(r.label) })
                    }
                }
            }
        },
        confirmButton = {
            val p = picked
            Button(onClick = { onPick(p!!) }, enabled = p != null) { Text("Take") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Confirms leaving a game in progress, which can't be undone. */
@Composable
fun LeaveGameDialog(warning: String, onLeave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave this game?") },
        text = { Text(warning) },
        confirmButton = { TextButton(onClick = onLeave) { Text("Leave") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep playing") } },
    )
}
