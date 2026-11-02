package com.tgaetaprojects.biophilia.game.codec

import com.tgaetaprojects.biophilia.game.BoardLayout
import com.tgaetaprojects.biophilia.game.Building
import com.tgaetaprojects.biophilia.game.DevelopmentCardType
import com.tgaetaprojects.biophilia.game.DiceTotal
import com.tgaetaprojects.biophilia.game.Edge
import com.tgaetaprojects.biophilia.game.FramePiece
import com.tgaetaprojects.biophilia.game.GameBoard
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.PlayerColor.*
import com.tgaetaprojects.biophilia.game.Resource
import com.tgaetaprojects.biophilia.game.Terrain
import com.tgaetaprojects.biophilia.game.Vertex
import com.tgaetaprojects.biophilia.game.engine.Action
import com.tgaetaprojects.biophilia.game.engine.DevCardCounts
import com.tgaetaprojects.biophilia.game.engine.Game
import com.tgaetaprojects.biophilia.game.engine.Phase
import com.tgaetaprojects.biophilia.game.engine.RandomBots
import com.tgaetaprojects.biophilia.game.engine.ResourceCounts
import com.tgaetaprojects.biophilia.game.engine.ResourceCounts.Companion.of
import com.tgaetaprojects.biophilia.game.engine.TradeOffer
import com.tgaetaprojects.biophilia.game.engine.viewFor
import com.tgaetaprojects.biophilia.game.random.GameRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Safeguards for the two version numbers. Each version's behavior is pinned to a fingerprint:
 * - [Game.RULES_VERSION] covers board generation and how seeded games unfold. Records only replay on the same rules
 *   version.
 * - [FORMAT_VERSION] covers the exact bytes every codec writes, for hand-built inputs that don't depend on the rules.
 *
 * When a fingerprint changes, either the change was a mistake (fix it), or it was deliberate: bump the version
 * number and add the new fingerprint under the new version. Keep the old entries as a record of past versions.
 * Bumping a version without a behavior change also fails, until its fingerprint is added.
 *
 * Both fingerprints are built from numbers (wire codes, coordinates, counts), never from names or `toString`, so
 * renaming or reordering an enum doesn't trip them.
 */
class VersionGuardTest {

    @Test
    fun rulesBehaviorIsPinnedToTheRulesVersion() {
        checkPinned("rules", "Game.RULES_VERSION", Game.RULES_VERSION, RULES_FINGERPRINTS, rulesFingerprint())
    }

    @Test
    fun encodingsArePinnedToTheFormatVersion() {
        checkPinned("format", "FORMAT_VERSION", FORMAT_VERSION, FORMAT_FINGERPRINTS, formatFingerprint())
    }

    @Test
    fun everySerializedEnumHasAWireCode() {
        // loading Codes already fails if a constant has no code; this documents the full set
        assertEquals(PlayerColor.entries.toSet(), Codes.COLOR.inCodeOrder.toSet())
        assertEquals(Resource.entries.toSet(), Codes.RESOURCE.inCodeOrder.toSet())
        assertEquals(DevelopmentCardType.entries.toSet(), Codes.DEV_CARD.inCodeOrder.toSet())
        assertEquals(DiceTotal.entries.toSet(), Codes.DICE.inCodeOrder.toSet())
        assertEquals(Terrain.entries.toSet(), Codes.TERRAIN.inCodeOrder.toSet())
        assertEquals(FramePiece.entries.toSet(), Codes.FRAME.inCodeOrder.toSet())
        assertEquals(Building.entries.toSet(), Codes.BUILDING.inCodeOrder.toSet())
        assertEquals(Hex.entries.toSet(), Codes.HEX.inCodeOrder.toSet())
    }

    @Test
    fun wireCodesRejectIncompleteOrOversizedTables() {
        assertFailsWith<IllegalArgumentException> { WireCodes(2, PlayerColor.entries, RED, WHITE, BLUE) }
        assertFailsWith<IllegalArgumentException> { WireCodes(2, PlayerColor.entries, RED, WHITE, BLUE, BLUE) }
        assertFailsWith<IllegalArgumentException> { WireCodes(2, Resource.entries, *Resource.entries.toTypedArray()) }
    }

