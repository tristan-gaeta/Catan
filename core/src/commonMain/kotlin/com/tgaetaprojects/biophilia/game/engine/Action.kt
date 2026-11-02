package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.Edge
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.Resource
import com.tgaetaprojects.biophilia.game.Vertex

/**
 * Something a player asks the [Game] to do. Unless noted, only the current player may act, and only in the
 * phase named.
 */
sealed interface Action {
    /** [Phase.SetupSettlement]: place a free settlement. */
    data class PlaceSettlement(val vertex: Vertex) : Action

    /** [Phase.SetupRoad] or [Phase.RoadBuilding]: place a free road. */
    data class PlaceRoad(val edge: Edge) : Action

    /** [Phase.PreRoll], or [Phase.RollForFirstPlayer] when it's your roll. */
    data object RollDice : Action

    /** [Phase.Discard]: any player who still owes a discard, giving exactly the amount owed. */
    data class Discard(val cards: ResourceCounts) : Action

    /** [Phase.MoveRobber]: [victim] must be named whenever an opponent has a building on [hex], else `null`. */
    data class MoveRobber(val hex: Hex, val victim: PlayerColor?) : Action

    /** [Phase.Main]. */
    data class BuildRoad(val edge: Edge) : Action

    /** [Phase.Main]. */
    data class BuildSettlement(val vertex: Vertex) : Action

    /** [Phase.Main]: upgrade one of your settlements. */
    data class BuildCity(val vertex: Vertex) : Action

    /** [Phase.Main]. */
    data object BuyDevCard : Action

    /** [Phase.PreRoll] or [Phase.Main], once per turn, with a card not bought this turn. */
    data object PlayKnight : Action

    /** Same timing as [PlayKnight]. */
    data object PlayRoadBuilding : Action

    /** Same timing as [PlayKnight]; [first] and [second] may be the same resource. */
    data class PlayYearOfPlenty(val first: Resource, val second: Resource) : Action

    /** Same timing as [PlayKnight]. */
    data class PlayMonopoly(val resource: Resource) : Action

    /** [Phase.Main]: give `rate * lots` of [give] to the bank for [lots] of [get]. */
    data class MaritimeTrade(val give: Resource, val get: Resource, val lots: Int = 1) : Action

    /**
     * [Phase.Main]: offer [to] the cards in [give] for the cards in [get]. The current player may offer to anyone;
     * other players may only make (counter)offers to the current player.
     */
    data class ProposeTrade(
        val to: PlayerColor,
        val give: ResourceCounts,
        val get: ResourceCounts
    ) : Action

    /** [Phase.Main]: the recipient of an offer accepts it. */
    data class AcceptTrade(val offerId: Int) : Action

    /** Either side of an offer withdraws or declines it. */
    data class CancelTrade(val offerId: Int) : Action

    /** [Phase.Main]. */
    data object EndTurn : Action
}
