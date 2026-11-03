package com.tgaetaprojects.settledown.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tgaetaprojects.settledown.ui.game.BoardCanvas
import com.tgaetaprojects.settledown.ui.game.BoardTargets
import com.tgaetaprojects.settledown.ui.game.GameSession

/**
 * The start screen: pick 3 or 4 players and a board, then start a pass-and-play game. The board preview is the one
 * the game will use.
 */
@Composable
fun HomeScreen(
    players: Int,
    boardSeed: Long,
    onPlayersChange: (Int) -> Unit,
    onShuffleBoard: () -> Unit,
    onStart: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.safeDrawingPadding().fillMaxSize(), contentAlignment = Alignment.Center) {
            val wide = maxWidth >= 720.dp && maxWidth > maxHeight
            val preview = @Composable { modifier: Modifier -> BoardPreview(boardSeed, modifier) }
            val settings = @Composable { modifier: Modifier ->
                SetupCard(players, onPlayersChange, onShuffleBoard, onStart, modifier)
            }
            if (wide) {
                Row(
                    Modifier.padding(32.dp).fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    preview(Modifier.weight(1f).fillMaxSize())
                    Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Title(TextAlign.Start)
                        settings(Modifier.fillMaxWidth())
                    }
                }
            } else {
                // Size the preview to what's left once the title and card fit, so Start stays on screen.
                val fullHeight = (maxWidth.coerceAtMost(560.dp) - 48.dp) / BOARD_ASPECT
                val previewHeight = (maxHeight - COMPACT_CHROME).coerceAtMost(fullHeight).coerceAtLeast(160.dp)
                Column(
                    Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Title(TextAlign.Center, compact = true)
                    preview(Modifier.fillMaxWidth().height(previewHeight))
                    settings(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Roughly the height of everything but the board preview in the compact layout. */
private val COMPACT_CHROME = 440.dp

/** Width over height of the drawn board, sea frame included. */
private const val BOARD_ASPECT = 1.15f

@Composable
private fun Title(align: TextAlign, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Settledown",
            style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Settle an island, trade, and build your way to 10 points.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BoardPreview(boardSeed: Long, modifier: Modifier) {
    // The game seed is irrelevant here: only the layout is drawn.
    val view = remember(boardSeed) { GameSession.new(players = 4, boardSeed = boardSeed, gameSeed = 0L).view }
    BoardCanvas(
        view = view,
        targets = BoardTargets(),
        dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
        onVertex = {},
        onEdge = {},
        onHex = {},
        modifier = modifier,
    )
}

@Composable
private fun SetupCard(
    players: Int,
    onPlayersChange: (Int) -> Unit,
    onShuffleBoard: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Players", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(3, 4).forEachIndexed { i, n ->
                    SegmentedButton(
                        selected = players == n,
                        onClick = { onPlayersChange(n) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        modifier = Modifier.testTag("players-$n"),
                    ) { Text("$n players") }
                }
            }
            Text(
                "Pass the device around: each player's cards stay hidden until they take it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onShuffleBoard, Modifier.weight(1f)) { Text("New board") }
                Button(onClick = onStart, Modifier.weight(1f).testTag("start")) { Text("Start game") }
            }
        }
    }
}