    @Test
    fun blobsFromAnotherVersionAreRefused() {
        val game = fixedGame(Phase.Main)
        val snapshot = SnapshotCodec.encode(game)
        assertFailsWith<CodecException> { SnapshotCodec.decode(snapshot.copyOf().also { it[0] = (FORMAT_VERSION + 1).toByte() }) }
        assertFailsWith<CodecException> { SnapshotCodec.decode(snapshot.copyOf().also { it[2] = (Game.RULES_VERSION + 1).toByte() }) }
        val view = SnapshotCodec.encodeView(game.viewFor(RED))
        assertFailsWith<CodecException> { SnapshotCodec.decodeView(view.copyOf().also { it[2] = (Game.RULES_VERSION + 1).toByte() }) }
    }

    private fun checkPinned(what: String, constant: String, version: Int, pinned: Map<Int, Int>, actual: Int) {
        val expected = pinned[version] ?: fail("No $what fingerprint for $constant = $version; if this is a new version, add $version to $actual")
        assertEquals(
            expected, actual,
            "The $what changed without a version bump. If the change is deliberate, bump $constant and add the new " +
                "version with fingerprint $actual; otherwise it's a regression."
        )
    }

    // ---- Rules ----

    private class Fingerprint {
        var value = 1
            private set

        fun mix(number: Int) {
            value = value * 31 + number
        }
    }

    /**
     * Layouts for 20 board seeds, then the full state after every action of a few seeded bot games. The games include
     * 3 and 4 players, and a tied roll for the first player.
     */
    private fun rulesFingerprint(): Int {
        val fingerprint = Fingerprint()
        for (seed in 1L..20L) fingerprint.mixLayout(BoardLayout.generate(seed))

        var rollOffTies = 0
        for (seed in GUARD_GAME_SEEDS) {
            val game = RandomBots.game(seed)
            fingerprint.mixLayout(game.board.layout)
            RandomBots.play(game, seed) { _, _, _ ->
                (game.phase as? Phase.RollForFirstPlayer)?.let { if (it.contenders.size < game.seatOrder.size) rollOffTies++ }
                fingerprint.mixState(game)
            }
        }
        assertTrue(rollOffTies > 0, "The guard games must include a tied roll for the first player")
        return fingerprint.value
    }

    private fun Fingerprint.mixLayout(layout: BoardLayout) {
        for (piece in layout.frame) mix(Codes.FRAME.code(piece))
        for (hex in Codes.HEX.inCodeOrder) {
            mix(Codes.TERRAIN.code(layout.terrain.getValue(hex)))
            mix(layout.numbers.getValue(hex).value)
        }
        // harbors by position along the coast: 0 for none, 1 for 3:1, 2 + resource code for 2:1
        for (edge in BoardLayout.coastline) {
            val port = layout.ports[edge]
            mix(
                when {
                    port == null -> 0
                    port.exchangeRate == 3 -> 1
                    else -> 2 + Codes.RESOURCE.code(port.accepted.single())
                }
            )
        }
    }

    private fun Fingerprint.mixState(game: Game) {
        mixPhase(game.phase, game)
        mix(Codes.COLOR.code(game.currentPlayer))
        for (color in game.turnOrder) mix(Codes.COLOR.code(color))
        mix(game.turn)
        mix(game.lastRoll?.value ?: 0)
        mix(Codes.HEX.code(game.board.robber))
        for (edge in Edge.entries) mix(game.board.roads[edge]?.let { Codes.COLOR.code(it) + 1 } ?: 0)
        for (vertex in Vertex.entries) mix(game.board.buildings[vertex]?.let { Codes.BUILDING.code(it) + 1 } ?: 0)
        for (color in game.seatOrder) {
            val player = game.player(color)
            mixCounts(player.hand)
            mixCounts(player.devCards)
            mixCounts(player.boughtThisTurn)
            mix(player.knightsPlayed)
        }
        for (card in game.devDeck) mix(Codes.DEV_CARD.code(card))
        mix(game.longestRoadHolder?.let { Codes.COLOR.code(it) + 1 } ?: 0)
        mix(game.largestArmyHolder?.let { Codes.COLOR.code(it) + 1 } ?: 0)
        for (offer in game.tradeOffers) {
            mix(offer.id); mix(Codes.COLOR.code(offer.from)); mix(Codes.COLOR.code(offer.to))
            mixCounts(offer.give); mixCounts(offer.get)
        }
    }

