package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.Vertex

/** Where the game is, and so which [Action]s are accepted next. */
sealed interface Phase {
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
