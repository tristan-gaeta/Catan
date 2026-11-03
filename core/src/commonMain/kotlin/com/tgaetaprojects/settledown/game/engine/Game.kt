package com.tgaetaprojects.settledown.game.engine

import com.tgaetaprojects.settledown.game.Building
import com.tgaetaprojects.settledown.game.DevelopmentCardType
import com.tgaetaprojects.settledown.game.DevelopmentCardType.KNIGHT
import com.tgaetaprojects.settledown.game.DevelopmentCardType.MONOPOLY
import com.tgaetaprojects.settledown.game.DevelopmentCardType.ROAD_BUILDING
import com.tgaetaprojects.settledown.game.DevelopmentCardType.VICTORY_POINT
import com.tgaetaprojects.settledown.game.DevelopmentCardType.YEAR_OF_PLENTY
import com.tgaetaprojects.settledown.game.DiceTotal
import com.tgaetaprojects.settledown.game.Edge
import com.tgaetaprojects.settledown.game.GameBoard
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.Vertex
import com.tgaetaprojects.settledown.game.engine.Game.Companion.RULES_VERSION
import com.tgaetaprojects.settledown.game.engine.RuleViolation.BANK_SHORTAGE
import com.tgaetaprojects.settledown.game.engine.RuleViolation.DECK_EMPTY
import com.tgaetaprojects.settledown.game.engine.RuleViolation.DEV_CARD_ALREADY_PLAYED
import com.tgaetaprojects.settledown.game.engine.RuleViolation.GAME_OVER
import com.tgaetaprojects.settledown.game.engine.RuleViolation.ILLEGAL_LOCATION
import com.tgaetaprojects.settledown.game.engine.RuleViolation.INSUFFICIENT_RESOURCES
import com.tgaetaprojects.settledown.game.engine.RuleViolation.INVALID_DISCARD
import com.tgaetaprojects.settledown.game.engine.RuleViolation.INVALID_ROBBER_MOVE
import com.tgaetaprojects.settledown.game.engine.RuleViolation.INVALID_TRADE
import com.tgaetaprojects.settledown.game.engine.RuleViolation.INVALID_VICTIM
import com.tgaetaprojects.settledown.game.engine.RuleViolation.NOT_YOUR_TURN
import com.tgaetaprojects.settledown.game.engine.RuleViolation.NO_PIECES_LEFT
import com.tgaetaprojects.settledown.game.engine.RuleViolation.NO_PLAYABLE_CARD
import com.tgaetaprojects.settledown.game.engine.RuleViolation.NO_SUCH_OFFER
import com.tgaetaprojects.settledown.game.engine.RuleViolation.UNKNOWN_PLAYER
import com.tgaetaprojects.settledown.game.engine.RuleViolation.WRONG_PHASE
import com.tgaetaprojects.settledown.game.random.GameRandom
import com.tgaetaprojects.settledown.game.random.shuffleWith
import com.tgaetaprojects.settledown.utils.EnumMap
import com.tgaetaprojects.settledown.utils.MutableEnumMap
import com.tgaetaprojects.settledown.utils.asReadOnly
import com.tgaetaprojects.settledown.utils.toEnumMap
import com.tgaetaprojects.settledown.utils.toEnumSet

/**
 * The rules engine for one game of the base rules, using the combined trade/build phase.
 *
 * All state lives here and is changed in place, only through [apply]. Each action is fully validated before
 * anything changes, so a [ActionResult.Rejected] action leaves the game untouched.
 *
 * A game is server-side state: it holds every player's hidden cards, the deck order and the random generator.
 * Players and bots get [viewFor] instead. The same seeds and the same actions always replay to the same game, as
 * long as [RULES_VERSION] is unchanged.
 *
 * Diagram: [Who may see what](https://github.com/tristan-gaeta/Catan/blob/main/docs/images/trust-boundary.svg).
 *
 * See [Rules engine](https://github.com/tristan-gaeta/Catan/blob/main/docs/rules-engine.md).
 *
 * @param seats 3 or 4 distinct colors in seating (clockwise) order. Who starts is decided by dice in
 *   [Phase.RollForFirstPlayer]; play then goes clockwise from them.
 */
