package com.tgaetaprojects.biophilia.game.codec

import com.tgaetaprojects.biophilia.game.Building
import com.tgaetaprojects.biophilia.game.DiceTotal
import com.tgaetaprojects.biophilia.game.Edge
import com.tgaetaprojects.biophilia.game.GameBoard
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.Vertex
import com.tgaetaprojects.biophilia.game.codec.LayoutCodec.readLayout
import com.tgaetaprojects.biophilia.game.codec.LayoutCodec.writeLayout
import com.tgaetaprojects.biophilia.game.engine.Game
import com.tgaetaprojects.biophilia.game.engine.Phase
import com.tgaetaprojects.biophilia.game.engine.PlayerState
import com.tgaetaprojects.biophilia.game.engine.PlayerSummary
import com.tgaetaprojects.biophilia.game.engine.PlayerView
import com.tgaetaprojects.biophilia.game.engine.ResourceCounts
import com.tgaetaprojects.biophilia.game.engine.SeededChance
import com.tgaetaprojects.biophilia.game.engine.TradeOffer
import com.tgaetaprojects.biophilia.game.engine.longestRoad
import com.tgaetaprojects.biophilia.game.random.GameRandom
import com.tgaetaprojects.biophilia.utils.MutableEnumMap
import com.tgaetaprojects.biophilia.utils.asReadOnly

/**
 * Binary snapshots of a whole [Game] and of a [PlayerView].
 *
 * A full snapshot is server-only: it includes every hidden card, the deck order and the random generator's state,
 * so a restored game continues exactly. A view is safe to send to its player.
 *
 * Both start with the seats and the layout, then the board: each of the 72 edges as an optional 2-bit color and
 * each of the 54 intersections as an optional 3-bit building. A full snapshot is about 150 bytes including the
 * layout. Values that follow from others (the bank, pieces left, road lengths) aren't stored; they're recomputed and
 * checked on decode.
 */
object SnapshotCodec {
    private const val DISCARD_WIDTH = 6
    private const val HAND_SIZE_WIDTH = 7
    private const val CARD_COUNT_WIDTH = 5
    private const val KNIGHTS_WIDTH = 4

    /** One fixed tag per phase. Tags are part of the format: never renumber them, only append. */
    private object PhaseTag {
        const val WIDTH = 4

        const val SETUP_SETTLEMENT = 0
        const val SETUP_ROAD = 1
        const val PRE_ROLL = 2
        const val DISCARD = 3
        const val MOVE_ROBBER = 4
        const val MAIN = 5
        const val ROAD_BUILDING = 6
        const val GAME_OVER = 7
        const val ROLL_FOR_FIRST_PLAYER = 8
    }

    // ---- Full snapshots ----

    /** Encodes the complete state. The game must be using its own seeded [SeededChance], not a substitute. */
    fun encode(game: Game): ByteArray = BitWriter().apply {
        val chance = game.chance
        check(chance is SeededChance && chance.random === game.random) {
            "Only games drawing from their own seeded generator can be snapshotted"
        }
        header(BlobKind.SNAPSHOT)
        varint(Game.RULES_VERSION)
        writeSeats(game.seatOrder, game.firstPlayer)
        writeLayout(game.board.layout)
        for (word in game.random.state()) bits(word, 32)

        varint(game.turn)
        varint(game.setupStep)
        writeShared(game.currentPlayer, game.phase, game.lastRoll, game.devCardPlayedThisTurn, game.seatOrder)
        writeBoard(game.board.robber, game.board.roads, game.board.buildings)

        for (color in game.seatOrder) {
            val player = game.player(color)
            counts(player.hand)
            counts(player.devCards)
            counts(player.boughtThisTurn)
            bits(player.knightsPlayed, KNIGHTS_WIDTH)
        }
        bits(game.devDeck.size, CARD_COUNT_WIDTH)
        for (card in game.devDeck) code(card, Codes.DEV_CARD)

        optional(game.longestRoadHolder, Codes.COLOR)
        optional(game.largestArmyHolder, Codes.COLOR)
        writeOffers(game.tradeOffers)
        varint(game.nextOfferId)
    }.toByteArray()

