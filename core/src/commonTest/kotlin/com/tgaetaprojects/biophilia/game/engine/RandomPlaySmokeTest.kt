package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.Building
import com.tgaetaprojects.biophilia.game.DevelopmentCardType.KNIGHT
import com.tgaetaprojects.biophilia.game.Resource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plays whole games with [RandomBots] and checks after every action that cards, pieces and points are conserved.
 */
class RandomPlaySmokeTest {

    @Test
    fun randomGamesStayConsistentAndFinish() {
        val seeds = 1L..40L
        val unfinished = seeds.filter { seed -> playGame(seed).first.phase !is Phase.GameOver }
        assertTrue(unfinished.isEmpty(), "Games with seeds $unfinished hit the action cap without a winner")
    }

    @Test
    fun theSameSeedAndActionsReplayTheSameGame() {
        val (first, firstEvents) = playGame(3L)
        val (second, secondEvents) = playGame(3L)
        assertEquals(firstEvents, secondEvents)
        assertEquals(first.phase, second.phase)
    }

    /** Plays one game with seeded bots, checking every action; returns the game and every event it produced. */
    private fun playGame(seed: Long): Pair<Game, List<GameEvent>> {
        val game = RandomBots.game(seed)
        val events = mutableListOf<GameEvent>()
        var progressCardsPlayed = 0
        RandomBots.play(game, seed) { actor, action, produced ->
            events += produced
            progressCardsPlayed += produced.count { it is GameEvent.DevCardPlayed && it.card != KNIGHT }
            checkInvariants(game, progressCardsPlayed, "seed $seed, event ${events.size} after $actor's $action")
        }
        return game to events
    }

    private fun checkInvariants(game: Game, progressCardsPlayed: Int, where: String) {
        for (resource in Resource.entries) {
            val held = game.players.sumOf { it.hand[resource] }
            assertEquals(19, game.bank[resource] + held, "$resource not conserved at $where")
        }
        for (player in game.players) {
            val color = player.color
            val settlements = game.board.buildings.values.count { it == Building.settlement(color) }
            val cities = game.board.buildings.values.count { it == Building.city(color) }
            val roads = game.board.roads.values.count { it == color }
            assertEquals(PlayerState.SETTLEMENTS - player.settlementsLeft, settlements, "$color settlements at $where")
            assertEquals(PlayerState.CITIES - player.citiesLeft, cities, "$color cities at $where")
            assertEquals(PlayerState.ROADS - player.roadsLeft, roads, "$color roads at $where")

            val awards = (if (game.longestRoadHolder == color) 2 else 0) + (if (game.largestArmyHolder == color) 2 else 0)
            assertEquals(settlements + 2 * cities + awards, game.publicVictoryPoints(color), "$color points at $where")
        }
        // every card is in the deck, in a hand, or played (knights stay in front of their player)
        val accounted = game.devCardsRemaining + progressCardsPlayed +
            game.players.sumOf { it.devCards.total + it.knightsPlayed }
        assertEquals(25, accounted, "Development cards not conserved at $where")

        game.longestRoadHolder?.let { holder ->
            val length = game.player(holder).longestRoad
            assertTrue(length >= MIN_LONGEST_ROAD && game.players.all { it.longestRoad <= length },
                "$holder holds Longest Road with $length at $where")
        }
    }
}
