package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.Direction.NE
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.PlayerColor.*
import com.tgaetaprojects.biophilia.game.Vertex
import com.tgaetaprojects.biophilia.game.engine.RuleViolation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetupTest {

    @Test
    fun setupRunsAsASnakeAndTheFirstPlayerStarts() {
        val game = newGame(players = listOf(RED, WHITE, BLUE, ORANGE))
        val placers = mutableListOf<PlayerColor>()
        while (game.phase != Phase.PreRoll) {
            if (game.phase is Phase.SetupSettlement) placers += game.currentPlayer
            val action = when (game.phase) {
                is Phase.SetupSettlement -> Action.PlaceSettlement(game.legalSettlementSpots(game.currentPlayer).first())
                else -> Action.PlaceRoad(game.legalRoadSpots(game.currentPlayer).first())
            }
            game.apply(game.currentPlayer, action).accepted()
        }
        assertEquals(listOf(RED, WHITE, BLUE, ORANGE, ORANGE, BLUE, WHITE, RED), placers)
        assertEquals(RED, game.currentPlayer)
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