    fun decode(bytes: ByteArray): Game = decoding("snapshot", bytes) {
        header(BlobKind.SNAPSHOT)
        readRulesVersion()
        val (seats, firstPlayer) = readSeats()
        val layout = readLayout()
        val random = GameRandom.fromState(IntArray(4) { bits(32) })
        val game = Game(seats, GameBoard(layout), random, shuffleDeck = false)
        firstPlayer?.let(game::setFirstPlayer)

        game.turn = varint()
        game.setupStep = varint()
        readShared(seats).let { (current, phase, lastRoll, played) ->
            game.currentPlayer = current
            game.phase = phase
            game.lastRoll = lastRoll
            game.devCardPlayedThisTurn = played
        }
        require((game.phase is Phase.RollForFirstPlayer) == (firstPlayer == null)) {
            "A first player must be recorded once, and only once, the opening rolls are done"
        }
        readBoard(seats, game.board)

        for (color in seats) {
            val player = game.player(color)
            player.hand = resourceCounts()
            player.devCards = devCardCounts()
            player.boughtThisTurn = devCardCounts()
            player.knightsPlayed = bits(KNIGHTS_WIDTH)
        }
        repeat(bits(CARD_COUNT_WIDTH)) { game.devDeck.add(code(Codes.DEV_CARD)) }

        game.longestRoadHolder = optional(Codes.COLOR)?.also { require(it in seats) }
        game.largestArmyHolder = optional(Codes.COLOR)?.also { require(it in seats) }
        game.offers += readOffers(seats)
        game.nextOfferId = varint()

        deriveAndCheck(game)
        game
    }

    /** Recomputes what the snapshot leaves out, rejecting states the rules could never reach. */
    private fun deriveAndCheck(game: Game) {
        val buildings = game.board.buildings.values
        for (player in game.players) {
            val color = player.color
            player.settlementsLeft = PlayerState.SETTLEMENTS - buildings.count { it == Building.settlement(color) }
            player.citiesLeft = PlayerState.CITIES - buildings.count { it == Building.city(color) }
            player.roadsLeft = PlayerState.ROADS - game.board.roads.values.count { it == color }
            require(player.settlementsLeft >= 0 && player.citiesLeft >= 0 && player.roadsLeft >= 0) {
                "$color has more pieces on the board than exist"
            }
            player.longestRoad = game.board.longestRoad(color)
        }

        val held = game.players.fold(ResourceCounts.EMPTY) { acc, p -> acc + p.hand }
        require(ResourceCounts.FULL_BANK.covers(held)) { "Players hold more cards than exist: $held" }
        game.bank = ResourceCounts.FULL_BANK - held

        val devCards = game.devDeck.size + game.players.sumOf { it.devCards.total + it.knightsPlayed }
        require(devCards <= 25) { "More than 25 development cards" }

        checkPhase(game)
    }

