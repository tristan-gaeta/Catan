package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.DiceTotal
import com.tgaetaprojects.biophilia.game.Direction.NE
import com.tgaetaprojects.biophilia.game.Direction.SW
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor.BLUE
import com.tgaetaprojects.biophilia.game.PlayerColor.RED
import com.tgaetaprojects.biophilia.game.PlayerColor.WHITE
import com.tgaetaprojects.biophilia.game.Resource.BRICK
import com.tgaetaprojects.biophilia.game.Resource.GRAIN
import com.tgaetaprojects.biophilia.game.Resource.ORE
import com.tgaetaprojects.biophilia.game.engine.ResourceCounts.Companion.of
import com.tgaetaprojects.biophilia.game.engine.RuleViolation.INVALID_DISCARD
import com.tgaetaprojects.biophilia.game.engine.RuleViolation.INVALID_ROBBER_MOVE
import com.tgaetaprojects.biophilia.game.engine.RuleViolation.INVALID_VICTIM
import com.tgaetaprojects.biophilia.game.engine.RuleViolation.WRONG_PHASE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductionAndRobberTest {
    private val game = newGame()

    // "2" appears on exactly one hex, so no other hex can produce alongside it
    private val hex = Hex.entries.first { game.board.numberTiles[it] == DiceTotal.TWO }
    private val resource = game.board.biomes.getValue(hex).resource!!

    // ---- Production ----

    @Test
    fun settlementsCollectOneAndCitiesTwo() {
        game.putSettlement(RED, hex.vertices.getValue(NE))
        game.putCity(WHITE, hex.vertices.getValue(SW))
        assertEquals(
            mapOf(RED to of(resource, 1), WHITE to of(resource, 2)),
            game.board.productionClaims(DiceTotal.TWO)
        )
    }

    @Test
    fun theRobbersHexProducesNothing() {
        game.putSettlement(RED, hex.vertices.getValue(NE))
        game.board.robber = hex
        assertTrue(game.board.productionClaims(DiceTotal.TWO).isEmpty())
    }

    @Test
    fun aShortageSharedByPlayersPaysNobodyThatResource() {
        val claims = mapOf(RED to of(ORE, 1), WHITE to of(ORE to 2, BRICK to 1))
        val bank = ResourceCounts.FULL_BANK.with(ORE, 2)
        assertEquals(mapOf(WHITE to of(BRICK, 1)), settleProduction(claims, bank))
    }

    @Test
    fun aShortageAffectingOnePlayerPaysWhatIsLeft() {
        val claims = mapOf(RED to of(ORE, 3))
        assertEquals(
            mapOf(RED to of(ORE, 2)),
            settleProduction(claims, ResourceCounts.FULL_BANK.with(ORE, 2))
        )
        assertEquals(emptyMap(), settleProduction(claims, ResourceCounts.FULL_BANK.with(ORE, 0)))
    }

    // ---- Rolling a 7 ----

    @Test
    fun discardIsHalfRoundedDownAboveSeven() {
        assertEquals(listOf(0, 0, 4, 4, 5, 9), listOf(3, 7, 8, 9, 11, 19).map(::discardAmount))
    }

    @Test
    fun discardsMustMatchWhatIsOwed() {
        game.startTurnFor(RED, Phase.Discard(mapOf(WHITE to 4)))
        game.give(WHITE, of(BRICK to 5, ORE to 4))

        game.apply(WHITE, Action.Discard(of(BRICK, 3))).rejectedWith(INVALID_DISCARD)
        game.apply(WHITE, Action.Discard(of(GRAIN, 4))).rejectedWith(INVALID_DISCARD)
        game.apply(BLUE, Action.Discard(of(BRICK, 1))).rejectedWith(INVALID_DISCARD)
        game.apply(RED, Action.MoveRobber(hex, null)).rejectedWith(WRONG_PHASE)

        game.apply(WHITE, Action.Discard(of(BRICK to 2, ORE to 2))).accepted()
        assertEquals(of(BRICK to 3, ORE to 2), game.player(WHITE).hand)
        assertEquals(Phase.MoveRobber(Phase.Main), game.phase)
    }

    @Test
    fun theRobberMustMoveAndRobAnAdjacentOpponent() {
        val target = Hex.entries.first { it != game.board.robber }
        game.putSettlement(WHITE, target.vertices.getValue(NE))
        game.putSettlement(RED, target.vertices.getValue(SW))
        game.give(WHITE, of(ORE, 1))
        game.startTurnFor(RED, Phase.MoveRobber(Phase.Main))

        game.apply(RED, Action.MoveRobber(game.board.robber, null))
            .rejectedWith(INVALID_ROBBER_MOVE)
        game.apply(RED, Action.MoveRobber(target, null)).rejectedWith(INVALID_VICTIM)
        game.apply(RED, Action.MoveRobber(target, RED)).rejectedWith(INVALID_VICTIM)
        game.apply(RED, Action.MoveRobber(target, BLUE)).rejectedWith(INVALID_VICTIM)

        val events = game.apply(RED, Action.MoveRobber(target, WHITE)).accepted()
        assertTrue(GameEvent.Stole(RED, WHITE, ORE) in events)
        assertEquals(target, game.board.robber)
        assertEquals(of(ORE, 1), game.player(RED).hand)
        assertTrue(game.player(WHITE).hand.isEmpty)
        assertEquals(Phase.Main, game.phase)
    }

    @Test
    fun robbingAnEmptyHandOrAnEmptyHexStealsNothing() {
        val occupied = Hex.entries.first { it != game.board.robber }
        game.putSettlement(WHITE, occupied.vertices.getValue(NE))
        game.startTurnFor(RED, Phase.MoveRobber(Phase.Main))
        assertTrue(
            GameEvent.Stole(RED, WHITE, null) in game.apply(
                RED,
                Action.MoveRobber(occupied, WHITE)
            ).accepted()
        )

        val empty =
            Hex.entries.first { it != game.board.robber && game.robberVictims(it).isEmpty() }
        game.startTurnFor(RED, Phase.MoveRobber(Phase.Main))
        game.apply(RED, Action.MoveRobber(empty, null)).accepted()
        assertEquals(empty, game.board.robber)
    }
}
