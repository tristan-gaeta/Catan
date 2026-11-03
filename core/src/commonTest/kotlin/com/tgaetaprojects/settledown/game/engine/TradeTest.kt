package com.tgaetaprojects.settledown.game.engine

import com.tgaetaprojects.settledown.game.PlayerColor.BLUE
import com.tgaetaprojects.settledown.game.PlayerColor.RED
import com.tgaetaprojects.settledown.game.PlayerColor.WHITE
import com.tgaetaprojects.settledown.game.PortType
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.Resource.BRICK
import com.tgaetaprojects.settledown.game.Resource.GRAIN
import com.tgaetaprojects.settledown.game.Resource.ORE
import com.tgaetaprojects.settledown.game.Resource.WOOL
import com.tgaetaprojects.settledown.game.engine.ResourceCounts.Companion.of
import com.tgaetaprojects.settledown.game.engine.RuleViolation.INSUFFICIENT_RESOURCES
import com.tgaetaprojects.settledown.game.engine.RuleViolation.INVALID_TRADE
import com.tgaetaprojects.settledown.game.engine.RuleViolation.NOT_YOUR_TURN
import com.tgaetaprojects.settledown.game.engine.RuleViolation.NO_SUCH_OFFER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TradeTest {
    private val game = newGame().apply { startTurnFor(RED) }

    /** One end of a [type] harbor's edge; either end gives access. */
    private fun harborVertex(type: PortType, end: Int = 0) =
        game.board.ports.entries.first { it.value == type }.key.vertices.elementAt(end)

    @Test
    fun eitherEndOfAHarborGivesAccess() {
        game.putSettlement(RED, harborVertex(PortType.GENERIC, end = 1))
        assertEquals(3, game.maritimeRate(RED, ORE))
    }

    @Test
    fun maritimeRatesDependOnHarbors() {
        assertTrue(Resource.entries.all { game.maritimeRate(RED, it) == 4 })

        game.putSettlement(RED, harborVertex(PortType.GENERIC))
        assertTrue(Resource.entries.all { game.maritimeRate(RED, it) == 3 })

        game.putSettlement(RED, harborVertex(PortType.BRICK))
        assertEquals(2, game.maritimeRate(RED, BRICK))
        assertEquals(3, game.maritimeRate(RED, ORE))
        assertEquals(4, game.maritimeRate(WHITE, BRICK))
    }

    @Test
    fun maritimeTradeExchangesWithTheBank() {
        game.give(RED, of(ORE, 9))
        game.apply(RED, Action.MaritimeTrade(ORE, ORE)).rejectedWith(INVALID_TRADE)
        game.apply(RED, Action.MaritimeTrade(ORE, WOOL, lots = 3))
            .rejectedWith(INSUFFICIENT_RESOURCES)

        game.apply(RED, Action.MaritimeTrade(ORE, WOOL, lots = 2)).accepted()
        assertEquals(of(ORE to 1, WOOL to 2), game.player(RED).hand)
        assertEquals(19 - 1, game.bank[ORE])
        assertEquals(19 - 2, game.bank[WOOL])
    }

    @Test
    fun domesticTradesAlwaysInvolveTheCurrentPlayer() {
        game.give(RED, of(ORE, 2))
        game.give(WHITE, of(BRICK, 1))
        game.give(BLUE, of(WOOL, 1))

        game.apply(RED, Action.ProposeTrade(WHITE, of(ORE, 1), ResourceCounts.EMPTY))
            .rejectedWith(INVALID_TRADE)
        game.apply(RED, Action.ProposeTrade(WHITE, of(ORE, 1), of(ORE, 1)))
            .rejectedWith(INVALID_TRADE)
        game.apply(RED, Action.ProposeTrade(RED, of(ORE, 1), of(BRICK, 1)))
            .rejectedWith(INVALID_TRADE)
        game.apply(RED, Action.ProposeTrade(WHITE, of(GRAIN, 1), of(BRICK, 1)))
            .rejectedWith(INSUFFICIENT_RESOURCES)
        game.apply(BLUE, Action.ProposeTrade(WHITE, of(WOOL, 1), of(BRICK, 1)))
            .rejectedWith(NOT_YOUR_TURN)

        val proposed =
            game.apply(RED, Action.ProposeTrade(WHITE, of(ORE, 1), of(BRICK, 1))).accepted()
        val offer = assertIs<GameEvent.TradeProposed>(proposed.single()).offer
        game.apply(BLUE, Action.AcceptTrade(offer.id)).rejectedWith(NOT_YOUR_TURN)
        game.apply(WHITE, Action.AcceptTrade(offer.id)).accepted()

        assertEquals(of(ORE to 1, BRICK to 1), game.player(RED).hand)
        assertEquals(of(ORE, 1), game.player(WHITE).hand)
        assertTrue(game.tradeOffers.isEmpty())
        game.apply(WHITE, Action.AcceptTrade(offer.id)).rejectedWith(NO_SUCH_OFFER)
    }

    @Test
    fun counterOffersDeclinesAndStaleOffers() {
        game.give(RED, of(ORE, 1))
        game.give(WHITE, of(BRICK, 2))

        // a counteroffer from another player to the current player
        val counter =
            game.apply(WHITE, Action.ProposeTrade(RED, of(BRICK, 2), of(ORE, 1))).accepted()
        val counterId = assertIs<GameEvent.TradeProposed>(counter.single()).offer.id

        // a second offer the recipient declines
        val declined =
            game.apply(RED, Action.ProposeTrade(BLUE, of(ORE, 1), of(WOOL, 1))).accepted()
        val declinedId = assertIs<GameEvent.TradeProposed>(declined.single()).offer.id
        game.apply(BLUE, Action.CancelTrade(declinedId)).accepted()

        game.apply(RED, Action.AcceptTrade(counterId)).accepted()
        assertEquals(of(BRICK, 2), game.player(RED).hand)

        // an offer that can no longer be paid for is refused, and ending the turn clears offers
        val stale = game.apply(RED, Action.ProposeTrade(WHITE, of(BRICK, 1), of(ORE, 1))).accepted()
        val staleId = assertIs<GameEvent.TradeProposed>(stale.single()).offer.id
        game.apply(WHITE, Action.AcceptTrade(staleId)).accepted()
        game.apply(RED, Action.ProposeTrade(WHITE, of(BRICK, 1), of(ORE, 1))).accepted()
        game.player(WHITE).hand = ResourceCounts.EMPTY
        game.apply(WHITE, Action.AcceptTrade(game.tradeOffers.single().id))
            .rejectedWith(INSUFFICIENT_RESOURCES)
        game.apply(RED, Action.EndTurn).accepted()
        assertTrue(game.tradeOffers.isEmpty())
    }
}