    /** Rejects phases the engine could never be in, which would otherwise crash or stall once play resumes. */
    private fun checkPhase(game: Game) {
        when (val phase = game.phase) {
            is Phase.RollForFirstPlayer -> {
                require(game.turn == 0 && game.setupStep == 0) { "The roll-off happens before set-up" }
                require(phase.contenders.size >= 2 && phase.rolls.keys.all { it in phase.contenders }) {
                    "A roll-off needs at least two contenders, and only they roll"
                }
                val next = phase.contenders.firstOrNull { it !in phase.rolls }
                require(next != null && game.currentPlayer == next) { "The current player must be the next to roll" }
            }
            is Phase.SetupSettlement, is Phase.SetupRoad -> {
                val players = game.turnOrder.size
                val step = game.setupStep
                require(game.turn == 0 && step < 2 * players) { "Set-up is over after ${2 * players} placements" }
                val round = if (step < players) 1 else 2
                val placing = game.turnOrder[if (round == 1) step else 2 * players - 1 - step]
                val phaseRound = if (phase is Phase.SetupSettlement) phase.round else (phase as Phase.SetupRoad).round
                require(game.currentPlayer == placing && phaseRound == round) { "Set-up placement out of order" }
                if (phase is Phase.SetupRoad) {
                    require(game.board.buildings[phase.settlement] == Building.settlement(placing)) {
                        "The set-up road must follow the player's own new settlement"
                    }
                }
            }
            is Phase.Discard -> {
                require(game.turn >= 1) { "Discards happen during play" }
                for ((color, owed) in phase.pending) {
                    val held = game.player(color).hand.total
                    require(held > 7 && owed == held / 2) { "$color can't owe $owed cards with $held in hand" }
                }
            }
            else -> require(game.turn >= 1) { "${phase::class.simpleName} only happens during play" }
        }
    }

    // ---- Player views ----

    fun encodeView(view: PlayerView): ByteArray = BitWriter().apply {
        header(BlobKind.VIEW)
        varint(Game.RULES_VERSION)
        writeSeats(view.seatOrder, view.firstPlayer)
        code(view.viewer, Codes.COLOR)
        writeLayout(view.layout)

        varint(view.turn)
        writeShared(view.currentPlayer, view.phase, view.lastRoll, view.devCardPlayedThisTurn, view.seatOrder)
        writeBoard(view.robber, view.roads, view.buildings)

        counts(view.bank)
        bits(view.devCardsRemaining, CARD_COUNT_WIDTH)
        for (summary in view.players) {
            bits(summary.handSize, HAND_SIZE_WIDTH)
            bits(summary.devCardCount, CARD_COUNT_WIDTH)
            bits(summary.boughtThisTurnCount, CARD_COUNT_WIDTH)
            bits(summary.knightsPlayed, KNIGHTS_WIDTH)
        }
        counts(view.hand)
        counts(view.devCards)
        counts(view.boughtThisTurn)

        optional(view.longestRoadHolder, Codes.COLOR)
        optional(view.largestArmyHolder, Codes.COLOR)
        writeOffers(view.tradeOffers)
    }.toByteArray()

    fun decodeView(bytes: ByteArray): PlayerView = decoding("player view", bytes) {
        header(BlobKind.VIEW)
        readRulesVersion()
        val (seats, firstPlayer) = readSeats()
        val viewer = code(Codes.COLOR).also { require(it in seats) { "Viewer $it isn't playing" } }
        val layout = readLayout()

        val turn = varint()
        val (current, phase, lastRoll, played) = readShared(seats)
        val board = GameBoard(layout)
        readBoard(seats, board)

        val bank = resourceCounts()
        val deckSize = bits(CARD_COUNT_WIDTH)
        val summaries = seats.map {
            PlayerSummary(it, bits(HAND_SIZE_WIDTH), bits(CARD_COUNT_WIDTH), bits(CARD_COUNT_WIDTH), bits(KNIGHTS_WIDTH))
        }
        val hand = resourceCounts()
        val devCards = devCardCounts()
        val bought = devCardCounts()

        PlayerView(
            viewer = viewer,
            seatOrder = seats,
            firstPlayer = firstPlayer,
            layout = layout,
            turn = turn,
            currentPlayer = current,
            phase = phase,
            lastRoll = lastRoll,
            devCardPlayedThisTurn = played,
            robber = board.robber,
            roads = board.roads.toMap().asReadOnly(),
            buildings = board.buildings.toMap().asReadOnly(),
            bank = bank,
            devCardsRemaining = deckSize,
            longestRoadHolder = optional(Codes.COLOR)?.also { require(it in seats) },
            largestArmyHolder = optional(Codes.COLOR)?.also { require(it in seats) },
            tradeOffers = readOffers(seats),
            players = summaries,
            hand = hand,
            devCards = devCards,
            boughtThisTurn = bought
        )
    }

