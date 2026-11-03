package com.tgaetaprojects.settledown.game.engine

import com.tgaetaprojects.settledown.game.DiceTotal
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Vertex

/**
 * Where the game is, and so which [Action]s are accepted next.
 *
 * Diagram: [Engine phases](https://github.com/tristan-gaeta/Catan/blob/main/docs/images/engine-phases.svg).
 *
 * See [Rules engine](https://github.com/tristan-gaeta/Catan/blob/main/docs/rules-engine.md#phases).
 */
sealed interface Phase {
    /**
     * Before set-up: each of [contenders] rolls the dice, in seat order; [rolls] holds the rolls so far. The highest
     * roll becomes the first player. If the highest roll is tied, the tied players roll again.
     */
    data class RollForFirstPlayer(
        val contenders: List<PlayerColor>,
        val rolls: Map<PlayerColor, DiceTotal>
    ) : Phase

    /** Set-up: the current player places a free settlement. In [round] 2 it also pays out starting resources. */
    data class SetupSettlement(val round: Int) : Phase

    /** Set-up: the current player places a free road touching the [settlement] they just placed. */
    data class SetupRoad(val round: Int, val settlement: Vertex) : Phase

    /** Start of a turn: roll the dice, or play a development card first. */
    data object PreRoll : Phase

    /** A 7 was rolled: each listed player must discard that many cards before the robber moves. */
    data class Discard(val pending: Map<PlayerColor, Int>) : Phase

    /** The current player must move the robber (and rob someone), then the game resumes at [returnTo]. */
    data class MoveRobber(val returnTo: Phase) : Phase

    /** After the roll: trade and build in any order, then end the turn. */
    data object Main : Phase

    /** Road Building was played: place up to [remaining] free roads, then resume at [returnTo]. */
    data class RoadBuilding(val remaining: Int, val returnTo: Phase) : Phase

    data class GameOver(val winner: PlayerColor) : Phase
}
