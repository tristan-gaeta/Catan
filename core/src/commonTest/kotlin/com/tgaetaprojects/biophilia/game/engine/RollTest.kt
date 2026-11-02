package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.DiceTotal
import com.tgaetaprojects.biophilia.game.Direction.NE
import com.tgaetaprojects.biophilia.game.Direction.SW
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.PlayerColor.*
import com.tgaetaprojects.biophilia.game.Resource.*
import com.tgaetaprojects.biophilia.game.engine.ResourceCounts.Companion.of
import com.tgaetaprojects.biophilia.game.engine.RuleViolation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Rolling through [Game.apply], with the dice forced. */
class RollTest {
    private val game = newGame()

    private fun rollAs(color: PlayerColor, roll: DiceTotal): List<GameEvent> {
        game.startTurnFor(color, Phase.PreRoll)
        game.diceRoller = { roll }
        return game.apply(color, Action.RollDice).accepted()
    }

    @Test
    fun aProductionRollPaysOutAndOpensTheMainPhase() {
        val hex = Hex.entries.first { game.board.numberTiles[it] == DiceTotal.TWO }
        val resource = game.board.biomes.getValue(hex).resource!!
        game.putSettlement(RED, hex.vertices.getValue(NE))
        game.putCity(WHITE, hex.vertices.getValue(SW))

        val events = rollAs(RED, DiceTotal.TWO)
        assertEquals(GameEvent.ResourcesProduced(mapOf(RED to of(resource, 1), WHITE to of(resource, 2))), events.last())
        assertEquals(of(resource, 1), game.player(RED).hand)
        assertEquals(19 - 3, game.bank[resource])
        assertEquals(Phase.Main, game.phase)
        assertEquals(DiceTotal.TWO, game.lastRoll)

        game.apply(RED, Action.RollDice).rejectedWith(WRONG_PHASE)
        game.apply(RED, Action.EndTurn).accepted()
        assertNull(game.lastRoll)
    }

    @Test
    fun aSevenWithNobodyOverSevenGoesStraightToTheRobber() {
        game.give(RED, of(BRICK to 4, ORE to 3))
        rollAs(RED, DiceTotal.SEVEN)
        assertEquals(Phase.MoveRobber(Phase.Main), game.phase)
        assertEquals(7, game.player(RED).hand.total)
    }

    @Test
    fun aSevenMakesEveryoneOverSevenDiscardInAnyOrder() {
        game.give(RED, of(BRICK to 5, ORE to 4))
        game.give(WHITE, of(WOOL to 7))
        game.give(BLUE, of(GRAIN to 8))

        rollAs(RED, DiceTotal.SEVEN)
        assertEquals(Phase.Discard(mapOf(RED to 4, BLUE to 4)), game.phase)
        game.apply(WHITE, Action.Discard(of(WOOL, 3))).rejectedWith(INVALID_DISCARD)
        game.apply(RED, Action.MoveRobber(Hex.entries.first { it != game.board.robber }, null)).rejectedWith(WRONG_PHASE)

        game.apply(BLUE, Action.Discard(of(GRAIN, 4))).accepted()
        assertEquals(Phase.Discard(mapOf(RED to 4)), game.phase)
        game.apply(RED, Action.Discard(of(BRICK to 2, ORE to 2))).accepted()
        assertEquals(Phase.MoveRobber(Phase.Main), game.phase)

        val held = game.players.fold(ResourceCounts.EMPTY) { acc, p -> acc + p.hand }
        assertEquals(ResourceCounts.FULL_BANK, game.bank + held)
    }

    @Test
    fun pendingDiscardsCannotBeChangedFromOutside() {
        game.give(BLUE, of(GRAIN to 8))
        rollAs(RED, DiceTotal.SEVEN)
        val pending = assertIs<Phase.Discard>(game.phase).pending

        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> { (pending as MutableMap<PlayerColor, Int>)[BLUE] = 0 }
    }
}
