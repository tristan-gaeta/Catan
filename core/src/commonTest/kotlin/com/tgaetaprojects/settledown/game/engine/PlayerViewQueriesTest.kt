package com.tgaetaprojects.settledown.game.engine

import com.tgaetaprojects.settledown.game.DevelopmentCardType
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor.BLUE
import com.tgaetaprojects.settledown.game.PlayerColor.ORANGE
import com.tgaetaprojects.settledown.game.PlayerColor.RED
import com.tgaetaprojects.settledown.game.PlayerColor.WHITE
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.codec.SnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerViewQueriesTest {

    /** A client only has the view, so everything it can ask must match what the server's game would answer. */
    @Test
    fun viewQueries_everyPositionOfRandomGames_matchGameQueries() {
        for (seed in 1L..4L) {
            val game = RandomBots.game(seed)
            RandomBots.play(game, seed) { _, _, _ -> checkViewsMatch(game) }
        }
    }

    private fun checkViewsMatch(game: Game) {
        for (seat in game.seatOrder) {
            val view = SnapshotCodec.decodeView(SnapshotCodec.encodeView(game.viewFor(seat)))
            for (color in game.seatOrder) {
                val player = game.player(color)
                assertEquals(game.legalSettlementSpots(color), view.legalSettlementSpots(color))
                assertEquals(game.legalRoadSpots(color), view.legalRoadSpots(color))
                assertEquals(game.legalCitySpots(color), view.legalCitySpots(color))
                assertEquals(player.settlementsLeft, view.settlementsLeft(color))
                assertEquals(player.citiesLeft, view.citiesLeft(color))
                assertEquals(player.roadsLeft, view.roadsLeft(color))
                for (resource in Resource.entries) {
                    assertEquals(game.maritimeRate(color, resource), view.maritimeRate(resource, color))
                }
            }
            for (hex in Hex.entries) assertEquals(game.robberVictims(hex), view.robberVictims(hex))
            for (type in DevelopmentCardType.entries) {
                assertEquals(game.playableCount(seat, type), view.playableCount(type))
            }
            assertEquals(game.totalVictoryPoints(seat), view.ownVictoryPoints)
        }
    }

    @Test
    fun mustAct_setupTurn_isOnlyTheCurrentPlayer() {
        val game = newGame()
        assertTrue(game.viewFor(RED).mustAct)
        assertFalse(game.viewFor(WHITE).mustAct)
    }

    @Test
    fun roadsLeft_colorNotPlaying_throwsLikeGame() {
        val game = newGame()
        assertFailsWith<IllegalArgumentException> { game.player(ORANGE) }
        assertFailsWith<IllegalArgumentException> { game.viewFor(RED).roadsLeft(ORANGE) }
        assertFailsWith<IllegalArgumentException> { game.viewFor(RED).legalSettlementSpots(ORANGE) }
    }

    @Test
    fun mustAct_discard_isEveryoneOwing() {
        val game = newGame()
        game.startTurnFor(RED, Phase.Discard(mapOf(WHITE to 4, BLUE to 5)))
        assertFalse(game.viewFor(RED).mustAct)
        assertTrue(game.viewFor(WHITE).mustAct)
        assertTrue(game.viewFor(BLUE).mustAct)
    }

    @Test
    fun mustAct_gameOver_isNobody() {
        val game = newGame()
        game.startTurnFor(RED, Phase.GameOver(RED))
        assertFalse(game.viewFor(RED).mustAct)
    }

    // ---- ResourceCounts.firstCards ----

    @Test
    fun firstCards_takesInResourceOrder() {
        val hand = ResourceCounts.of(Resource.ORE to 3, Resource.BRICK to 2)
        assertEquals(ResourceCounts.of(Resource.BRICK to 2, Resource.ORE to 1), hand.firstCards(3))
    }

    @Test
    fun firstCards_zero_isEmpty() {
        assertEquals(ResourceCounts.EMPTY, ResourceCounts.of(Resource.ORE, 3).firstCards(0))
    }

    @Test
    fun firstCards_moreThanHeld_throws() {
        assertFailsWith<IllegalArgumentException> { ResourceCounts.of(Resource.ORE, 1).firstCards(2) }
    }
}
