package com.tgaetaprojects.settledown.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tgaetaprojects.settledown.game.DevelopmentCardType
import com.tgaetaprojects.settledown.game.Edge
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.Vertex
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.GameEvent
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.PlayerView
import com.tgaetaprojects.settledown.game.engine.ResourceCounts
import com.tgaetaprojects.settledown.game.engine.RuleViolation

/** What the current player is about to build with a tap on the board in [Phase.Main]. */
enum class BuildTool(val cost: ResourceCounts) {
    ROAD(ResourceCounts.ROAD),
    SETTLEMENT(ResourceCounts.SETTLEMENT),
    CITY(ResourceCounts.CITY),
}

/** The spots a tap on the board may pick right now. */
data class BoardTargets(
    val vertices: Set<Vertex> = emptySet(),
    val edges: Set<Edge> = emptySet(),
    val hexes: Set<Hex> = emptySet(),
) {
    val isEmpty: Boolean get() = vertices.isEmpty() && edges.isEmpty() && hexes.isEmpty()
}

/**
 * What the game screen needs from a game, wherever it's being played. Everything here works from the [view] alone, so
 * it's the same on one device as over the network; a subclass only decides where the view comes from and where actions
 * go.
 */
abstract class GameController {
    /** The game as [actor] may see it. It's replaced with each change, so Compose sees the change. */
    abstract val view: PlayerView

    /**
     * Asks to play [action] as [actor], and returns why it was refused if that's known at once. Otherwise it returns
     * `null`, which doesn't promise the action was accepted: over the network the answer comes later, in [view] or
     * [refusal].
     */
    abstract fun act(action: Action): RuleViolation?

    /** The newest events first. */
    val log = mutableStateListOf<String>()

    /** What [Phase.Main] taps build; set through [selectTool]. Cleared after the player's next accepted action. */
    var tool: BuildTool? by mutableStateOf(null)
        private set

    /** True while tied players re-roll for who goes first, even when every player tied. */
    var isTieBreak: Boolean by mutableStateOf(false)
        private set

    /** The newest refusal that arrived after [act] returned, for the screen to show once. */
    open val refusal: LateRefusal? get() = null

    /** Whether the device must be passed before the screen shows [actor]'s cards. */
    open val needsHandOff: Boolean get() = false

    /** [actor] has the device; show their cards. */
    open fun reveal() {}

    /** What to call [color] on screen. */
    open fun nameOf(color: PlayerColor): String = color.label

    /** The seat this screen shows and acts for. */
    val actor: PlayerColor get() = view.viewer

    val phase: Phase get() = view.phase

    /** Whether the game is waiting on [actor]. */
    val canAct: Boolean get() = view.mustAct

    /** The seat the status line is about: [actor] when it's their move, or else who they're waiting for. */
    val spotlight: PlayerColor
        get() = when {
            canAct -> actor
            else -> (phase as? Phase.GameOver)?.winner ?: view.currentPlayer
        }

    /** The status line: what [actor] should do, or who they're waiting for. */
    val statusText: String
        get() {
            val phase = phase
            return when {
                canAct || phase is Phase.GameOver -> prompt(phase, actor, isTieBreak)
                phase is Phase.Discard -> "Waiting for ${phase.pending.keys.joinToString { nameOf(it) }} to discard"
                else -> "Waiting for ${nameOf(view.currentPlayer)}"
            }
        }

    /** Records [events] from an accepted action: they join the log, and [phaseAfter] shows whether a roll-off tied. */
    protected fun recordAccepted(events: List<GameEvent>, phaseAfter: Phase) {
        log.addAll(0, events.map(::describe).asReversed())
        while (log.size > LOG_LIMIT) log.removeAt(log.lastIndex)
        // a roll that leaves no rolls recorded must have ended in a tie: the tied players start over
        isTieBreak = phaseAfter is Phase.RollForFirstPlayer && (isTieBreak || phaseAfter.rolls.isEmpty())
    }