    private fun Fingerprint.mixPhase(phase: Phase, game: Game) {
        when (phase) {
            is Phase.RollForFirstPlayer -> {
                mix(1)
                for (color in phase.contenders) mix(Codes.COLOR.code(color) * 16 + (phase.rolls[color]?.value ?: 0))
            }
            is Phase.SetupSettlement -> { mix(2); mix(phase.round) }
            is Phase.SetupRoad -> { mix(3); mix(phase.round); mix(phase.settlement.ordinal) }
            Phase.PreRoll -> mix(4)
            is Phase.Discard -> { mix(5); for (color in game.seatOrder) mix(phase.pending[color] ?: 0) }
            is Phase.MoveRobber -> { mix(6); mixPhase(phase.returnTo, game) }
            Phase.Main -> mix(7)
            is Phase.RoadBuilding -> { mix(8); mix(phase.remaining); mixPhase(phase.returnTo, game) }
            is Phase.GameOver -> { mix(9); mix(Codes.COLOR.code(phase.winner)) }
        }
    }

    private fun Fingerprint.mixCounts(counts: ResourceCounts) {
        for (resource in Codes.RESOURCE.inCodeOrder) mix(counts[resource])
    }

    private fun Fingerprint.mixCounts(counts: DevCardCounts) {
        for (type in Codes.DEV_CARD.inCodeOrder) mix(counts[type])
    }

    // ---- Format ----

    /** Every kind of blob, with a snapshot and a view in every phase, built from fixed inputs. */
    private fun formatFingerprint(): Int {
        val blobs = mutableListOf(
            LayoutCodec.encode(fixedLayout()),
            GameRecord(1, listOf(RED, WHITE, BLUE), 3L, 4L, fixedLayout(), sampleActions().map { RecordedAction(RED, it) }).encode(),
        )
        blobs += sampleActions().map { ActionCodec.encode(ORANGE, it) }
        for (phase in samplePhases()) {
            val game = fixedGame(phase)
            val snapshot = SnapshotCodec.encode(game)
            // the decoder must accept every sample, or the sample isn't a state the engine can be in
            assertContentEquals(snapshot, SnapshotCodec.encode(SnapshotCodec.decode(snapshot)), "$phase")
            blobs += withoutRulesVersion(snapshot)
            blobs += withoutRulesVersion(SnapshotCodec.encodeView(game.viewFor(WHITE)))
        }
        return blobs.fold(1) { hash, bytes -> hash * 31 + bytes.contentHashCode() }
    }

    /** Snapshots and views embed the rules version at byte 2; drop it so a rules bump alone doesn't trip this. */
    private fun withoutRulesVersion(bytes: ByteArray): ByteArray {
        check(bytes[2].toInt() == Game.RULES_VERSION)
        return bytes.copyOfRange(0, 2) + bytes.copyOfRange(3, bytes.size)
    }

    private fun fixedLayout(): BoardLayout {
        val tiles = listOf(
            Terrain.HILL, Terrain.FOREST, Terrain.PASTURE, Terrain.FIELDS, Terrain.MOUNTAIN, Terrain.HILL,
            Terrain.FOREST, Terrain.PASTURE, Terrain.FIELDS, Terrain.DESERT, Terrain.MOUNTAIN, Terrain.HILL,
            Terrain.FOREST, Terrain.PASTURE, Terrain.FIELDS, Terrain.MOUNTAIN, Terrain.FOREST, Terrain.PASTURE,
            Terrain.FIELDS,
        )
        val numbers = listOf(5, 2, 6, 3, 8, 10, 9, 12, 11, 7, 4, 8, 10, 9, 4, 5, 6, 3, 11).map { DiceTotal.fromInt(it) }
        val hexes = Hex.entries.sortedWith(compareBy({ it.r }, { it.q }))
        return BoardLayout.of(
            listOf(FramePiece.GENERIC, FramePiece.WOOL, FramePiece.LUMBER, FramePiece.BRICK, FramePiece.ORE, FramePiece.GRAIN),
            hexes.zip(tiles).toMap(),
            hexes.zip(numbers).toMap(),
        )
    }

