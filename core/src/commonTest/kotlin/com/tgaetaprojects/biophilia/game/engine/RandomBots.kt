package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.DevelopmentCardType
import com.tgaetaprojects.biophilia.game.DevelopmentCardType.*
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.PlayerColor.*
import com.tgaetaprojects.biophilia.game.Resource
import com.tgaetaprojects.biophilia.game.random.GameRandom
import com.tgaetaprojects.biophilia.game.random.pickWith
import kotlin.test.fail

/**
 * Test bots that play whole games by picking random actions the query functions say are legal: greedy about
 * cities and settlements, random about everything else. Used to exercise the engine and the codecs on realistic
 * positions. Any refused action fails the test, since it means validation and queries disagree.
 */
internal object RandomBots {
    const val MAX_ACTIONS = 20_000

    /** Odd seeds give 3-player games, even seeds 4. */
    fun colorsFor(seed: Long): List<PlayerColor> = listOf(RED, WHITE, BLUE, ORANGE).take(if (seed % 2 == 0L) 4 else 3)

    /** Distinct board and game seeds for test game [seed]; real games draw them independently. */
    fun boardSeedFor(seed: Long): Long = seed * 1_000_003
    fun gameSeedFor(seed: Long): Long = seed * 7_919 + 13

    /** A fresh game for test [seed], still in the opening roll-off. */
    fun game(seed: Long): Game = Game(colorsFor(seed), boardSeedFor(seed), gameSeedFor(seed))

    /**
     * Bot choices come from [GameRandom], whose sequence is fixed across platforms and Kotlin versions, so a bot's
     * game is as reproducible as the engine's (the version guard depends on that).
     *
     * Plays [game] until someone wins or [maxActions] actions have been taken, with bot choices seeded by [botSeed].
     * Actions go through [apply] (pass a `GameRecorder`'s to record the game), and [onAccepted] sees each one with
     * its events.
     */
    fun play(
        game: Game,
        botSeed: Long,
        maxActions: Int = MAX_ACTIONS,
        apply: (PlayerColor, Action) -> ActionResult = game::apply,
        onAccepted: (actor: PlayerColor, action: Action, events: List<GameEvent>) -> Unit = { _, _, _ -> }
    ) {
        val bot = GameRandom(botSeed)
        var actionsThisTurn = 0
        var lastTurn = 0
        for (step in 0 until maxActions) {
            if (game.phase is Phase.GameOver) return
            if (game.turn != lastTurn) {
                lastTurn = game.turn
                actionsThisTurn = 0
            }
            val (actor, action) = chooseAction(game, bot, actionsThisTurn++)
            val result = apply(actor, action)
            if (result !is ActionResult.Accepted) {
                fail("bot seed $botSeed, step $step: $actor's $action was refused with $result in ${game.phase}")
            }
            onAccepted(actor, action, result.events)
        }
    }

    private fun chooseAction(game: Game, bot: GameRandom, actionsThisTurn: Int): Pair<PlayerColor, Action> {
        val me = game.currentPlayer
        return when (val phase = game.phase) {
            is Phase.RollForFirstPlayer -> me to Action.RollDice
            is Phase.SetupSettlement -> me to Action.PlaceSettlement(game.legalSettlementSpots(me).pickWith(bot))
            is Phase.SetupRoad, is Phase.RoadBuilding -> me to Action.PlaceRoad(game.legalRoadSpots(me).pickWith(bot))
            Phase.PreRoll ->
                if (canPlay(game, KNIGHT) && bot.nextInt(3) == 0) me to Action.PlayKnight
                else me to Action.RollDice
            is Phase.Discard -> {
                val (color, owed) = phase.pending.entries.first()
                color to Action.Discard(randomCards(game.player(color).hand, owed, bot))
            }
            is Phase.MoveRobber -> {
                val hex = Hex.entries.filter { it != game.board.robber }.pickWith(bot)
                me to Action.MoveRobber(hex, game.robberVictims(hex).toList().pickOrNull(bot))
            }
            Phase.Main -> mainAction(game, bot, actionsThisTurn)
            is Phase.GameOver -> error("game is over")
        }
    }

    private fun mainAction(game: Game, bot: GameRandom, actionsThisTurn: Int): Pair<PlayerColor, Action> {
        val me = game.currentPlayer
        val player = game.player(me)
        val hand = player.hand

        // answer any open offer first
        game.tradeOffers.firstOrNull()?.let { offer ->
            val recipient = game.player(offer.to)
            return offer.to to if (recipient.hand.covers(offer.get) && bot.nextInt(2) == 0) {
                Action.AcceptTrade(offer.id)
            } else {
                Action.CancelTrade(offer.id)
            }
        }
        if (actionsThisTurn > 40) return me to Action.EndTurn

        if (hand.covers(ResourceCounts.CITY) && player.citiesLeft > 0) {
            game.legalCitySpots(me).pickOrNull(bot)?.let { return me to Action.BuildCity(it) }
        }
        if (hand.covers(ResourceCounts.SETTLEMENT) && player.settlementsLeft > 0) {
            game.legalSettlementSpots(me).pickOrNull(bot)?.let { return me to Action.BuildSettlement(it) }
        }

        val options = mutableListOf<Action>(Action.EndTurn, Action.EndTurn)
        if (hand.covers(ResourceCounts.ROAD) && player.roadsLeft > 0) {
            game.legalRoadSpots(me).pickOrNull(bot)?.let { options += Action.BuildRoad(it) }
        }
        if (hand.covers(ResourceCounts.DEV_CARD) && game.devCardsRemaining > 0) options += Action.BuyDevCard
        if (canPlay(game, KNIGHT)) options += Action.PlayKnight
        if (canPlay(game, ROAD_BUILDING)) options += Action.PlayRoadBuilding
        if (canPlay(game, MONOPOLY)) options += Action.PlayMonopoly(Resource.entries.pickWith(bot))
        if (canPlay(game, YEAR_OF_PLENTY)) {
            val (a, b) = Resource.entries.pickWith(bot) to Resource.entries.pickWith(bot)
            if (game.bank.covers(ResourceCounts.of(a, b))) options += Action.PlayYearOfPlenty(a, b)
        }
        for (give in hand.types) {
            if (hand[give] < game.maritimeRate(me, give)) continue
            val get = Resource.entries.filter { it != give && game.bank[it] > 0 }.pickOrNull(bot) ?: continue
            options += Action.MaritimeTrade(give, get)
        }
        if (!hand.isEmpty) {
            val give = hand.types.pickWith(bot)
            val get = Resource.entries.filter { it != give }.pickWith(bot)
            val partner = game.turnOrder.filter { it != me }.pickWith(bot)
            options += Action.ProposeTrade(partner, ResourceCounts.of(give, 1), ResourceCounts.of(get, 1))
        }
        return me to options.pickWith(bot)
    }

    private fun <T> List<T>.pickOrNull(bot: GameRandom): T? = if (isEmpty()) null else pickWith(bot)

    private fun canPlay(game: Game, type: DevelopmentCardType) =
        !game.devCardPlayedThisTurn && game.playableCount(game.currentPlayer, type) > 0

    private fun randomCards(hand: ResourceCounts, count: Int, bot: GameRandom): ResourceCounts {
        var left = hand
        var picked = ResourceCounts.EMPTY
        repeat(count) {
            val card = left.cardAt(bot.nextInt(left.total))
            left -= card
            picked += card
        }
        return picked
    }
}
