package com.tgaetaprojects.settledown.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tgaetaprojects.settledown.game.DevelopmentCardType
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.ResourceCounts
import com.tgaetaprojects.settledown.game.engine.RuleViolation
import com.tgaetaprojects.settledown.ui.theme.GameColors
import com.tgaetaprojects.settledown.ui.theme.SettledownTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which pop-up the game screen is showing, besides the discard dialog that [Phase.Discard] forces. */
private sealed interface Popup {
    data class ChooseVictim(val hex: Hex) : Popup
    data object MaritimeTrade : Popup
    data object YearOfPlenty : Popup
    data object Monopoly : Popup
    data object LeaveGame : Popup
}

/**
 * A game in progress: the board, and a panel for the player this screen acts for. Wide windows put the panel beside
 * the board; phones put it underneath. In a pass-and-play game, a cover hides the next player's cards until they take
 * the device.
 *
 * @param leaveLabel what the top bar's button says; it calls [onNewGame], after showing [leaveWarning] if the game is
 *   still going.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    session: GameController,
    onNewGame: () -> Unit,
    leaveLabel: String = "New game",
    leaveWarning: String = "The game in progress will be lost.",
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var popup by remember { mutableStateOf<Popup?>(null) }
    val report: (RuleViolation?) -> Unit = { violation ->
        if (violation != null) scope.launch { snackbar.showSnackbar(violation.message()) }
    }
    val act: (Action) -> Unit = { report(session.act(it)) }
    LaunchedEffect(session.refusal) { session.refusal?.let { snackbar.showSnackbar(it.violation.message()) } }
    // a choice being made when the turn moved on, say when the timer ran out, can no longer be played
    LaunchedEffect(session.canAct) { if (!session.canAct && popup != Popup.LeaveGame) popup = null }

    if (session.needsHandOff) {
        HandOffCover(session)
        return
    }

    val view = session.view
    val targets = session.targets()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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
                actions = {
                    TextButton(
                        onClick = { if (session.phase is Phase.GameOver) onNewGame() else popup = Popup.LeaveGame },
                    ) { Text(leaveLabel) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        // the panel pads itself for the navigation bar, so its background runs to the bottom edge
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
        ),
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth >= 720.dp && maxWidth > maxHeight
            val panelMaxHeight = maxHeight * 0.5f
            val board = @Composable { modifier: Modifier ->
                BoardCanvas(
                    view = view,
                    targets = targets,
                    dark = dark,
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
                    modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    session.notice?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("sessionNotice")) }
                    StatusCard(session)
                    HandSection(session)
                    ActionPanel(session, targets, act, onPopup = { popup = it }, onNewGame = onNewGame)
                    PlayersSection(session)
                    LogSection(session)
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    board(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding())
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        panel(Modifier.width(400.dp).fillMaxHeight())
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    board(Modifier.fillMaxWidth().weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    ) {
                        panel(Modifier.fillMaxWidth().heightIn(max = panelMaxHeight))
                    }
                }
            }
        }
    }

    val phase = session.phase
    if (phase is Phase.Discard && session.canAct) {
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

        Popup.LeaveGame -> LeaveGameDialog(
            warning = leaveWarning,
            onLeave = { popup = null; onNewGame() },
            onDismiss = { popup = null },
        )

        null -> {}
    }
}

/** Fills the screen in the next player's color until they take the device, so nobody sees another's cards. */
@Composable
private fun HandOffCover(session: GameController) {
    val color = session.actor
    val ink = GameColors.onPlayer(color)
    Surface(color = GameColors.player(color), contentColor = ink, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Pass the device to", style = MaterialTheme.typography.titleLarge)
                Text(
                    color.label,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("handOffPlayer"),
                )
                Text(
                    prompt(session.phase, color),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = session::reveal,
                    colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = GameColors.player(color)),
                    modifier = Modifier.padding(top = 16.dp).height(56.dp).testTag("reveal"),
                ) { Text("I'm ${color.label}, show my cards", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun StatusCard(session: GameController) {
    val view = session.view
    val playerColor = GameColors.player(session.spotlight)
    val container = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, playerColor, 0.22f)
    Surface(color = container, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(8.dp).height(72.dp).background(playerColor))
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(36.dp)
                        .background(playerColor, CircleShape)
                        .border(2.dp, GameColors.pieceOutline, CircleShape)
                )
                Text(
                    session.statusText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).testTag("prompt"),
                )
                session.deadline?.let { Countdown(it) }
                view.lastRoll?.let { roll -> RollTile(roll.value) }
            }
        }
    }
}