    protected fun clearTool() {
        tool = null
    }

    /** Selects [tool] for the next board tap, or clears it; a tool the actor can't build now is ignored. */
    fun selectTool(tool: BuildTool?) {
        this.tool = tool?.takeIf { phase == Phase.Main && canBuild(it) }
    }

    /** Whether the actor has the cards and a piece left for [tool]. */
    fun canBuild(tool: BuildTool): Boolean {
        val piecesLeft = when (tool) {
            BuildTool.ROAD -> view.roadsLeft(actor)
            BuildTool.SETTLEMENT -> view.settlementsLeft(actor)
            BuildTool.CITY -> view.citiesLeft(actor)
        }
        return piecesLeft > 0 && view.hand.covers(tool.cost)
    }

    /** Whether the actor can make at least one trade with the bank now: enough to give, and something to get. */
    fun canTradeWithBank(): Boolean =
        phase == Phase.Main && canAct && Resource.entries.any { give ->
            view.hand[give] >= maritimeRate(give) && Resource.entries.any { it != give && view.bank[it] > 0 }
        }

    fun targets(): BoardTargets {
        if (!canAct) return BoardTargets()
        return when (phase) {
            is Phase.SetupSettlement -> BoardTargets(vertices = view.legalSettlementSpots().toSet())
            is Phase.SetupRoad, is Phase.RoadBuilding -> BoardTargets(edges = view.legalRoadSpots().toSet())
            is Phase.MoveRobber -> BoardTargets(hexes = Hex.entries.toSet() - view.robber)
            Phase.Main -> when (tool?.takeIf(::canBuild)) {
                BuildTool.ROAD -> BoardTargets(edges = view.legalRoadSpots().toSet())
                BuildTool.SETTLEMENT -> BoardTargets(vertices = view.legalSettlementSpots().toSet())
                BuildTool.CITY -> BoardTargets(vertices = view.legalCitySpots().toSet())
                null -> BoardTargets()
            }

            is Phase.RollForFirstPlayer, Phase.PreRoll, is Phase.Discard, is Phase.GameOver -> BoardTargets()
        }
    }

    fun tapVertex(vertex: Vertex): RuleViolation? = when (phase) {
        is Phase.SetupSettlement -> act(Action.PlaceSettlement(vertex))
        Phase.Main -> when (tool) {
            BuildTool.SETTLEMENT -> act(Action.BuildSettlement(vertex))
            BuildTool.CITY -> act(Action.BuildCity(vertex))
            else -> null
        }

        else -> null
    }

    fun tapEdge(edge: Edge): RuleViolation? = when (phase) {
        is Phase.SetupRoad, is Phase.RoadBuilding -> act(Action.PlaceRoad(edge))
        Phase.Main -> if (tool == BuildTool.ROAD) act(Action.BuildRoad(edge)) else null
        else -> null
    }

    /** The players the actor could rob by moving the robber to [hex]. */
    fun robberVictims(hex: Hex): Set<PlayerColor> = view.robberVictims(hex)

    fun maritimeRate(resource: Resource): Int = view.maritimeRate(resource)

    /** Whether the actor could play a [type] card now: right phase, none played yet this turn, and one held. */
    fun canPlay(type: DevelopmentCardType): Boolean =
        canAct && (phase == Phase.PreRoll || phase == Phase.Main) && !view.devCardPlayedThisTurn &&
                type != DevelopmentCardType.VICTORY_POINT && view.playableCount(type) > 0

    /** The actor's points, hidden victory point cards included; others only see [PlayerView.publicVictoryPoints]. */
    fun ownVictoryPoints(): Int = view.ownVictoryPoints

    private companion object {
        const val LOG_LIMIT = 60
    }
}

/** A refused action. Each one is a new instance, so the same refusal twice still shows twice. */
class LateRefusal(val violation: RuleViolation)
