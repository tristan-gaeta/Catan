package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.DevelopmentCardType.VICTORY_POINT
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.PlayerColor.BLUE
import com.tgaetaprojects.biophilia.game.PlayerColor.RED
import com.tgaetaprojects.biophilia.game.PlayerColor.WHITE
import com.tgaetaprojects.biophilia.game.Vertex
import com.tgaetaprojects.biophilia.game.engine.RuleViolation.GAME_OVER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VictoryTest {
    private val game = newGame()

    /** Gives [color] 4 cities (8 points) on legal, spread-out intersections. */
    private fun buildFourCities(color: PlayerColor) {
        repeat(4) {
            game.putCity(
                color,
                Vertex.entries.first { game.board.satisfiesDistanceRule(it) })
        }
        assertEquals(8, game.publicVictoryPoints(color))
    }

    @Test
    fun tenPointsOnlyWinOnYourOwnTurn() {
        buildFourCities(RED)
        game.giveDevCard(RED, VICTORY_POINT)
        game.giveDevCard(RED, VICTORY_POINT)
        assertEquals(10, game.totalVictoryPoints(RED))

        game.startTurnFor(WHITE)
        game.apply(WHITE, Action.EndTurn).accepted()
        assertEquals(Phase.PreRoll, game.phase)

        game.startTurnFor(BLUE)
        val events = game.apply(BLUE, Action.EndTurn).accepted()
        assertEquals(GameEvent.GameWon(RED, 10), events.last())
        assertEquals(Phase.GameOver(RED), game.phase)

        game.apply(RED, Action.RollDice).rejectedWith(GAME_OVER)
    }

    @Test
    fun buyingTheTenthPointWinsImmediately() {
        buildFourCities(RED)
        game.giveDevCard(RED, VICTORY_POINT)
        game.devDeck.remove(VICTORY_POINT)
        game.devDeck.addFirst(VICTORY_POINT)
        game.give(RED, ResourceCounts.DEV_CARD)
        game.startTurnFor(RED)

        val events = game.apply(RED, Action.BuyDevCard).accepted()
        assertEquals(GameEvent.GameWon(RED, 10), events.last())
    }

    @Test
    fun reachingTenMidTurnWinsImmediately() {
        buildFourCities(RED)
        game.giveDevCard(RED, VICTORY_POINT)
        val spot = Vertex.entries.first { game.board.satisfiesDistanceRule(it) }
        game.putRoads(RED, listOf(spot.edges.first()))
        game.give(RED, ResourceCounts.SETTLEMENT)
        game.startTurnFor(RED)

        val events = game.apply(RED, Action.BuildSettlement(spot)).accepted()
        assertEquals(GameEvent.GameWon(RED, 10), events.last())
        assertTrue(game.phase is Phase.GameOver)
    }
}