/** The seconds left before the server moves for whoever it's waiting on. */
@Composable
private fun Countdown(deadline: TurnDeadline) {
    var seconds by remember(deadline) { mutableStateOf(deadline.secondsLeft()) }
    LaunchedEffect(deadline) {
        while (true) {
            seconds = deadline.secondsLeft()
            delay(250)
        }
    }
    Text(
        "${seconds}s",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag("countdown"),
    )
}

/** This turn's dice total on a die-like tile. The engine reports totals, not the two dice. */
@Composable
private fun RollTile(total: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GameColors.token,
        contentColor = if (total == 6 || total == 8) GameColors.tokenHot else GameColors.tokenText,
        shadowElevation = 3.dp,
        modifier = Modifier.semantics { contentDescription = "Rolled $total" },
    ) {
        Column(
            Modifier.size(52.dp).testTag("lastRoll"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("ROLL", style = MaterialTheme.typography.labelSmall, color = GameColors.tokenText.copy(alpha = 0.6f))
            Text(total.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionPanel(
    session: GameController,
    targets: BoardTargets,
    act: (Action) -> Unit,
    onPopup: (Popup) -> Unit,
    onNewGame: () -> Unit,
) {
    val view = session.view
    if (!session.canAct && session.phase !is Phase.GameOver) return
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
                        onClick = { session.selectTool(if (session.tool == tool) null else tool) },
                        shape = SegmentedButtonDefaults.itemShape(i, BuildTool.entries.size),
                        enabled = session.canBuild(tool),
                        modifier = Modifier.testTag("build-${tool.name.lowercase()}"),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tool.label, maxLines = 1)
                            CostPips(tool.cost)
                        }
                    }
                }
            }
            if (session.tool != null) {
                Text(
                    if (targets.isEmpty) "There's nowhere to build that." else "Tap a highlighted spot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { act(Action.BuyDevCard) },
                    enabled = view.hand.covers(ResourceCounts.DEV_CARD) && view.devCardsRemaining > 0,
                ) { Text("Dev card · ${view.devCardsRemaining} left") }
                FilledTonalButton(
                    onClick = { onPopup(Popup.MaritimeTrade) },
                    enabled = session.canTradeWithBank(),
                ) { Text("Trade with bank") }
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
private fun DevCardButtons(session: GameController, act: (Action) -> Unit, onPopup: (Popup) -> Unit) {
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
private fun HandSection(session: GameController) {
    val view = session.view
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${session.nameOf(session.actor)}'s hand", style = MaterialTheme.typography.titleSmall)
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
private fun PlayersSection(session: GameController) {
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
                    PlayerDot(color, size = 18.dp)
                    Column(Modifier.weight(1f)) {
                        val offline = if (session.isConnected(color) == false) " (away)" else ""
                        Text(session.nameOf(color) + offline, style = MaterialTheme.typography.titleSmall)
                        val awards = listOfNotNull(
                            "Longest road".takeIf { view.longestRoadHolder == color },
                            "Largest army".takeIf { view.largestArmyHolder == color },
                        )
                        Text(
                            "${summary.handSize} cards · ${summary.devCardCount} dev · " +
                                    "${summary.knightsPlayed} knights" +
                                    awards.joinToString("") { " · $it" },
                            style = MaterialTheme.typography.bodyMedium,
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
private fun LogSection(session: GameController) {
    if (session.log.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Recent", style = MaterialTheme.typography.titleSmall)
        HorizontalDivider()
        for (line in session.log.take(8)) {
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview
@Composable
private fun GameScreenPreview() {
    val session = remember { GameSession.demo().apply { selectTool(BuildTool.ROAD) } }
    SettledownTheme(darkTheme = false) { GameScreen(session, onNewGame = {}) }
}