    // ---- Shared sections ----

    private fun BitReader.readRulesVersion() {
        val version = varint()
        if (version != Game.RULES_VERSION) {
            throw CodecException("Encoded with rules version $version; this engine is version ${Game.RULES_VERSION}")
        }
    }

    /** Seats: 3 or 4 colors, clockwise, then the first player once they've been rolled. */
    private fun BitWriter.writeSeats(seats: List<PlayerColor>, firstPlayer: PlayerColor?) {
        bits(seats.size - 3, 1)
        for (color in seats) code(color, Codes.COLOR)
        optional(firstPlayer, Codes.COLOR)
    }

    private fun BitReader.readSeats(): Pair<List<PlayerColor>, PlayerColor?> {
        val seats = List(bits(1) + 3) { code(Codes.COLOR) }
        require(seats.toSet().size == seats.size) { "Duplicate colors $seats" }
        val first = optional(Codes.COLOR)?.also { require(it in seats) { "First player $it isn't seated" } }
        return seats to first
    }

    private data class Shared(val current: PlayerColor, val phase: Phase, val lastRoll: DiceTotal?, val devCardPlayed: Boolean)

    private fun BitWriter.writeShared(
        current: PlayerColor, phase: Phase, lastRoll: DiceTotal?, devCardPlayed: Boolean, seats: List<PlayerColor>
    ) {
        code(current, Codes.COLOR)
        writePhase(phase, seats)
        optional(lastRoll, Codes.DICE)
        bool(devCardPlayed)
    }

    private fun BitReader.readShared(seats: List<PlayerColor>): Shared {
        val current = code(Codes.COLOR).also { require(it in seats) { "Current player $it isn't playing" } }
        return Shared(current, readPhase(seats), optional(Codes.DICE), bool())
    }

    /** A phase tag, then the phase's fields; the phases that resume another nest it. */
    private fun BitWriter.writePhase(phase: Phase, seats: List<PlayerColor>) {
        when (phase) {
            is Phase.RollForFirstPlayer -> {
                bits(PhaseTag.ROLL_FOR_FIRST_PLAYER, PhaseTag.WIDTH)
                for (color in seats) bool(color in phase.contenders)
                for (color in phase.contenders) optional(phase.rolls[color], Codes.DICE)
            }
            is Phase.SetupSettlement -> {
                bits(PhaseTag.SETUP_SETTLEMENT, PhaseTag.WIDTH)
                bits(phase.round - 1, 1)
            }
            is Phase.SetupRoad -> {
                bits(PhaseTag.SETUP_ROAD, PhaseTag.WIDTH)
                bits(phase.round - 1, 1)
                vertex(phase.settlement)
            }
            Phase.PreRoll -> bits(PhaseTag.PRE_ROLL, PhaseTag.WIDTH)
            is Phase.Discard -> {
                bits(PhaseTag.DISCARD, PhaseTag.WIDTH)
                for (color in seats) bits(phase.pending[color] ?: 0, DISCARD_WIDTH)
            }
            is Phase.MoveRobber -> {
                bits(PhaseTag.MOVE_ROBBER, PhaseTag.WIDTH)
                writePhase(phase.returnTo, seats)
            }
            Phase.Main -> bits(PhaseTag.MAIN, PhaseTag.WIDTH)
            is Phase.RoadBuilding -> {
                bits(PhaseTag.ROAD_BUILDING, PhaseTag.WIDTH)
                bits(phase.remaining, 2)
                writePhase(phase.returnTo, seats)
            }
            is Phase.GameOver -> {
                bits(PhaseTag.GAME_OVER, PhaseTag.WIDTH)
                code(phase.winner, Codes.COLOR)
            }
        }
    }

