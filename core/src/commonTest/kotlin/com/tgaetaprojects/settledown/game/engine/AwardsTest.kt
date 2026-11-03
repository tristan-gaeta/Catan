package com.tgaetaprojects.settledown.game.engine

import com.tgaetaprojects.settledown.game.DevelopmentCardType.KNIGHT
import com.tgaetaprojects.settledown.game.Direction.NE
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor.BLUE
import com.tgaetaprojects.settledown.game.PlayerColor.RED
import com.tgaetaprojects.settledown.game.PlayerColor.WHITE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AwardsTest {
    private val game = newGame()
    private val board = game.board
    private val start = Hex.J.vertices.getValue(NE)

    // ---- Measuring roads ----

    @Test
    fun branchesDoNotAddUp() {
        val (path, stops) = pathFrom(start, 5)
        game.putRoads(RED, path + stops[2].edges.first { it !in path })
        assertEquals(5, board.longestRoad(RED))
    }

    @Test
    fun aLoopCountsInFull() {
        game.putRoads(RED, Hex.J.edges.values.toList())
        assertEquals(6, board.longestRoad(RED))
    }

    @Test
    fun anOpponentsSettlementSplitsTheRoadButYourOwnDoesNot() {
        val (path, stops) = pathFrom(start, 7)
        game.putRoads(RED, path)
        assertEquals(7, board.longestRoad(RED))

        game.putSettlement(RED, stops[3])
        assertEquals(7, board.longestRoad(RED))

        board.placeBuilding(stops[3], com.tgaetaprojects.settledown.game.Building.settlement(WHITE))
        assertEquals(4, board.longestRoad(RED))
    }

    // ---- Who holds the cards ----

    @Test
    fun longestRoadNeedsFiveAndAStrictlyLongerChallenger() {
        assertNull(longestRoadHolder(null, mapOf(RED to 4, WHITE to 2)))
        assertEquals(RED, longestRoadHolder(null, mapOf(RED to 5, WHITE to 2)))
        assertNull(longestRoadHolder(null, mapOf(RED to 5, WHITE to 5)))
        assertEquals(RED, longestRoadHolder(RED, mapOf(RED to 6, WHITE to 6)))
        assertEquals(WHITE, longestRoadHolder(RED, mapOf(RED to 6, WHITE to 7)))
    }

    @Test
    fun aBrokenHolderKeepsATieButLosesToATieBetweenOthers() {
        assertEquals(RED, longestRoadHolder(RED, mapOf(RED to 5, WHITE to 5, BLUE to 3)))
        assertNull(longestRoadHolder(RED, mapOf(RED to 4, WHITE to 6, BLUE to 6)))
        assertEquals(WHITE, longestRoadHolder(RED, mapOf(RED to 4, WHITE to 6, BLUE to 5)))
        assertNull(longestRoadHolder(RED, mapOf(RED to 4, WHITE to 4)))
    }

    @Test
    fun largestArmyNeedsThreeAndAStrictlyLargerChallenger() {
        assertNull(largestArmyHolder(null, mapOf(RED to 2, WHITE to 1)))
        assertEquals(RED, largestArmyHolder(null, mapOf(RED to 3, WHITE to 1)))
        assertEquals(RED, largestArmyHolder(RED, mapOf(RED to 3, WHITE to 3)))
        assertEquals(WHITE, largestArmyHolder(RED, mapOf(RED to 3, WHITE to 4)))
    }

    // ---- Through the engine ----

    @Test
    fun theFifthRoadTakesLongestRoad() {
        val (path, stops) = pathFrom(start, 5)
        game.putSettlement(RED, stops[0])
        game.putRoads(RED, path.take(4))
        game.give(RED, ResourceCounts.ROAD)
        game.startTurnFor(RED)

        val events = game.apply(RED, Action.BuildRoad(path[4])).accepted()
        assertTrue(GameEvent.LongestRoadChanged(RED, 5) in events)
        assertEquals(RED, game.longestRoadHolder)
        assertEquals(3, game.publicVictoryPoints(RED))
    }

    @Test
    fun aSettlementThatBreaksTheHolderCanSetTheCardAside() {
        val (path, stops) = pathFrom(start, 6)
        game.putRoads(RED, path)
        game.longestRoadHolder = RED

        // WHITE reaches the middle of RED's road and settles there, splitting it into 3 + 3
        val whiteRoad = stops[3].edges.first { it !in path }
        game.putRoads(WHITE, listOf(whiteRoad))
        game.give(WHITE, ResourceCounts.SETTLEMENT)
        game.startTurnFor(WHITE)

        val events = game.apply(WHITE, Action.BuildSettlement(stops[3])).accepted()
        assertTrue(GameEvent.LongestRoadChanged(null, 0) in events)
        assertNull(game.longestRoadHolder)
    }

    @Test
    fun aBrokenHolderStillTiedForLongestKeepsTheCard() {
        val (redPath, redStops) = pathFrom(start, 7)
        game.putRoads(RED, redPath)
        game.longestRoadHolder = RED

        // WHITE's own 5-road reaches RED's road two intersections in; settling there splits RED into 2 + 5
        val (whitePath, _) = pathFrom(redStops[2], 5, avoid = redStops)
        game.putRoads(WHITE, whitePath)
        game.give(WHITE, ResourceCounts.SETTLEMENT)
        game.startTurnFor(WHITE)

        val events = game.apply(WHITE, Action.BuildSettlement(redStops[2])).accepted()
        assertEquals(5, game.player(RED).longestRoad)
        assertEquals(5, game.player(WHITE).longestRoad)
        assertEquals(RED, game.longestRoadHolder)
        assertTrue(events.none { it is GameEvent.LongestRoadChanged })
    }

    @Test
    fun theThirdKnightTakesLargestArmy() {
        game.player(RED).knightsPlayed = 2
        game.giveDevCard(RED, KNIGHT)
        game.startTurnFor(RED)

        val events = game.apply(RED, Action.PlayKnight).accepted()
        assertTrue(GameEvent.LargestArmyChanged(RED, 3) in events)
        assertEquals(2, game.publicVictoryPoints(RED))
    }
}
