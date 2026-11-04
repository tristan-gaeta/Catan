package com.tgaetaprojects.settledown.game.engine

import com.tgaetaprojects.settledown.game.BoardLayout
import com.tgaetaprojects.settledown.game.Building
import com.tgaetaprojects.settledown.game.DevelopmentCardType
import com.tgaetaprojects.settledown.game.DiceTotal
import com.tgaetaprojects.settledown.game.Edge
import com.tgaetaprojects.settledown.game.GameBoard
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.Vertex
import com.tgaetaprojects.settledown.utils.asReadOnly

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

    // ---- Queries for clients; the board is public, so these match the same queries on Game ----

    /** The public pieces rebuilt into a board, so these queries share the engine's placement rules. */
    private val board: GameBoard by lazy {
        GameBoard(layout).also { board ->
            for ((edge, color) in roads) board.placeRoad(edge, color)
            for ((vertex, building) in buildings) board.placeBuilding(vertex, building)
            board.robber = robber
        }
    }

    /** Whether the game is waiting on [viewer]: to discard, or otherwise because it's their turn. */
    val mustAct: Boolean
        get() = when (val p = phase) {
            is Phase.Discard -> viewer in p.pending
            is Phase.GameOver -> false
            else -> currentPlayer == viewer
        }

    /** Throws for a color that isn't playing, like [Game.player]. */
    fun settlementsLeft(color: PlayerColor): Int {
        requireSeated(color)
        return PlayerState.SETTLEMENTS - buildings.values.count { it == Building.settlement(color) }
    }

    /** Throws for a color that isn't playing, like [Game.player]. */
    fun citiesLeft(color: PlayerColor): Int {
        requireSeated(color)
        return PlayerState.CITIES - buildings.values.count { it == Building.city(color) }
    }

    /** Throws for a color that isn't playing, like [Game.player]. */
    fun roadsLeft(color: PlayerColor): Int {
        requireSeated(color)
        return PlayerState.ROADS - roads.values.count { it == color }
    }

    private fun requireSeated(color: PlayerColor) {
        require(color in seatOrder) { "$color is not playing" }
    }

    /** See [Game.legalSettlementSpots]. */
    fun legalSettlementSpots(color: PlayerColor = viewer): List<Vertex> =
        board.legalSettlementSpots(color, settlementsLeft(color), phase)

    /** See [Game.legalRoadSpots]. */
    fun legalRoadSpots(color: PlayerColor = viewer): List<Edge> =
        board.legalRoadSpots(color, roadsLeft(color), phase, currentPlayer)

    /** See [Game.legalCitySpots]. */
    fun legalCitySpots(color: PlayerColor = viewer): List<Vertex> =
        board.legalCitySpots(color, citiesLeft(color))

    /** See [Game.maritimeRate]. */
    fun maritimeRate(resource: Resource, color: PlayerColor = viewer): Int =
        board.maritimeRate(color, resource)

    /** See [Game.robberVictims]. */
    fun robberVictims(hex: Hex): Set<PlayerColor> = board.robberVictims(hex, currentPlayer)

    /** How many of [viewer]'s cards of [type] could be played, ignoring timing: held minus bought this turn. */
    fun playableCount(type: DevelopmentCardType): Int = devCards[type] - boughtThisTurn[type]

    /** [viewer]'s real score: [publicVictoryPoints] plus their own hidden victory point cards. */
    val ownVictoryPoints: Int
        get() = publicVictoryPoints(viewer) + devCards[DevelopmentCardType.VICTORY_POINT]
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
