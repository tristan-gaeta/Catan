package com.tgaetaprojects.settledown.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tgaetaprojects.settledown.game.DevelopmentCardType
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.ResourceCounts
import com.tgaetaprojects.settledown.game.engine.RuleViolation
import kotlinx.coroutines.launch

/** Which pop-up the game screen is showing, besides the discard dialog that [Phase.Discard] forces. */
private sealed interface Popup {
    data class ChooseVictim(val hex: Hex) : Popup
    data object MaritimeTrade : Popup
    data object YearOfPlenty : Popup
    data object Monopoly : Popup
}

/**
 * A pass-and-play game: the board, and a panel for whoever must act next. Wide windows put the panel beside the
 * board; phones put it underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(session: GameSession, onNewGame: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var popup by remember { mutableStateOf<Popup?>(null) }
    val report: (RuleViolation?) -> Unit = { violation ->
        if (violation != null) scope.launch { snackbar.showSnackbar(violation.message()) }
    }
    val act: (Action) -> Unit = { report(session.act(it)) }

    val view = session.view
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settledown", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (view.turn == 0) "Setup" else "Turn ${view.turn}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = { TextButton(onClick = onNewGame) { Text("New game") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth >= 720.dp && maxWidth > maxHeight
            val panelMaxHeight = maxHeight * 0.55f
            val board = @Composable { modifier: Modifier ->
                BoardCanvas(
                    view = view,
                    targets = session.targets(),
                    highlight = MaterialTheme.colorScheme.primary,
                    darkSea = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
                    onVertex = { report(session.tapVertex(it)) },
                    onEdge = { report(session.tapEdge(it)) },
                    onHex = { hex ->
                        val victims = session.robberVictims(hex)
                        if (victims.size > 1) popup = Popup.ChooseVictim(hex)
                        else act(Action.MoveRobber(hex, victims.singleOrNull()))
                    },
                    modifier = modifier.padding(8.dp),
                )
            }
            val panel = @Composable { modifier: Modifier ->
                Column(
                    modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatusCard(session)
                    ActionPanel(session, act, onPopup = { popup = it }, onNewGame = onNewGame)
                    HandSection(session)
                    PlayersSection(session)
                    LogSection(session)
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    board(Modifier.weight(1f).fillMaxHeight())
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        panel(Modifier.width(400.dp).fillMaxHeight())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    board(Modifier.fillMaxWidth().weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        panel(Modifier.fillMaxWidth().heightIn(max = panelMaxHeight))
                    }
                }
            }
        }
    }

    val phase = session.phase
    if (phase is Phase.Discard) {
        DiscardDialog(session.actor, view.hand, phase.pending.getValue(session.actor)) {
            act(Action.Discard(it))
        }
    }
    when (val p = popup) {
        is Popup.ChooseVictim -> VictimDialog(
            victims = session.robberVictims(p.hex),
            onPick = { popup = null; act(Action.MoveRobber(p.hex, it)) },
            onDismiss = { popup = null },
        )

        Popup.MaritimeTrade -> MaritimeTradeDialog(
            hand = view.hand,
            bank = view.bank,
            rate = session::maritimeRate,
            onTrade = { give, get -> popup = null; act(Action.MaritimeTrade(give, get)) },
            onDismiss = { popup = null },
        )

        Popup.YearOfPlenty -> YearOfPlentyDialog(
            bank = view.bank,
            onPick = { a, b -> popup = null; act(Action.PlayYearOfPlenty(a, b)) },
            onDismiss = { popup = null },
        )

        Popup.Monopoly -> MonopolyDialog(
            onPick = { popup = null; act(Action.PlayMonopoly(it)) },
            onDismiss = { popup = null },
        )

        null -> {}
    }
}

@Composable
private fun StatusCard(session: GameSession) {
    val view = session.view
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayerDot(session.actor, size = 20.dp)
            Text(
                prompt(session.phase, session.actor),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).testTag("prompt"),
            )
            view.lastRoll?.let { roll ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Text(
                        roll.value.toString(),
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).testTag("lastRoll"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionPanel(
    session: GameSession,
    act: (Action) -> Unit,
    onPopup: (Popup) -> Unit,
    onNewGame: () -> Unit,
) {
    val view = session.view
    when (val phase = session.phase) {
        is Phase.RollForFirstPlayer -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (phase.rolls.isNotEmpty()) {
                Text(
                    phase.rolls.entries.joinToString { (p, roll) -> "${p.label} ${roll.value}" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(onClick = { act(Action.RollDice) }, Modifier.fillMaxWidth()) { Text("Roll dice") }
        }

        Phase.PreRoll -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { act(Action.RollDice) }, Modifier.fillMaxWidth()) { Text("Roll dice") }
            DevCardButtons(session, act, onPopup)
        }

        Phase.Main -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Build", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BuildTool.entries.forEachIndexed { i, tool ->
                    SegmentedButton(
                        selected = session.tool == tool,
                        onClick = { session.tool = if (session.tool == tool) null else tool },
                        shape = SegmentedButtonDefaults.itemShape(i, BuildTool.entries.size),
                        enabled = view.hand.covers(session.cost(tool)),
                        modifier = Modifier.testTag("build-${tool.name.lowercase()}"),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tool.label, maxLines = 1)
                            CostPips(session.cost(tool))
                        }
                    }
                }
            }
            if (session.tool != null) {
                Text(
                    if (session.targets().isEmpty) "There's nowhere to build that." else "Tap a highlighted spot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { act(Action.BuyDevCard) },
                    enabled = view.hand.covers(ResourceCounts.DEV_CARD) && view.devCardsRemaining > 0,
                ) { Text("Buy card (${view.devCardsRemaining})") }
                FilledTonalButton(onClick = { onPopup(Popup.MaritimeTrade) }) { Text("Trade with bank") }
            }
            DevCardButtons(session, act, onPopup)
            Button(onClick = { act(Action.EndTurn) }, Modifier.fillMaxWidth().testTag("endTurn")) {
                Text("End turn")
            }
        }

        is Phase.GameOver -> Button(onClick = onNewGame, Modifier.fillMaxWidth()) { Text("New game") }

        is Phase.SetupSettlement, is Phase.SetupRoad, is Phase.Discard, is Phase.MoveRobber,
        is Phase.RoadBuilding -> Text(
            "Tap a highlighted spot on the board.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DevCardButtons(session: GameSession, act: (Action) -> Unit, onPopup: (Popup) -> Unit) {
    val held = DevelopmentCardType.entries.filter {
        it != DevelopmentCardType.VICTORY_POINT && session.view.devCards[it] > 0
    }
    if (held.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (type in held) {
            OutlinedButton(
                onClick = {
                    when (type) {
                        DevelopmentCardType.KNIGHT -> act(Action.PlayKnight)
                        DevelopmentCardType.ROAD_BUILDING -> act(Action.PlayRoadBuilding)
                        DevelopmentCardType.YEAR_OF_PLENTY -> onPopup(Popup.YearOfPlenty)
                        DevelopmentCardType.MONOPOLY -> onPopup(Popup.Monopoly)
                        DevelopmentCardType.VICTORY_POINT -> {}
                    }
                },
                enabled = session.canPlay(type),
            ) { Text("Play ${type.label}") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandSection(session: GameSession) {
    val view = session.view
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${session.actor.label}'s hand", style = MaterialTheme.typography.titleSmall)
        HandRow(view.hand)
        val cards = DevelopmentCardType.entries.filter { view.devCards[it] > 0 }
        if (cards.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (type in cards) {
                    val count = view.devCards[type]
                    val fresh = view.boughtThisTurn[type]
                    AssistChip(
                        onClick = {},
                        label = {
                            Text("${type.label} ×$count" + if (fresh > 0) " ($fresh new)" else "")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayersSection(session: GameSession) {
    val view = session.view
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Players", style = MaterialTheme.typography.titleSmall)
        for (color in view.turnOrder) {
            val summary = view.summary(color)
            val current = color == view.currentPlayer
            val points = if (color == session.actor) session.ownVictoryPoints() else view.publicVictoryPoints(color)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlayerDot(color)
                    Column(Modifier.weight(1f)) {
                        Text(color.label, style = MaterialTheme.typography.titleSmall)
                        val awards = listOfNotNull(
                            "Longest road".takeIf { view.longestRoadHolder == color },
                            "Largest army".takeIf { view.largestArmyHolder == color },
                        )
                        Text(
                            "${summary.handSize} cards · ${summary.devCardCount} dev · " +
                                    "${summary.knightsPlayed} knights" +
                                    awards.joinToString("") { " · $it" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "$points VP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogSection(session: GameSession) {
    if (session.log.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Recent", style = MaterialTheme.typography.titleSmall)
        HorizontalDivider()
        for (line in session.log.take(8)) {
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier)
    }
}
