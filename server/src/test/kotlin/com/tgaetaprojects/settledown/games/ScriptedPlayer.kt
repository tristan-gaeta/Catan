package com.tgaetaprojects.settledown.games

import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.PlayerView
import com.tgaetaprojects.settledown.game.engine.ResourceCounts

/**
 * A plain player that works only from what the server sends it, as a real client must. Its legal-move queries come
 * from the view, so a full game with it also shows they agree with the server's rules: any refusal fails the test.
 */
internal fun scriptedAction(view: PlayerView): Action {
    val me = view.viewer
    return when (val phase = view.phase) {
        is Phase.RollForFirstPlayer, Phase.PreRoll -> Action.RollDice
        is Phase.SetupSettlement -> Action.PlaceSettlement(
            view.legalSettlementSpots().maxBy { vertex -> vertex.hexes.sumOf { view.layout.numbers.getValue(it).pips } }
        )

        is Phase.SetupRoad, is Phase.RoadBuilding -> Action.PlaceRoad(view.legalRoadSpots().first())
        is Phase.Discard -> Action.Discard(view.hand.firstCards(phase.pending.getValue(me)))
        is Phase.MoveRobber -> {
            val hex = Hex.entries.filter { it != view.robber }.maxBy { view.robberVictims(it).size }
            Action.MoveRobber(hex, view.robberVictims(hex).firstOrNull())
        }

        Phase.Main -> mainAction(view)
        is Phase.GameOver -> error("The game is over")
    }
}

private fun mainAction(view: PlayerView): Action {
    val me = view.viewer
    val hand = view.hand
    if (hand.covers(ResourceCounts.CITY)) view.legalCitySpots().firstOrNull()?.let { return Action.BuildCity(it) }
    if (hand.covers(ResourceCounts.SETTLEMENT)) {
        view.legalSettlementSpots().firstOrNull()?.let { return Action.BuildSettlement(it) }
    }
    if (hand.covers(ResourceCounts.ROAD) && view.roadsLeft(me) > 0 && view.settlementsLeft(me) > 0 &&
        view.legalSettlementSpots().isEmpty()
    ) {
        view.legalRoadSpots().firstOrNull()?.let { return Action.BuildRoad(it) }
    }
    if (hand.covers(ResourceCounts.DEV_CARD) && view.devCardsRemaining > 0) return Action.BuyDevCard
    // every trade shrinks the hand, so this can't go on forever
    val give = Resource.entries.maxBy { hand[it] }
    val get = Resource.entries.filter { it != give && view.bank[it] > 0 }.minByOrNull { hand[it] }
    if (get != null && hand[give] >= view.maritimeRate(give)) return Action.MaritimeTrade(give, get)
    return Action.EndTurn
}
