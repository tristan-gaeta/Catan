package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.BoardLayout
import com.tgaetaprojects.biophilia.game.Building
import com.tgaetaprojects.biophilia.game.DiceTotal
import com.tgaetaprojects.biophilia.game.Edge
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.Vertex
import com.tgaetaprojects.biophilia.utils.asReadOnly

/**
 * Everything [viewer] is allowed to know about a game: the whole board and all public counts, their own cards in
 * full, and only totals for everyone else's hidden cards. It never contains the deck order or the game's random
 * state, so it is safe to send to that player's client or hand to a bot playing that seat.
 *
 * Built by [Game.viewFor]; encoded by `SnapshotCodec.encodeView`. See
 * [what may leave the server](https://github.com/tristan-gaeta/Catan/blob/main/docs/rules-engine.md#what-may-leave-the-server).
 */
data class PlayerView(
    val viewer: PlayerColor,
    val seatOrder: List<PlayerColor>,
    /** Who rolled highest at the start, or `null` while that's still being rolled. */
    val firstPlayer: PlayerColor?,
    val layout: BoardLayout,
    val turn: Int,
    val currentPlayer: PlayerColor,
    val phase: Phase,
    val lastRoll: DiceTotal?,
    val devCardPlayedThisTurn: Boolean,
    val robber: Hex,
    val roads: Map<Edge, PlayerColor>,
    val buildings: Map<Vertex, Building>,
    /** The bank's stacks are face up, so their sizes are public. */
    val bank: ResourceCounts,
    val devCardsRemaining: Int,
    val longestRoadHolder: PlayerColor?,
    val largestArmyHolder: PlayerColor?,
    val tradeOffers: List<TradeOffer>,
    /** Public facts about every player, in seat order, including the viewer. */
    val players: List<PlayerSummary>,
    val hand: ResourceCounts,
    val devCards: DevCardCounts,
    val boughtThisTurn: DevCardCounts
) {
    /** Seats in playing order, starting with the first player; the seat order until the first player is rolled. */
    val turnOrder: List<PlayerColor>
        get() = firstPlayer?.let { first ->
            val seat = seatOrder.indexOf(first)
            seatOrder.drop(seat) + seatOrder.take(seat)
        } ?: seatOrder

    fun summary(color: PlayerColor): PlayerSummary = players.first { it.color == color }

    /** Points everyone can see: buildings and the two special cards. */
    fun publicVictoryPoints(color: PlayerColor): Int =
        buildings.values.filter { it.color == color }.sumOf { it.victoryPoints } +
                (if (longestRoadHolder == color) 2 else 0) +
                (if (largestArmyHolder == color) 2 else 0)
}

/** What everyone at the table can see about one player. */
data class PlayerSummary(
    val color: PlayerColor,
    val handSize: Int,
    val devCardCount: Int,
    val boughtThisTurnCount: Int,
    val knightsPlayed: Int
)

/** The game as [viewer] may see it. */
fun Game.viewFor(viewer: PlayerColor): PlayerView {
    val me = player(viewer)
    return PlayerView(
        viewer = viewer,
        seatOrder = seatOrder,
        firstPlayer = firstPlayer,
        layout = board.layout,
        turn = turn,
        currentPlayer = currentPlayer,
        phase = phase,
        lastRoll = lastRoll,
        devCardPlayedThisTurn = devCardPlayedThisTurn,
        robber = board.robber,
        roads = board.roads.toMap().asReadOnly(),
        buildings = board.buildings.toMap().asReadOnly(),
        bank = bank,
        devCardsRemaining = devCardsRemaining,
        longestRoadHolder = longestRoadHolder,
        largestArmyHolder = largestArmyHolder,
        tradeOffers = tradeOffers,
        players = seatOrder.map(::player).map {
            PlayerSummary(
                it.color,
                it.hand.total,
                it.devCards.total,
                it.boughtThisTurn.total,
                it.knightsPlayed
            )
        },
        hand = me.hand,
        devCards = me.devCards,
        boughtThisTurn = me.boughtThisTurn
    )
}