class Game internal constructor(
    seats: List<PlayerColor>,
    val board: GameBoard,
    internal val random: GameRandom,
    shuffleDeck: Boolean
) {
    /**
     * A new game. [boardSeed] is public: it fixes the layout, and clients can rebuild the board from it. [gameSeed]
     * is secret: it decides every dice roll, card draw and steal, so it must never leave the server. Draw both
     * independently of a cryptographically secure source; never derive one from the other or from anything a
     * player can see.
     */
    constructor(seats: List<PlayerColor>, boardSeed: Long, gameSeed: Long) :
            this(seats, GameBoard(boardSeed), GameRandom(gameSeed), shuffleDeck = true)

    init {
        require(seats.size in 3..4) { "The base game is for 3 or 4 players, not ${seats.size}" }
        require(seats.toEnumSet().size == seats.size) { "Duplicate player colors: $seats" }
    }

    /**
     * Where dice rolls and stolen cards come from; replaced in tests and in [copy]. Internal so nothing outside the
     * engine can draw from the real game's generator, which would desynchronize it from its record.
     */
    internal var chance: Chance = SeededChance(random)

    /** Everyone's seat, clockwise. */
    val seatOrder: List<PlayerColor> = seats.toList()

    /** Seats in playing order, starting with the first player; the seat order until the first player is rolled. */
    var turnOrder: List<PlayerColor> = seatOrder
        private set

    private val states: EnumMap<PlayerColor, PlayerState> =
        seatOrder.map { it to PlayerState(it) }.toEnumMap()

    /** Every player's state, in [turnOrder]. */
    var players: List<PlayerState> = seatOrder.map { states.getValue(it) }
        private set

    /** The player who rolled highest at the start, or `null` while that's still being rolled. */
    val firstPlayer: PlayerColor? get() = if (phase is Phase.RollForFirstPlayer) null else turnOrder.first()

    var bank: ResourceCounts = ResourceCounts.FULL_BANK
        internal set

    /** The face-down deck, top card first. Its order is secret server-side state. */
    internal val devDeck: ArrayDeque<DevelopmentCardType> =
        if (shuffleDeck) ArrayDeque(
            newDevelopmentDeck().toMutableList().apply { shuffleWith(random) }) else ArrayDeque()
    val devCardsRemaining: Int get() = devDeck.size

    var phase: Phase = Phase.RollForFirstPlayer(seatOrder, emptyMap())
        internal set
    var currentPlayer: PlayerColor = seatOrder.first()
        internal set

    /** The current turn number, starting at 1 after set-up; 0 during set-up. */
    var turn: Int = 0
        internal set

    /** This turn's roll; `null` during set-up and until the current player rolls. */
    var lastRoll: DiceTotal? = null
        internal set

    var longestRoadHolder: PlayerColor? = null
        internal set
    var largestArmyHolder: PlayerColor? = null
        internal set

    internal var devCardPlayedThisTurn = false

    internal val offers = mutableListOf<TradeOffer>()
    internal var nextOfferId = 1

    /** How many set-up placements (settlement plus road) are done; drives the snake order. */
    internal var setupStep = 0

    /** Open domestic trade offers; all are withdrawn when the turn ends. */
    val tradeOffers: List<TradeOffer> get() = offers.toList()

    fun player(color: PlayerColor): PlayerState =
        states[color] ?: throw IllegalArgumentException("$color is not playing")

    // ---- Scoring ----

    /** Points everyone can see: buildings and the two special cards. */
    fun publicVictoryPoints(color: PlayerColor): Int {
        val player = player(color)
        return (PlayerState.SETTLEMENTS - player.settlementsLeft) +
                2 * (PlayerState.CITIES - player.citiesLeft) +
                (if (longestRoadHolder == color) 2 else 0) +
                (if (largestArmyHolder == color) 2 else 0)
    }

    /** [publicVictoryPoints] plus hidden victory point cards; this is what wins the game. */
    fun totalVictoryPoints(color: PlayerColor): Int =
        publicVictoryPoints(color) + player(color).devCards[VICTORY_POINT]

    // ---- Queries for UIs and bots ----

    /** Where [color] may place a settlement now: free in set-up, connected by road afterward. */
    fun legalSettlementSpots(color: PlayerColor): List<Vertex> {
        if (player(color).settlementsLeft == 0) return emptyList()
        val requireRoad = phase !is Phase.SetupSettlement
        return Vertex.entries.filter { board.canPlaceSettlement(color, it, requireRoad) }
    }

    /** Where [color] may place a road now; during set-up only next to the settlement just placed. */
    fun legalRoadSpots(color: PlayerColor): List<Edge> {
        if (player(color).roadsLeft == 0) return emptyList()
        return when (val p = phase) {
            is Phase.SetupRoad ->
                if (color == currentPlayer) p.settlement.edges.filter { it !in board.roads } else emptyList()

            else -> Edge.entries.filter { board.canPlaceRoad(color, it) }
        }
    }

    fun legalCitySpots(color: PlayerColor): List<Vertex> =
        Vertex.entries.filter { board.canUpgradeToCity(color, it) }

    fun maritimeRate(color: PlayerColor, resource: Resource): Int =
        board.maritimeRate(color, resource)

    /** Who the current player could rob by moving the robber to [hex]. */
    fun robberVictims(hex: Hex): Set<PlayerColor> = board.robberVictims(hex, currentPlayer)

    /** How many cards of [type] [color] could play now, ignoring timing: held minus bought this turn. */
    fun playableCount(color: PlayerColor, type: DevelopmentCardType): Int {
        val player = player(color)
        return player.devCards[type] - player.boughtThisTurn[type]
    }

    // ---- Actions ----

    /** Validates [action] by [actor] and, if legal, applies it. */
    fun apply(actor: PlayerColor, action: Action): ActionResult {
        if (actor !in states) return ActionResult.Rejected(UNKNOWN_PLAYER)
        if (phase is Phase.GameOver) return ActionResult.Rejected(GAME_OVER)

        val events = mutableListOf<GameEvent>()
        val violation = when (action) {
            // actions other players may take
            is Action.Discard -> discard(actor, action.cards, events)
            is Action.ProposeTrade -> proposeTrade(actor, action, events)
            is Action.AcceptTrade -> acceptTrade(actor, action.offerId, events)
            is Action.CancelTrade -> cancelTrade(actor, action.offerId, events)
            else -> if (actor != currentPlayer) NOT_YOUR_TURN else when (action) {
                is Action.PlaceSettlement -> placeSetupSettlement(action.vertex, events)
                is Action.PlaceRoad -> placeFreeRoad(action.edge, events)
                Action.RollDice -> rollDice(events)
                is Action.MoveRobber -> moveRobber(action.hex, action.victim, events)
                is Action.BuildRoad -> buildRoad(action.edge, events)
                is Action.BuildSettlement -> buildSettlement(action.vertex, events)
                is Action.BuildCity -> buildCity(action.vertex, events)
                Action.BuyDevCard -> buyDevCard(events)
                Action.PlayKnight -> playKnight(events)
                Action.PlayRoadBuilding -> playRoadBuilding(events)
                is Action.PlayYearOfPlenty -> playYearOfPlenty(action.first, action.second, events)
                is Action.PlayMonopoly -> playMonopoly(action.resource, events)
                is Action.MaritimeTrade -> maritimeTrade(action, events)
                Action.EndTurn -> endTurn(events)
                is Action.Discard, is Action.ProposeTrade, is Action.AcceptTrade, is Action.CancelTrade ->
                    error("handled above")
            }
        }
        if (violation != null) return ActionResult.Rejected(violation)

        checkVictory(events)
        return ActionResult.Accepted(events)
    }

    private val current: PlayerState get() = player(currentPlayer)

    // ---- Choosing the first player ----

    /**
     * Each contender rolls once, in seat order. The highest roll goes first; if the highest roll is tied, just the
     * tied players roll again. (The rulebook only says the highest roll starts; re-rolling ties is the usual way
     * to settle them.)
     */
    private fun rollForFirstPlayer(
        p: Phase.RollForFirstPlayer,
        events: MutableList<GameEvent>
    ): RuleViolation? {
        val roll = chance.roll()
        events += GameEvent.DiceRolled(currentPlayer, roll)

        val rolls = MutableEnumMap<PlayerColor, DiceTotal>().also {
            it.putAll(p.rolls); it[currentPlayer] = roll
        }
        val waiting = p.contenders.firstOrNull { it !in rolls }
        if (waiting != null) {
            currentPlayer = waiting
            phase = Phase.RollForFirstPlayer(p.contenders, rolls.asReadOnly())
            return null
        }

        val best = p.contenders.maxOf { rolls.getValue(it).value }
        val tied = p.contenders.filter { rolls.getValue(it).value == best }
        if (tied.size > 1) {
            currentPlayer = tied.first()
            phase = Phase.RollForFirstPlayer(tied, emptyMap())
        } else {
            setFirstPlayer(tied.single())
            currentPlayer = tied.single()
            phase = Phase.SetupSettlement(1)
            events += GameEvent.TurnOrderDecided(turnOrder)
        }
        return null
    }

    /** Fixes the turn order: clockwise around the table from [first]. */
    internal fun setFirstPlayer(first: PlayerColor) {
        val seat = seatOrder.indexOf(first)
        require(seat >= 0) { "$first isn't playing" }
        turnOrder = seatOrder.drop(seat) + seatOrder.take(seat)
        players = turnOrder.map { states.getValue(it) }
    }

    // ---- Set-up ----

    private fun placeSetupSettlement(
        vertex: Vertex,
        events: MutableList<GameEvent>
    ): RuleViolation? {
        val p = phase as? Phase.SetupSettlement ?: return WRONG_PHASE
        if (!board.canPlaceSettlement(
                currentPlayer,
                vertex,
                requireRoad = false
            )
        ) return ILLEGAL_LOCATION

        board.placeBuilding(vertex, Building.settlement(currentPlayer))
        current.settlementsLeft--
        events += GameEvent.SettlementBuilt(currentPlayer, vertex, free = true)

        if (p.round == 2) {
            val income = vertex.hexes.mapNotNull { board.biomes.getValue(it).resource }
                .fold(ResourceCounts.EMPTY) { acc, r -> acc + r }
            takeFromBank(current, income)
            events += GameEvent.StartingResources(currentPlayer, income)
        }
        phase = Phase.SetupRoad(p.round, vertex)
        return null
    }

    /** The set-up road, or one of Road Building's free roads. */
    private fun placeFreeRoad(edge: Edge, events: MutableList<GameEvent>): RuleViolation? {
        when (val p = phase) {
            is Phase.SetupRoad -> {
                if (edge !in p.settlement.edges || edge in board.roads) return ILLEGAL_LOCATION
                layRoad(edge, free = true, events)
                advanceSetup(events)
            }

            is Phase.RoadBuilding -> {
                if (!board.canPlaceRoad(currentPlayer, edge)) return ILLEGAL_LOCATION
                layRoad(edge, free = true, events)
                val remaining = p.remaining - 1
                phase =
                    if (remaining == 0 || current.roadsLeft == 0 || legalRoadSpots(currentPlayer).isEmpty()) {
                        p.returnTo
                    } else {
                        Phase.RoadBuilding(remaining, p.returnTo)
                    }
            }

            else -> return WRONG_PHASE
        }
        return null
    }

    /** Set-up runs as a snake: turn order, then reversed, so the first player places last and then starts. */
    private fun advanceSetup(events: MutableList<GameEvent>) {
        val n = turnOrder.size
        setupStep++
        if (setupStep == 2 * n) {
            startTurn(turnOrder.first(), events)
        } else {
            val round = if (setupStep < n) 1 else 2
            currentPlayer = turnOrder[if (round == 1) setupStep else 2 * n - 1 - setupStep]
            phase = Phase.SetupSettlement(round)
        }
    }

    // ---- Rolling and the robber ----

    private fun rollDice(events: MutableList<GameEvent>): RuleViolation? {
        (phase as? Phase.RollForFirstPlayer)?.let { return rollForFirstPlayer(it, events) }
        if (phase != Phase.PreRoll) return WRONG_PHASE

        val roll = chance.roll()
        lastRoll = roll
        events += GameEvent.DiceRolled(currentPlayer, roll)

        if (roll == DiceTotal.SEVEN) {
            val pending = MutableEnumMap<PlayerColor, Int>()
            for (player in players) {
                val owed = discardAmount(player.hand.total)
                if (owed > 0) pending[player.color] = owed
            }
            phase =
                if (pending.isEmpty()) Phase.MoveRobber(Phase.Main) else Phase.Discard(pending.asReadOnly())
        } else {
            val gains = settleProduction(board.productionClaims(roll), bank)
            for ((color, cards) in gains) takeFromBank(player(color), cards)
            events += GameEvent.ResourcesProduced(gains.asReadOnly())
            phase = Phase.Main
        }
        return null
    }

    private fun discard(
        actor: PlayerColor,
        cards: ResourceCounts,
        events: MutableList<GameEvent>
    ): RuleViolation? {
        val p = phase as? Phase.Discard ?: return WRONG_PHASE
        val owed = p.pending[actor] ?: return INVALID_DISCARD
        val player = player(actor)
        if (cards.total != owed || !player.hand.covers(cards)) return INVALID_DISCARD

        player.hand -= cards
        bank += cards
        events += GameEvent.Discarded(actor, cards)

        val stillOwed = p.pending - actor
        phase =
            if (stillOwed.isEmpty()) Phase.MoveRobber(Phase.Main) else Phase.Discard(stillOwed.asReadOnly())
        return null
    }

    private fun moveRobber(
        hex: Hex,
        victim: PlayerColor?,
        events: MutableList<GameEvent>
    ): RuleViolation? {
        val p = phase as? Phase.MoveRobber ?: return WRONG_PHASE
        if (hex == board.robber) return INVALID_ROBBER_MOVE
        val victims = board.robberVictims(hex, currentPlayer)
        if (if (victim == null) victims.isNotEmpty() else victim !in victims) return INVALID_VICTIM

        board.robber = hex
        events += GameEvent.RobberMoved(currentPlayer, hex)

        if (victim != null) {
            val target = player(victim)
            val stolen = if (target.hand.isEmpty) null else chance.stealFrom(target.hand)
            if (stolen != null) {
                target.hand -= stolen
                current.hand += stolen
            }
            events += GameEvent.Stole(currentPlayer, victim, stolen)
        }
        phase = p.returnTo
        return null
    }

    // ---- Building ----

    private fun buildRoad(edge: Edge, events: MutableList<GameEvent>): RuleViolation? {
        if (phase != Phase.Main) return WRONG_PHASE
        if (current.roadsLeft == 0) return NO_PIECES_LEFT
        if (!current.hand.covers(ResourceCounts.ROAD)) return INSUFFICIENT_RESOURCES
        if (!board.canPlaceRoad(currentPlayer, edge)) return ILLEGAL_LOCATION

        payBank(current, ResourceCounts.ROAD)
        layRoad(edge, free = false, events)
        return null
    }

    private fun layRoad(edge: Edge, free: Boolean, events: MutableList<GameEvent>) {
        board.placeRoad(edge, currentPlayer)
        current.roadsLeft--
        events += GameEvent.RoadBuilt(currentPlayer, edge, free)
        updateLongestRoad(events)
    }

    private fun buildSettlement(vertex: Vertex, events: MutableList<GameEvent>): RuleViolation? {
        if (phase != Phase.Main) return WRONG_PHASE
        if (current.settlementsLeft == 0) return NO_PIECES_LEFT
        if (!current.hand.covers(ResourceCounts.SETTLEMENT)) return INSUFFICIENT_RESOURCES
        if (!board.canPlaceSettlement(
                currentPlayer,
                vertex,
                requireRoad = true
            )
        ) return ILLEGAL_LOCATION

        payBank(current, ResourceCounts.SETTLEMENT)
        board.placeBuilding(vertex, Building.settlement(currentPlayer))
        current.settlementsLeft--
        events += GameEvent.SettlementBuilt(currentPlayer, vertex, free = false)
        // a new settlement can split an opponent's road
        updateLongestRoad(events)
        return null
    }

    private fun buildCity(vertex: Vertex, events: MutableList<GameEvent>): RuleViolation? {
        if (phase != Phase.Main) return WRONG_PHASE
        if (current.citiesLeft == 0) return NO_PIECES_LEFT
        if (!current.hand.covers(ResourceCounts.CITY)) return INSUFFICIENT_RESOURCES
        if (!board.canUpgradeToCity(currentPlayer, vertex)) return ILLEGAL_LOCATION

        payBank(current, ResourceCounts.CITY)
        board.placeBuilding(vertex, Building.city(currentPlayer))
        current.citiesLeft--
        current.settlementsLeft++
        events += GameEvent.CityBuilt(currentPlayer, vertex)
        return null
    }

    // ---- Development cards ----

    private fun buyDevCard(events: MutableList<GameEvent>): RuleViolation? {
        if (phase != Phase.Main) return WRONG_PHASE
        if (devDeck.isEmpty()) return DECK_EMPTY
        if (!current.hand.covers(ResourceCounts.DEV_CARD)) return INSUFFICIENT_RESOURCES

        payBank(current, ResourceCounts.DEV_CARD)
        val card = devDeck.removeFirst()
        current.devCards += card
        current.boughtThisTurn += card
        events += GameEvent.DevCardBought(currentPlayer, card)
        return null
    }

    /** Timing shared by every playable card: before or after the roll, once per turn, not bought this turn. */
    private fun checkCanPlay(type: DevelopmentCardType): RuleViolation? = when {
        phase != Phase.PreRoll && phase != Phase.Main -> WRONG_PHASE
        devCardPlayedThisTurn -> DEV_CARD_ALREADY_PLAYED
        playableCount(currentPlayer, type) <= 0 -> NO_PLAYABLE_CARD
        else -> null
    }

    private fun consumeCard(type: DevelopmentCardType, events: MutableList<GameEvent>) {
        current.devCards -= type
        devCardPlayedThisTurn = true
        events += GameEvent.DevCardPlayed(currentPlayer, type)
    }

    private fun playKnight(events: MutableList<GameEvent>): RuleViolation? {
        checkCanPlay(KNIGHT)?.let { return it }
        consumeCard(KNIGHT, events)
        current.knightsPlayed++
        updateLargestArmy(events)
        phase = Phase.MoveRobber(returnTo = phase)
        return null
    }

    private fun playRoadBuilding(events: MutableList<GameEvent>): RuleViolation? {
        checkCanPlay(ROAD_BUILDING)?.let { return it }
        consumeCard(ROAD_BUILDING, events)
        // with no pieces or nowhere to build, the card is simply spent
        val roads = minOf(2, current.roadsLeft)
        if (roads > 0 && legalRoadSpots(currentPlayer).isNotEmpty()) phase =
            Phase.RoadBuilding(roads, returnTo = phase)
        return null
    }

    private fun playYearOfPlenty(
        first: Resource,
        second: Resource,
        events: MutableList<GameEvent>
    ): RuleViolation? {
        checkCanPlay(YEAR_OF_PLENTY)?.let { return it }
        val cards = ResourceCounts.of(first, second)
        if (!bank.covers(cards)) return BANK_SHORTAGE

        consumeCard(YEAR_OF_PLENTY, events)
        takeFromBank(current, cards)
        events += GameEvent.YearOfPlenty(currentPlayer, cards)
        return null
    }

    private fun playMonopoly(resource: Resource, events: MutableList<GameEvent>): RuleViolation? {
        checkCanPlay(MONOPOLY)?.let { return it }
        consumeCard(MONOPOLY, events)

        val taken = MutableEnumMap<PlayerColor, Int>()
        for (other in players) {
            if (other.color == currentPlayer) continue
            val count = other.hand[resource]
            if (count == 0) continue
            other.hand = other.hand.with(resource, 0)
            current.hand = current.hand.with(resource, current.hand[resource] + count)
            taken[other.color] = count
        }
        events += GameEvent.Monopolized(currentPlayer, resource, taken.asReadOnly())
        return null
    }

    // ---- Trade ----

    private fun maritimeTrade(
        trade: Action.MaritimeTrade,
        events: MutableList<GameEvent>
    ): RuleViolation? {
        if (phase != Phase.Main) return WRONG_PHASE
        if (trade.give == trade.get || trade.lots < 1) return INVALID_TRADE
        val cost = board.maritimeRate(currentPlayer, trade.give) * trade.lots
        if (current.hand[trade.give] < cost) return INSUFFICIENT_RESOURCES
        if (bank[trade.get] < trade.lots) return BANK_SHORTAGE

        val gave = ResourceCounts.of(trade.give, cost)
        val got = ResourceCounts.of(trade.get, trade.lots)
        payBank(current, gave)
        takeFromBank(current, got)
        events += GameEvent.MaritimeTraded(currentPlayer, gave, got)
        return null
    }

    private fun proposeTrade(
        actor: PlayerColor,
        trade: Action.ProposeTrade,
        events: MutableList<GameEvent>
    ): RuleViolation? {
        if (phase != Phase.Main) return WRONG_PHASE
        if (trade.to !in states || trade.to == actor) return INVALID_TRADE
        // every trade involves the player whose turn it is
        if (actor != currentPlayer && trade.to != currentPlayer) return NOT_YOUR_TURN
        if (!isValidTradeShape(trade.give, trade.get)) return INVALID_TRADE
        if (!player(actor).hand.covers(trade.give)) return INSUFFICIENT_RESOURCES

        val offer = TradeOffer(nextOfferId++, actor, trade.to, trade.give, trade.get)
        offers += offer
        events += GameEvent.TradeProposed(offer)
        return null
    }

    private fun acceptTrade(
        actor: PlayerColor,
        offerId: Int,
        events: MutableList<GameEvent>
    ): RuleViolation? {
        if (phase != Phase.Main) return WRONG_PHASE
        val offer = offers.find { it.id == offerId } ?: return NO_SUCH_OFFER
        if (actor != offer.to) return NOT_YOUR_TURN
        val from = player(offer.from)
        val to = player(offer.to)
        // hands may have changed since the offer was made
        if (!from.hand.covers(offer.give) || !to.hand.covers(offer.get)) return INSUFFICIENT_RESOURCES

        from.hand = from.hand - offer.give + offer.get
        to.hand = to.hand - offer.get + offer.give
        offers.remove(offer)
        events += GameEvent.TradeAccepted(offer)
        return null
    }

    private fun cancelTrade(
        actor: PlayerColor,
        offerId: Int,
        events: MutableList<GameEvent>
    ): RuleViolation? {
        val offer = offers.find { it.id == offerId } ?: return NO_SUCH_OFFER
        if (actor != offer.from && actor != offer.to) return NOT_YOUR_TURN
        offers.remove(offer)
        events += GameEvent.TradeCancelled(offer, actor)
        return null
    }

    // ---- Turn flow ----

    private fun endTurn(events: MutableList<GameEvent>): RuleViolation? {
        if (phase != Phase.Main) return WRONG_PHASE
        current.boughtThisTurn = DevCardCounts.EMPTY
        startTurn(turnOrder[(turnOrder.indexOf(currentPlayer) + 1) % turnOrder.size], events)
        return null
    }

    private fun startTurn(color: PlayerColor, events: MutableList<GameEvent>) {
        currentPlayer = color
        turn++
        lastRoll = null
        phase = Phase.PreRoll
        devCardPlayedThisTurn = false
        offers.clear()
        events += GameEvent.TurnStarted(color, turn)
    }

    /**
     * A player wins the moment they have 10 points on their own turn, counting hidden victory point cards. Run after
     * every accepted action, including the one that starts a turn, since points can be gained on another player's
     * turn (e.g. Longest Road passing on when a road is broken).
     */
    private fun checkVictory(events: MutableList<GameEvent>) {
        if (turn == 0 || phase is Phase.GameOver) return
        val points = totalVictoryPoints(currentPlayer)
        if (points >= VICTORY_POINTS_TO_WIN) {
            phase = Phase.GameOver(currentPlayer)
            events += GameEvent.GameWon(currentPlayer, points)
        }
    }

    private fun updateLongestRoad(events: MutableList<GameEvent>) {
        for (player in players) player.longestRoad = board.longestRoad(player.color)
        val holder =
            longestRoadHolder(longestRoadHolder, players.associate { it.color to it.longestRoad })
        if (holder != longestRoadHolder) {
            longestRoadHolder = holder
            events += GameEvent.LongestRoadChanged(
                holder,
                holder?.let { player(it).longestRoad } ?: 0)
        }
    }

    private fun updateLargestArmy(events: MutableList<GameEvent>) {
        val holder =
            largestArmyHolder(largestArmyHolder, players.associate { it.color to it.knightsPlayed })
        if (holder != largestArmyHolder && holder != null) {
            largestArmyHolder = holder
            events += GameEvent.LargestArmyChanged(holder, player(holder).knightsPlayed)
        }
    }

    private fun takeFromBank(player: PlayerState, cards: ResourceCounts) {
        bank -= cards
        player.hand += cards
    }

    private fun payBank(player: PlayerState, cards: ResourceCounts) {
        player.hand -= cards
        bank += cards
    }

    // ---- Copying ----

    /**
     * An independent deep copy that draws its dice and steals from [chance], for exploring moves on the server
     * without touching the real game. The copy shares only the immutable layout.
     *
     * The copy carries every secret of the original, including the deck order, so it must stay on the server just
     * like the original. A bot playing a seat should plan from [viewFor] instead. The chance source is required, and
     * may not be the real game's own, so a copy can never foresee the real game's rolls or advance its generator.
     */
    fun copy(chance: Chance): Game {
        require(!(chance is SeededChance && chance.random === random)) {
            "A copy can't draw from the real game's generator; give it its own Chance"
        }
        val copy = Game(seatOrder, board.copy(), random.copy(), shuffleDeck = false)
        if (phase !is Phase.RollForFirstPlayer) copy.setFirstPlayer(turnOrder.first())
        copy.devDeck.addAll(devDeck)
        copy.bank = bank
        copy.phase = phase
        copy.currentPlayer = currentPlayer
        copy.turn = turn
        copy.lastRoll = lastRoll
        copy.longestRoadHolder = longestRoadHolder
        copy.largestArmyHolder = largestArmyHolder
        copy.devCardPlayedThisTurn = devCardPlayedThisTurn
        copy.offers.addAll(offers)
        copy.nextOfferId = nextOfferId
        copy.setupStep = setupStep
        for (player in players) copy.player(player.color).copyFrom(player)
        copy.chance = chance
        return copy
    }

    companion object {
        const val VICTORY_POINTS_TO_WIN = 10

        /**
         * The version of the rules, the board generator, and the order in which they draw random numbers. Recorded
         * games only replay correctly on the same version, so bump it whenever a change would alter how a recorded
         * game unfolds. `VersionGuardTest` pins this version's behavior and fails if it changes without a bump.
         */
        const val RULES_VERSION = 2
    }
}
