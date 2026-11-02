package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.Building
import com.tgaetaprojects.biophilia.game.DevelopmentCardType
import com.tgaetaprojects.biophilia.game.DevelopmentCardType.*
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.PlayerColor.*
import com.tgaetaprojects.biophilia.game.Resource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Plays whole games with bots that pick random actions the query functions say are legal, and checks after every
 * action that the engine accepted it and that cards and pieces are conserved. A rejection means the engine's
 * validation and its queries disagree.
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

    /**
     * Pins seed 3's whole event log to a fingerprint recorded earlier. Replaying twice in one JVM can't catch
     * iteration over identity-hashed collections (enum hash codes change between JVM runs); this can.
     *
     * Any deliberate change to the rules, the board generator or the bots changes the log: check the new game
     * still looks right, then update [REPLAY_FINGERPRINT].
     */
    @Test
    fun aSeededGameMatchesItsRecordedFingerprint() {
        val (game, events) = playGame(3L)
        val fingerprint = events.joinToString("\n").hashCode()
        assertEquals(REPLAY_FINGERPRINT, fingerprint, "Seed 3 replayed differently (${events.size} events, ${game.phase})")
    }

    /** Plays one game with seeded bots, checking every action; returns the game and every event it produced. */
    private fun playGame(seed: Long): Pair<Game, List<GameEvent>> {
        val colors = listOf(RED, WHITE, BLUE, ORANGE).take(if (seed % 2 == 0L) 4 else 3)
        val game = Game(colors, seed)
        val bot = Random(seed)
        val events = mutableListOf<GameEvent>()
        var actionsThisTurn = 0
        var lastTurn = 0
        var progressCardsPlayed = 0

        for (step in 0 until MAX_ACTIONS) {
            if (game.phase is Phase.GameOver) break
            if (game.turn != lastTurn) {
                lastTurn = game.turn
                actionsThisTurn = 0
            }
            val (actor, action) = chooseAction(game, bot, actionsThisTurn++)
            val result = game.apply(actor, action)
            if (result !is ActionResult.Accepted) {
                fail("seed $seed, step $step: $actor's $action was refused with $result in ${game.phase}")
            }
            events += result.events
            progressCardsPlayed += result.events.count { it is GameEvent.DevCardPlayed && it.card != KNIGHT }
            checkInvariants(game, progressCardsPlayed, "seed $seed, step $step after $actor's $action")
        }
        return game to events
    }

    private fun chooseAction(game: Game, bot: Random, actionsThisTurn: Int): Pair<PlayerColor, Action> {
        val me = game.currentPlayer
        return when (val phase = game.phase) {
            is Phase.SetupSettlement -> me to Action.PlaceSettlement(game.legalSettlementSpots(me).random(bot))
            is Phase.SetupRoad, is Phase.RoadBuilding -> me to Action.PlaceRoad(game.legalRoadSpots(me).random(bot))
            Phase.PreRoll ->
                if (canPlay(game, KNIGHT) && bot.nextInt(3) == 0) me to Action.PlayKnight
                else me to Action.RollDice
            is Phase.Discard -> {
                val (color, owed) = phase.pending.entries.first()
                color to Action.Discard(randomCards(game.player(color).hand, owed, bot))
            }
            is Phase.MoveRobber -> {
                val hex = Hex.entries.filter { it != game.board.robber }.random(bot)
                me to Action.MoveRobber(hex, game.robberVictims(hex).randomOrNull(bot))
            }
            Phase.Main -> mainAction(game, bot, actionsThisTurn)
            is Phase.GameOver -> error("game is over")
        }
    }

    private fun mainAction(game: Game, bot: Random, actionsThisTurn: Int): Pair<PlayerColor, Action> {
        val me = game.currentPlayer
        val player = game.player(me)
        val hand = player.hand

        // answer any open offer first
        game.tradeOffers.firstOrNull()?.let { offer ->
            val recipient = game.player(offer.to)
            return offer.to to if (recipient.hand.covers(offer.get) && bot.nextBoolean()) {
                Action.AcceptTrade(offer.id)
            } else {
                Action.CancelTrade(offer.id)
            }
        }
        if (actionsThisTurn > 40) return me to Action.EndTurn

        // greedy about points, random about everything else
        if (hand.covers(ResourceCounts.CITY) && player.citiesLeft > 0) {
            game.legalCitySpots(me).randomOrNull(bot)?.let { return me to Action.BuildCity(it) }
        }
        if (hand.covers(ResourceCounts.SETTLEMENT) && player.settlementsLeft > 0) {
            game.legalSettlementSpots(me).randomOrNull(bot)?.let { return me to Action.BuildSettlement(it) }
        }

        val options = mutableListOf<Action>(Action.EndTurn, Action.EndTurn)
        if (hand.covers(ResourceCounts.ROAD) && player.roadsLeft > 0) {
            game.legalRoadSpots(me).randomOrNull(bot)?.let { options += Action.BuildRoad(it) }
        }
        if (hand.covers(ResourceCounts.DEV_CARD) && game.devCardsRemaining > 0) options += Action.BuyDevCard
        if (canPlay(game, KNIGHT)) options += Action.PlayKnight
        if (canPlay(game, ROAD_BUILDING)) options += Action.PlayRoadBuilding
        if (canPlay(game, MONOPOLY)) options += Action.PlayMonopoly(Resource.entries.random(bot))
        if (canPlay(game, YEAR_OF_PLENTY)) {
            val (a, b) = Resource.entries.random(bot) to Resource.entries.random(bot)
            if (game.bank.covers(ResourceCounts.of(a, b))) options += Action.PlayYearOfPlenty(a, b)
        }
        for (give in hand.types) {
            if (hand[give] < game.maritimeRate(me, give)) continue
            val get = Resource.entries.filter { it != give && game.bank[it] > 0 }.randomOrNull(bot) ?: continue
            options += Action.MaritimeTrade(give, get)
        }
        if (!hand.isEmpty) {
            val give = hand.types.random(bot)
            val get = Resource.entries.filter { it != give }.random(bot)
            val partner = game.turnOrder.filter { it != me }.random(bot)
            options += Action.ProposeTrade(partner, ResourceCounts.of(give, 1), ResourceCounts.of(get, 1))
        }
        return me to options.random(bot)
    }

    private fun canPlay(game: Game, type: DevelopmentCardType) =
        !game.devCardPlayedThisTurn && game.playableCount(game.currentPlayer, type) > 0

    private fun randomCards(hand: ResourceCounts, count: Int, bot: Random): ResourceCounts {
        var left = hand
        var picked = ResourceCounts.EMPTY
        repeat(count) {
            val card = left.cardAt(bot.nextInt(left.total))
            left -= card
            picked += card
        }
        return picked
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

    companion object {
        private const val MAX_ACTIONS = 20_000
        private const val REPLAY_FINGERPRINT = 208827961
    }
}
