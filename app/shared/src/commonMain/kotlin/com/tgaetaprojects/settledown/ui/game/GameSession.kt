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
import com.tgaetaprojects.settledown.game.engine.ActionResult
import com.tgaetaprojects.settledown.game.engine.Game
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.PlayerView
import com.tgaetaprojects.settledown.game.engine.ResourceCounts
import com.tgaetaprojects.settledown.game.engine.RuleViolation
import com.tgaetaprojects.settledown.game.engine.viewFor

/** What the current player is about to build with a tap on the board in [Phase.Main]. */
enum class BuildTool { ROAD, SETTLEMENT, CITY }

/** The spots a tap on the board may pick right now. */
data class BoardTargets(
    val vertices: Set<Vertex> = emptySet(),
    val edges: Set<Edge> = emptySet(),
    val hexes: Set<Hex> = emptySet(),
) {
    val isEmpty: Boolean get() = vertices.isEmpty() && edges.isEmpty() && hexes.isEmpty()
}

/**
 * A pass-and-play game on one device: every seat is local, so the session holds the whole [Game] and always acts as
 * whoever must move next ([actor]). The screen draws from [view], the actor's [PlayerView], which is replaced after
 * every accepted action so Compose sees the change.
 *
 * A networked client will hold only a [PlayerView]; the legal-spot queries here are the part that would then move
 * behind the server.
 */
class GameSession(private val game: Game) {
    var view: PlayerView by mutableStateOf(game.viewFor(actorOf(game)))
        private set

    /** The newest events first, as the acting player saw them. */
    val log = mutableStateListOf<String>()

    /** What [Phase.Main] taps build. Cleared after every accepted action. */
    var tool: BuildTool? by mutableStateOf(null)

    /** Who must act next: the roller, the next player owing a discard, or the current player. */
    val actor: PlayerColor get() = view.viewer

    val phase: Phase get() = view.phase

    /** Applies [action] as [actor] and returns why it was refused, or `null` if it was accepted. */
    fun act(action: Action): RuleViolation? {
        val actor = actor
        return when (val result = game.apply(actor, action)) {
            is ActionResult.Rejected -> result.violation
            is ActionResult.Accepted -> {
                val names = result.eventsFor(actor).map { describe(it) }
                log.addAll(0, names.asReversed())
                while (log.size > LOG_LIMIT) log.removeAt(log.lastIndex)
                tool = null
                view = game.viewFor(actorOf(game))
                null
            }
        }
    }

    fun targets(): BoardTargets {
        val actor = actor
        return when (phase) {
            is Phase.SetupSettlement -> BoardTargets(vertices = game.legalSettlementSpots(actor).toSet())
            is Phase.SetupRoad, is Phase.RoadBuilding -> BoardTargets(edges = game.legalRoadSpots(actor).toSet())
            is Phase.MoveRobber -> BoardTargets(hexes = Hex.entries.toSet() - view.robber)
            Phase.Main -> when (tool) {
                BuildTool.ROAD -> BoardTargets(edges = game.legalRoadSpots(actor).toSet())
                BuildTool.SETTLEMENT -> BoardTargets(vertices = game.legalSettlementSpots(actor).toSet())
                BuildTool.CITY -> BoardTargets(vertices = game.legalCitySpots(actor).toSet())
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
    fun robberVictims(hex: Hex): Set<PlayerColor> = game.robberVictims(hex)

    fun maritimeRate(resource: Resource): Int = game.maritimeRate(actor, resource)

    /** Whether the actor could play a [type] card now: right phase, none played yet this turn, and one held. */
    fun canPlay(type: DevelopmentCardType): Boolean =
        (phase == Phase.PreRoll || phase == Phase.Main) && !view.devCardPlayedThisTurn &&
                type != DevelopmentCardType.VICTORY_POINT && game.playableCount(actor, type) > 0

    /** The actor's points, hidden victory point cards included; others only see [PlayerView.publicVictoryPoints]. */
    fun ownVictoryPoints(): Int = game.totalVictoryPoints(actor)

    /** The cost of a [tool], for showing next to it. */
    fun cost(tool: BuildTool): ResourceCounts = when (tool) {
        BuildTool.ROAD -> ResourceCounts.ROAD
        BuildTool.SETTLEMENT -> ResourceCounts.SETTLEMENT
        BuildTool.CITY -> ResourceCounts.CITY
    }

    companion object {
        private const val LOG_LIMIT = 60

        private fun actorOf(game: Game): PlayerColor = when (val p = game.phase) {
            is Phase.Discard -> game.turnOrder.first { it in p.pending }
            else -> game.currentPlayer
        }

        fun new(players: Int, boardSeed: Long, gameSeed: Long): GameSession =
            GameSession(Game(PlayerColor.entries.take(players), boardSeed, gameSeed))

        /**
         * A game played forward a few turns by a simple greedy script, for previews and screenshot tests. It ends in
         * [Phase.Main] of a later turn, with buildings, roads and cards to show.
         */
        fun demo(turns: Int = 10): GameSession {
            val session = new(players = 4, boardSeed = 2026L, gameSeed = 7L)
            session.playScripted(turns)
            return session
        }
    }
}

/** Plays greedily until turn [turns] has been rolled; deterministic for a given game. */
internal fun GameSession.playScripted(turns: Int) {
    var steps = 0
    while (steps++ < 2_000) {
        val p = phase
        val hand = view.hand
        val violation = when (p) {
            is Phase.RollForFirstPlayer, Phase.PreRoll -> act(Action.RollDice)
            is Phase.SetupSettlement -> tapVertex(targets().vertices.maxBy { pips(it) * 100 - it.ordinal })
            is Phase.SetupRoad, is Phase.RoadBuilding -> tapEdge(targets().edges.minBy { it.ordinal })
            is Phase.Discard -> act(Action.Discard(firstCards(hand, p.pending.getValue(actor))))
            is Phase.MoveRobber -> {
                val hex = targets().hexes.maxBy { robberVictims(it).size * 100 - it.ordinal }
                act(Action.MoveRobber(hex, robberVictims(hex).minByOrNull { it.ordinal }))
            }

            Phase.Main -> {
                if (view.turn >= turns) return
                buildSomething() ?: act(Action.EndTurn)
            }

            is Phase.GameOver -> return
        }
        check(violation == null) { "Scripted play was refused: $violation in $p" }
    }
    error("Scripted play didn't finish")
}

private fun GameSession.buildSomething(): RuleViolation? {
    val hand = view.hand
    for (tool in listOf(BuildTool.CITY, BuildTool.SETTLEMENT, BuildTool.ROAD)) {
        if (!hand.covers(cost(tool))) continue
        this.tool = tool
        val targets = targets()
        val pick = targets.vertices.maxByOrNull { pips(it) * 100 - it.ordinal }
        if (pick != null) return tapVertex(pick)
        val road = targets.edges.minByOrNull { it.ordinal }
        if (road != null) return tapEdge(road)
    }
    tool = null
    return null
}

private fun GameSession.pips(vertex: Vertex): Int =
    vertex.hexes.sumOf { hex -> view.layout.numbers.getValue(hex).let { pipCount(it.value) } }

/** The dots under a number disc: how many of the 36 dice outcomes roll [total]. */
internal fun pipCount(total: Int): Int = if (total == 7) 0 else 6 - kotlin.math.abs(7 - total)

private fun firstCards(hand: ResourceCounts, count: Int): ResourceCounts {
    var left = count
    var cards = ResourceCounts.EMPTY
    for (r in Resource.entries) {
        val take = minOf(left, hand[r])
        cards += ResourceCounts.of(r, take)
        left -= take
    }
    return cards
}