    private fun sampleActions(): List<Action> = listOf(
        Action.PlaceSettlement(Vertex.V20),
        Action.PlaceRoad(Edge.E30),
        Action.RollDice,
        Action.Discard(of(Resource.BRICK to 3, Resource.WOOL to 1)),
        Action.MoveRobber(Hex.K, BLUE),
        Action.MoveRobber(Hex.D, null),
        Action.BuildRoad(Edge.E31),
        Action.BuildSettlement(Vertex.V22),
        Action.BuildCity(Vertex.V20),
        Action.BuyDevCard,
        Action.PlayKnight,
        Action.PlayRoadBuilding,
        Action.PlayYearOfPlenty(Resource.ORE, Resource.GRAIN),
        Action.PlayMonopoly(Resource.LUMBER),
        Action.MaritimeTrade(Resource.WOOL, Resource.ORE, 2),
        Action.ProposeTrade(WHITE, of(Resource.ORE, 1), of(Resource.BRICK to 1, Resource.GRAIN to 1)),
        Action.AcceptTrade(3),
        Action.CancelTrade(4),
        Action.EndTurn,
    )

    private fun samplePhases(): List<Phase> = listOf(
        Phase.RollForFirstPlayer(listOf(RED, WHITE, BLUE), mapOf(RED to DiceTotal.EIGHT)),
        Phase.SetupSettlement(2),
        Phase.SetupRoad(1, Vertex.V10),
        Phase.PreRoll,
        Phase.Discard(mapOf(RED to 4)),
        Phase.MoveRobber(Phase.PreRoll),
        Phase.Main,
        Phase.RoadBuilding(2, Phase.Main),
        Phase.GameOver(RED),
    )

    /**
     * A position built by hand, with no shuffling, so it doesn't depend on the rules: mid-game for most phases, and
     * consistent with [phase] (set-up and the roll-off happen before turn 1, a discard is half a big hand).
     */
    private fun fixedGame(phase: Phase): Game {
        val game = Game(listOf(RED, WHITE, BLUE), GameBoard(fixedLayout()), GameRandom.fromState(intArrayOf(1, 2, 3, 4)), shuffleDeck = false)
        game.devDeck.addAll(listOf(DevelopmentCardType.KNIGHT, DevelopmentCardType.MONOPOLY, DevelopmentCardType.VICTORY_POINT))
        game.board.placeBuilding(Vertex.V20, Building.city(RED))
        game.board.placeBuilding(Vertex.V40, Building.settlement(WHITE))
        game.board.placeRoad(Edge.E30, RED)
        game.board.placeRoad(Edge.E50, WHITE)
        game.board.robber = Hex.K
        game.player(RED).hand = of(Resource.ORE to 2, Resource.WOOL to 1)
        game.player(WHITE).devCards = DevCardCounts.EMPTY + DevelopmentCardType.KNIGHT
        game.player(BLUE).knightsPlayed = 2
        game.phase = phase
        when (phase) {
            is Phase.RollForFirstPlayer -> game.currentPlayer = WHITE
            is Phase.SetupSettlement -> {
                // WHITE, BLUE, RED: the 4th placement is RED's second settlement
                game.setFirstPlayer(WHITE)
                game.setupStep = 3
                game.currentPlayer = RED
            }
            is Phase.SetupRoad -> {
                game.setFirstPlayer(WHITE)
                game.setupStep = 1
                game.currentPlayer = BLUE
                game.board.placeBuilding(Vertex.V10, Building.settlement(BLUE))
            }
            else -> {
                game.setFirstPlayer(WHITE)
                game.turn = 7
                game.setupStep = 6
                game.currentPlayer = BLUE
                game.lastRoll = DiceTotal.SEVEN
                game.offers += TradeOffer(2, BLUE, RED, of(Resource.BRICK, 1), of(Resource.ORE, 1))
                game.nextOfferId = 3
                if (phase is Phase.Discard) game.player(RED).hand = of(Resource.ORE to 5, Resource.WOOL to 4)
            }
        }
        return game
    }

    private companion object {
        /** Test games for the rules fingerprint: 3 players, 4 players, and one whose first roll is tied. */
        val GUARD_GAME_SEEDS = listOf(1L, 2L, 6L)

        /** Rules fingerprints by `Game.RULES_VERSION`. */
        val RULES_FINGERPRINTS = mapOf(1 to 1179605149)

        /** Format fingerprints by `FORMAT_VERSION`. */
        val FORMAT_FINGERPRINTS = mapOf(1 to -2092564893)
    }
}
