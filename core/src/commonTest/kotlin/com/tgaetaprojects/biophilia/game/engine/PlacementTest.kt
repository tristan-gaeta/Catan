package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.Building
import com.tgaetaprojects.biophilia.game.Direction.NE
import com.tgaetaprojects.biophilia.game.Edge
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor.*
import com.tgaetaprojects.biophilia.game.Vertex
import com.tgaetaprojects.biophilia.game.engine.RuleViolation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlacementTest {
    private val game = newGame().apply { startTurnFor(RED) }
    private val board = game.board
    private val home = Hex.J.vertices.getValue(NE)

    @Test
    fun settlementsNeedARoadAndRespectTheDistanceRule() {
        val (edges, stops) = pathFrom(home, 2)
        game.putSettlement(RED, home)
        game.putRoads(RED, edges)
        game.give(RED, ResourceCounts.SETTLEMENT * 2)

        game.apply(RED, Action.BuildSettlement(stops[1])).rejectedWith(ILLEGAL_LOCATION)
        val unconnected = Vertex.entries.first { v ->
            board.satisfiesDistanceRule(v) && v.edges.none { board.roads[it] == RED }
        }
        game.apply(RED, Action.BuildSettlement(unconnected)).rejectedWith(ILLEGAL_LOCATION)

        game.apply(RED, Action.BuildSettlement(stops[2])).accepted()
        assertEquals(Building.settlement(RED), board.buildings[stops[2]])
        assertEquals(PlayerState.SETTLEMENTS - 2, game.player(RED).settlementsLeft)
        assertEquals(ResourceCounts.SETTLEMENT, game.player(RED).hand)
    }

    @Test
    fun roadsMustConnectAndCannotRunThroughAnOpponentsBuilding() {
        val (edges, stops) = pathFrom(home, 2)
        game.putSettlement(RED, home)
        game.putRoads(RED, edges)
        game.putSettlement(WHITE, stops[2])
        game.give(RED, ResourceCounts.ROAD * 3)

        val beyondWhite = stops[2].edges.first { it !in edges }
        game.apply(RED, Action.BuildRoad(beyondWhite)).rejectedWith(ILLEGAL_LOCATION)

        val disconnected = Edge.entries.first { e ->
            e !in board.roads && e.vertices.all { v -> board.buildings[v] == null && v.edges.none { it in board.roads } }
        }
        game.apply(RED, Action.BuildRoad(disconnected)).rejectedWith(ILLEGAL_LOCATION)
        game.apply(RED, Action.BuildRoad(edges[0])).rejectedWith(ILLEGAL_LOCATION)

        game.apply(RED, Action.BuildRoad(stops[1].edges.first { it !in edges })).accepted()
        assertEquals(PlayerState.ROADS - 3, game.player(RED).roadsLeft)
    }

    @Test
    fun citiesReplaceYourOwnSettlementsAndReturnThePiece() {
        val whiteHome = Hex.A.vertices.getValue(NE)
        game.putSettlement(RED, home)
        game.putSettlement(WHITE, whiteHome)
        game.give(RED, ResourceCounts.CITY)

        game.apply(RED, Action.BuildCity(whiteHome)).rejectedWith(ILLEGAL_LOCATION)
        game.apply(RED, Action.BuildCity(home.neighbors.keys.first())).rejectedWith(ILLEGAL_LOCATION)

        game.apply(RED, Action.BuildCity(home)).accepted()
        assertEquals(Building.city(RED), board.buildings[home])
        assertEquals(PlayerState.SETTLEMENTS, game.player(RED).settlementsLeft)
        assertEquals(PlayerState.CITIES - 1, game.player(RED).citiesLeft)
        assertEquals(2, game.publicVictoryPoints(RED))
    }

    @Test
    fun buildingNeedsResourcesAndPiecesAndLeavesNoTraceWhenRefused() {
        val (edges, _) = pathFrom(home, 1)
        game.putSettlement(RED, home)

        game.apply(RED, Action.BuildRoad(edges[0])).rejectedWith(INSUFFICIENT_RESOURCES)
        game.give(RED, ResourceCounts.ROAD)
        game.player(RED).roadsLeft = 0
        game.apply(RED, Action.BuildRoad(edges[0])).rejectedWith(NO_PIECES_LEFT)

        assertFalse(edges[0] in board.roads)
        assertEquals(ResourceCounts.ROAD, game.player(RED).hand)
    }

    @Test
    fun buildingOnlyHappensOnYourTurnAfterTheRoll() {
        game.putSettlement(RED, home)
        game.give(RED, ResourceCounts.ROAD)
        game.apply(WHITE, Action.BuildRoad(home.edges.first())).rejectedWith(NOT_YOUR_TURN)
        game.startTurnFor(RED, Phase.PreRoll)
        game.apply(RED, Action.BuildRoad(home.edges.first())).rejectedWith(WRONG_PHASE)
    }
}
