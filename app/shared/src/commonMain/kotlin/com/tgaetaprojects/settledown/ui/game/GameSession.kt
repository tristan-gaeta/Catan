package com.tgaetaprojects.settledown.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tgaetaprojects.settledown.game.DiceTotal
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Vertex
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.ActionResult
import com.tgaetaprojects.settledown.game.engine.Game
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.PlayerView
import com.tgaetaprojects.settledown.game.engine.RuleViolation
import com.tgaetaprojects.settledown.game.engine.viewFor

/**
 * A pass-and-play game on one device: every seat is local, so the session holds the whole [Game] and always acts as
 * whoever must move next. Its [view] is that player's, replaced after every accepted action.
 *
 * Everyone sees the screen, so the shared log only shows public events, and [needsHandOff] asks the screen to hide the
 * next player's cards until they take the device.
 */
class GameSession(private val game: Game) : GameController() {
    override var view: PlayerView by mutableStateOf(game.viewFor(actorOf(game)))
        private set

    /** The player who last confirmed they hold the device. */
    var revealedTo: PlayerColor? by mutableStateOf(null)
        private set

    /** The opening roll-off and the end of the game reveal nothing private, so they don't ask. */
    override val needsHandOff: Boolean
        get() = phase !is Phase.RollForFirstPlayer && phase !is Phase.GameOver && actor != revealedTo

    override fun reveal() {
        revealedTo = actor
    }

    /** Applies [action] as [actor] and returns why it was refused, or `null` if it was accepted. */
    override fun act(action: Action): RuleViolation? {
        return when (val result = game.apply(actor, action)) {
            is ActionResult.Rejected -> result.violation
            is ActionResult.Accepted -> {
                recordAccepted(result.events.map { it.publicView() }, game.phase)
                clearTool()
                view = game.viewFor(actorOf(game))
                null
            }
        }
    }

    companion object {
        private fun actorOf(game: Game): PlayerColor = when (val p = game.phase) {
            is Phase.Discard -> game.turnOrder.first { it in p.pending }
            else -> game.currentPlayer
        }

        fun new(players: Int, boardSeed: Long, gameSeed: Long): GameSession =
            GameSession(Game(PlayerColor.entries.take(players), boardSeed, gameSeed))

        /**
         * A game played forward by a simple greedy script, for previews and screenshot tests. It stops in
         * [Phase.Main] on turn [turns] or later, at the first turn where the actor can build a road, with the device
         * already handed to them.
         */
        fun demo(turns: Int = 10): GameSession {
            val session = new(players = 4, boardSeed = 2026L, gameSeed = 7L)
            session.playScripted(turns)
            session.reveal()
            return session
        }
    }
}

/**
 * Plays greedily until it is [Phase.Main] on turn [turns] or later and the actor could build a road somewhere, or
 * until [stopWhen] holds; deterministic for a given game.
 */
internal fun GameSession.playScripted(turns: Int, stopWhen: GameSession.() -> Boolean = { false }) {
    var steps = 0
    while (steps++ < 5_000) {
        if (stopWhen()) return
        val p = phase
        val violation = when (p) {
            is Phase.RollForFirstPlayer, Phase.PreRoll -> act(Action.RollDice)
            is Phase.SetupSettlement -> tapVertex(targets().vertices.maxBy { pips(it) * 100 - it.ordinal })
            is Phase.SetupRoad, is Phase.RoadBuilding -> tapEdge(targets().edges.minBy { it.ordinal })
            is Phase.Discard -> act(Action.Discard(view.hand.firstCards(p.pending.getValue(actor))))
            is Phase.MoveRobber -> {
                val hex = targets().hexes.maxBy { robberVictims(it).size * 100 - it.ordinal }
                act(Action.MoveRobber(hex, robberVictims(hex).minByOrNull { it.ordinal }))
            }

            Phase.Main -> {
                if (view.turn >= turns && canBuild(BuildTool.ROAD)) {
                    selectTool(BuildTool.ROAD)
                    val canPlace = !targets().isEmpty
                    selectTool(null)
                    if (canPlace) return
                }
                buildSomething() ?: act(Action.EndTurn)
            }

            is Phase.GameOver -> return
        }
        check(violation == null) { "Scripted play was refused: $violation in $p" }
    }
    error("Scripted play didn't finish")
}

private fun GameSession.buildSomething(): RuleViolation? {
    for (tool in listOf(BuildTool.CITY, BuildTool.SETTLEMENT, BuildTool.ROAD)) {
        selectTool(tool)
        if (this.tool == null) continue
        val targets = targets()
        val pick = targets.vertices.maxByOrNull { pips(it) * 100 - it.ordinal }
        if (pick != null) return tapVertex(pick)
        val road = targets.edges.minByOrNull { it.ordinal }
        if (road != null) return tapEdge(road)
    }
    selectTool(null)
    return null
}

private fun GameSession.pips(vertex: Vertex): Int =
    vertex.hexes.sumOf { hex -> pipCount(view.layout.numbers.getValue(hex).value) }

/** The dots under a number disc: how many of the 36 dice outcomes roll [total]. */
internal fun pipCount(total: Int): Int = DiceTotal.fromInt(total).pips
