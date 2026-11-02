package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.PlayerColor

/**
 * One player's private and public state. Only [Game] mutates it; victory points depend on board-wide awards, so
 * they're computed by [Game.publicVictoryPoints] and [Game.totalVictoryPoints].
 */
class PlayerState internal constructor(val color: PlayerColor) {
    var hand: ResourceCounts = ResourceCounts.EMPTY
        internal set

    /** Every unplayed development card held, including ones bought this turn. */
    var devCards: DevCardCounts = DevCardCounts.EMPTY
        internal set

    /** Cards bought during the current turn, which can't be played until the next one. */
    var boughtThisTurn: DevCardCounts = DevCardCounts.EMPTY
        internal set

    var knightsPlayed: Int = 0
        internal set

    var settlementsLeft: Int = SETTLEMENTS
        internal set
    var citiesLeft: Int = CITIES
        internal set
    var roadsLeft: Int = ROADS
        internal set

    /** Cached length of this player's longest road, refreshed whenever a road or settlement is built. */
    var longestRoad: Int = 0
        internal set

    override fun toString(): String = "PlayerState($color, hand=$hand, devCards=$devCards, knights=$knightsPlayed)"

    companion object {
        const val SETTLEMENTS = 5
        const val CITIES = 4
        const val ROADS = 15
    }
}
