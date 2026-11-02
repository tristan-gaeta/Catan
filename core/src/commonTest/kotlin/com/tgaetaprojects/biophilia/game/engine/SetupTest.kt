package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.DiceTotal
import com.tgaetaprojects.biophilia.game.Direction.NE
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.PlayerColor.*
import com.tgaetaprojects.biophilia.game.Vertex
import com.tgaetaprojects.biophilia.game.engine.RuleViolation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupTest {

    // ---- Choosing the first player ----

    @Test
    fun theHighestRollGoesFirstAndPlayGoesClockwiseFromThem() {
        val game = Game(listOf(RED, WHITE, BLUE, ORANGE), boardSeed = 1, gameSeed = 2)
        game.forceRolls(DiceTotal.SIX, DiceTotal.TEN, DiceTotal.THREE, DiceTotal.EIGHT)
        assertEquals(Phase.RollForFirstPlayer(listOf(RED, WHITE, BLUE, ORANGE), emptyMap()), game.phase)
        assertNull(game.firstPlayer)

        game.apply(WHITE, Action.RollDice).rejectedWith(NOT_YOUR_TURN)
        game.apply(RED, Action.PlaceSettlement(Hex.J.vertices.getValue(NE))).rejectedWith(WRONG_PHASE)
        for (color in listOf(RED, WHITE, BLUE)) game.apply(color, Action.RollDice).accepted()
        val events = game.apply(ORANGE, Action.RollDice).accepted()

        assertEquals(listOf(WHITE, BLUE, ORANGE, RED), game.turnOrder)
        assertEquals(listOf(RED, WHITE, BLUE, ORANGE), game.seatOrder)
        assertEquals(GameEvent.TurnOrderDecided(listOf(WHITE, BLUE, ORANGE, RED)), events.last())
        assertEquals(WHITE, game.firstPlayer)
        assertEquals(WHITE, game.currentPlayer)
        assertEquals(Phase.SetupSettlement(1), game.phase)
        assertEquals(0, game.turn)
    }

    @Test
    fun onlyPlayersTiedForTheHighestRollRollAgain() {
        val game = Game(listOf(RED, WHITE, BLUE), boardSeed = 1, gameSeed = 2)
        game.forceRolls(DiceTotal.EIGHT, DiceTotal.ELEVEN, DiceTotal.ELEVEN, DiceTotal.FOUR, DiceTotal.FOUR, DiceTotal.NINE, DiceTotal.TWO)
        for (color in listOf(RED, WHITE, BLUE)) game.apply(color, Action.RollDice).accepted()
        assertEquals(Phase.RollForFirstPlayer(listOf(WHITE, BLUE), emptyMap()), game.phase)
        assertEquals(WHITE, game.currentPlayer)
        game.apply(RED, Action.RollDice).rejectedWith(NOT_YOUR_TURN)

        // tied again at 4, then WHITE wins the third roll-off 9 to 2
        game.apply(WHITE, Action.RollDice).accepted()
        game.apply(BLUE, Action.RollDice).accepted()
        assertEquals(Phase.RollForFirstPlayer(listOf(WHITE, BLUE), emptyMap()), game.phase)
        game.apply(WHITE, Action.RollDice).accepted()
        game.apply(BLUE, Action.RollDice).accepted()
        assertEquals(listOf(WHITE, BLUE, RED), game.turnOrder)
        assertEquals(WHITE, game.currentPlayer)
    }

    // ---- Placing starting pieces ----

    @Test
    fun setupRunsAsASnakeAndTheFirstPlayerStarts() {
        val game = newGame(seats = listOf(RED, WHITE, BLUE, ORANGE)).apply { skipFirstPlayerRoll(BLUE) }
        val placers = mutableListOf<PlayerColor>()
        while (game.phase != Phase.PreRoll) {
            if (game.phase is Phase.SetupSettlement) placers += game.currentPlayer
            val action = when (game.phase) {
                is Phase.SetupSettlement -> Action.PlaceSettlement(game.legalSettlementSpots(game.currentPlayer).first())
                else -> Action.PlaceRoad(game.legalRoadSpots(game.currentPlayer).first())
            }
            game.apply(game.currentPlayer, action).accepted()
        }
        assertEquals(listOf(BLUE, ORANGE, RED, WHITE, WHITE, RED, ORANGE, BLUE), placers)
        assertEquals(BLUE, game.currentPlayer)
        assertEquals(1, game.turn)
    }

    @Test
    fun onlyTheSecondSettlementPaysStartingResources() {
        val game = newGame()
        val secondSettlements = mutableMapOf<PlayerColor, Vertex>()
        while (game.phase != Phase.PreRoll) {
            val phase = game.phase
            if (phase is Phase.SetupSettlement) {
                val spot = game.legalSettlementSpots(game.currentPlayer).first()
                if (phase.round == 2) secondSettlements[game.currentPlayer] = spot
                game.apply(game.currentPlayer, Action.PlaceSettlement(spot)).accepted()
                if (phase.round == 1) assertTrue(game.player(game.currentPlayer).hand.isEmpty)
            } else {
                game.apply(game.currentPlayer, Action.PlaceRoad(game.legalRoadSpots(game.currentPlayer).first())).accepted()
            }
        }
        for ((color, vertex) in secondSettlements) {
            val expected = vertex.hexes.mapNotNull { game.board.biomes.getValue(it).resource }
                .fold(ResourceCounts.EMPTY) { acc, r -> acc + r }
            assertEquals(expected, game.player(color).hand, "$color's starting hand")
        }
        val handTotal = game.players.fold(ResourceCounts.EMPTY) { acc, p -> acc + p.hand }
        assertEquals(ResourceCounts.FULL_BANK, game.bank + handTotal)
    }

    @Test
    fun setupSettlementsFollowTheDistanceRuleButNeedNoRoad() {
        val game = newGame()
        val north = Hex.J.vertices.getValue(NE)
        game.apply(RED, Action.PlaceSettlement(north)).accepted()
        game.apply(RED, Action.PlaceRoad(north.edges.first())).accepted()

        game.apply(WHITE, Action.PlaceSettlement(north.neighbors.keys.first())).rejectedWith(ILLEGAL_LOCATION)
        game.apply(WHITE, Action.PlaceSettlement(Hex.A.vertices.getValue(NE))).accepted()
    }

    @Test
    fun setupRoadMustTouchTheNewSettlement() {
        val game = newGame()
        val north = Hex.J.vertices.getValue(NE)
        game.apply(RED, Action.PlaceSettlement(north)).accepted()

        val elsewhere = Hex.A.edges.values.first()
        game.apply(RED, Action.PlaceRoad(elsewhere)).rejectedWith(ILLEGAL_LOCATION)
        game.apply(RED, Action.RollDice).rejectedWith(WRONG_PHASE)
        game.apply(RED, Action.PlaceRoad(north.edges.first())).accepted()
        assertEquals(WHITE, game.currentPlayer)
    }

    @Test
    fun otherPlayersCannotActDuringSomeoneElsesSetup() {
        val game = newGame()
        game.apply(WHITE, Action.PlaceSettlement(Hex.J.vertices.getValue(NE))).rejectedWith(NOT_YOUR_TURN)
        game.apply(ORANGE, Action.RollDice).rejectedWith(UNKNOWN_PLAYER)
    }
}