    private fun BitReader.readPhase(seats: List<PlayerColor>): Phase = when (val tag = bits(PhaseTag.WIDTH)) {
        PhaseTag.ROLL_FOR_FIRST_PLAYER -> {
            // contenders keep seat order, as the engine does when ties re-roll
            val contenders = seats.filter { bool() }
            require(contenders.size >= 2 || contenders == seats) { "A roll-off needs at least two contenders" }
            val rolls = MutableEnumMap<PlayerColor, DiceTotal>()
            for (color in contenders) optional(Codes.DICE)?.let { rolls[color] = it }
            Phase.RollForFirstPlayer(contenders, rolls.asReadOnly())
        }
        PhaseTag.SETUP_SETTLEMENT -> Phase.SetupSettlement(bits(1) + 1)
        PhaseTag.SETUP_ROAD -> Phase.SetupRoad(bits(1) + 1, vertex())
        PhaseTag.PRE_ROLL -> Phase.PreRoll
        PhaseTag.DISCARD -> {
            val pending = MutableEnumMap<PlayerColor, Int>()
            for (color in seats) bits(DISCARD_WIDTH).let { if (it > 0) pending[color] = it }
            require(pending.isNotEmpty()) { "A discard phase with nobody discarding" }
            Phase.Discard(pending.asReadOnly())
        }
        PhaseTag.MOVE_ROBBER -> Phase.MoveRobber(readResumablePhase(seats))
        PhaseTag.MAIN -> Phase.Main
        PhaseTag.ROAD_BUILDING -> {
            val remaining = bits(2)
            require(remaining in 1..2) { "Road Building with $remaining roads" }
            Phase.RoadBuilding(remaining, readResumablePhase(seats))
        }
        PhaseTag.GAME_OVER -> Phase.GameOver(code(Codes.COLOR).also { require(it in seats) })
        else -> throw CodecException("Unknown phase tag $tag")
    }

    /** Robber moves and Road Building only ever resume the pre-roll or main phase. */
    private fun BitReader.readResumablePhase(seats: List<PlayerColor>): Phase =
        readPhase(seats).also { require(it == Phase.PreRoll || it == Phase.Main) { "Can't resume into $it" } }

    private fun BitWriter.writeBoard(robber: Hex, roads: Map<Edge, PlayerColor>, buildings: Map<Vertex, Building>) {
        code(robber, Codes.HEX)
        for (edge in Edge.entries) optional(roads[edge], Codes.COLOR)
        for (vertex in Vertex.entries) optional(buildings[vertex], Codes.BUILDING)
    }

    private fun BitReader.readBoard(seats: List<PlayerColor>, board: GameBoard) {
        board.robber = code(Codes.HEX)
        for (edge in Edge.entries) {
            val color = optional(Codes.COLOR) ?: continue
            require(color in seats) { "Road for $color, who isn't playing" }
            board.placeRoad(edge, color)
        }
        for (vertex in Vertex.entries) {
            val building = optional(Codes.BUILDING) ?: continue
            require(building.color in seats) { "Building for ${building.color}, who isn't playing" }
            board.placeBuilding(vertex, building)
        }
    }

    private fun BitWriter.writeOffers(offers: List<TradeOffer>) {
        varint(offers.size)
        for (offer in offers) {
            varint(offer.id)
            code(offer.from, Codes.COLOR)
            code(offer.to, Codes.COLOR)
            counts(offer.give)
            counts(offer.get)
        }
    }

    // an offer is at least an id byte, two colors and two hands of counts: 8 + 4 + 50 bits
    private fun BitReader.readOffers(seats: List<PlayerColor>): List<TradeOffer> = List(count(minBitsEach = 62)) {
        TradeOffer(varint(), code(Codes.COLOR), code(Codes.COLOR), resourceCounts(), resourceCounts())
            .also { require(it.from in seats && it.to in seats) { "Offer between players not in the game" } }
    }
}
