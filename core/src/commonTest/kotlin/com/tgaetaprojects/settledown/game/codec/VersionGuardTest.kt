package com.tgaetaprojects.settledown.game.codec

import com.tgaetaprojects.settledown.game.BoardLayout
import com.tgaetaprojects.settledown.game.Building
import com.tgaetaprojects.settledown.game.DevelopmentCardType
import com.tgaetaprojects.settledown.game.DiceTotal
import com.tgaetaprojects.settledown.game.Edge
import com.tgaetaprojects.settledown.game.FramePiece
import com.tgaetaprojects.settledown.game.GameBoard
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.PlayerColor.BLUE
import com.tgaetaprojects.settledown.game.PlayerColor.ORANGE
import com.tgaetaprojects.settledown.game.PlayerColor.RED
import com.tgaetaprojects.settledown.game.PlayerColor.WHITE
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.Terrain
import com.tgaetaprojects.settledown.game.Vertex
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.DevCardCounts
import com.tgaetaprojects.settledown.game.engine.Game
import com.tgaetaprojects.settledown.game.engine.GameEvent
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.RandomBots
import com.tgaetaprojects.settledown.game.engine.ResourceCounts
import com.tgaetaprojects.settledown.game.engine.ResourceCounts.Companion.of
import com.tgaetaprojects.settledown.game.engine.RuleViolation
import com.tgaetaprojects.settledown.game.engine.TradeOffer
import com.tgaetaprojects.settledown.game.engine.viewFor
import com.tgaetaprojects.settledown.game.random.GameRandom
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
 * Both fingerprints are built from numbers (ordinals, coordinates, counts), never from names or `toString`, so
 * renaming an enum constant doesn't trip them. Reordering one does, and rightly: ordinals are what the codecs write,
 * and the engine iterates enums in order (e.g. which card a steal takes), so a reorder changes the format and can
 * change the rules.
 */
class VersionGuardTest {

    @Test
    fun rulesBehaviorIsPinnedToTheRulesVersion() {
        checkPinned(
            "rules",
            "Game.RULES_VERSION",
            Game.RULES_VERSION,
            RULES_FINGERPRINTS,
            rulesFingerprint()
        )
    }

    @Test
    fun encodingsArePinnedToTheFormatVersion() {
        checkPinned(
            "format",
            "FORMAT_VERSION",
            FORMAT_VERSION,
            FORMAT_FINGERPRINTS,
            formatFingerprint()
        )
    }

    @Test
    fun blobsFromAnotherVersionAreRefused() {
        val game = fixedGame(Phase.Main)
        val snapshot = SnapshotCodec.encode(game)
        assertFailsWith<CodecException> {
            SnapshotCodec.decode(
                snapshot.copyOf().also { it[0] = (FORMAT_VERSION + 1).toByte() })
        }
        assertFailsWith<CodecException> {
            SnapshotCodec.decode(
                snapshot.copyOf().also { it[2] = (Game.RULES_VERSION + 1).toByte() })
        }
        val view = SnapshotCodec.encodeView(game.viewFor(RED))
        assertFailsWith<CodecException> {
            SnapshotCodec.decodeView(
                view.copyOf().also { it[2] = (Game.RULES_VERSION + 1).toByte() })
        }
    }

    private fun checkPinned(
        what: String,
        constant: String,
        version: Int,
        pinned: Map<Int, Int>,
        actual: Int
    ) {
        val expected = pinned[version]
            ?: fail("No $what fingerprint for $constant = $version; if this is a new version, add $version to $actual")
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
        for (piece in layout.frame) mix(piece.ordinal)
        for (hex in Hex.entries) {
            mix(layout.terrain.getValue(hex).ordinal)
            mix(layout.numbers.getValue(hex).value)
        }
        // harbors by position along the coast: 0 for none, 1 for 3:1, 2 + resource ordinal for 2:1
        for (edge in BoardLayout.coastline) {
            val port = layout.ports[edge]
            mix(
                when {
                    port == null -> 0
                    port.exchangeRate == 3 -> 1
                    else -> 2 + port.accepted.single().ordinal
                }
            )
        }
    }

    private fun Fingerprint.mixState(game: Game) {
        mixPhase(game.phase, game)
        mix(game.currentPlayer.ordinal)
        for (color in game.turnOrder) mix(color.ordinal)
        mix(game.turn)
        mix(game.lastRoll?.value ?: 0)
        mix(game.board.robber.ordinal)
        for (edge in Edge.entries) mix(game.board.roads[edge]?.let { it.ordinal + 1 } ?: 0)
        for (vertex in Vertex.entries) mix(game.board.buildings[vertex]?.let { it.ordinal + 1 }
            ?: 0)
        for (color in game.seatOrder) {
            val player = game.player(color)
            mixCounts(player.hand)
            mixCounts(player.devCards)
            mixCounts(player.boughtThisTurn)
            mix(player.knightsPlayed)
        }
        for (card in game.devDeck) mix(card.ordinal)
        mix(game.longestRoadHolder?.let { it.ordinal + 1 } ?: 0)
        mix(game.largestArmyHolder?.let { it.ordinal + 1 } ?: 0)
        for (offer in game.tradeOffers) {
            mix(offer.id); mix(offer.from.ordinal); mix(offer.to.ordinal)
            mixCounts(offer.give); mixCounts(offer.get)
        }
    }

    private fun Fingerprint.mixPhase(phase: Phase, game: Game) {
        when (phase) {
            is Phase.RollForFirstPlayer -> {
                mix(1)
                for (color in phase.contenders) mix(
                    color.ordinal * 16 + (phase.rolls[color]?.value ?: 0)
                )
            }

            is Phase.SetupSettlement -> {
                mix(2); mix(phase.round)
            }

            is Phase.SetupRoad -> {
                mix(3); mix(phase.round); mix(phase.settlement.ordinal)
            }

            Phase.PreRoll -> mix(4)
            is Phase.Discard -> {
                mix(5); for (color in game.seatOrder) mix(phase.pending[color] ?: 0)
            }

            is Phase.MoveRobber -> {
                mix(6); mixPhase(phase.returnTo, game)
            }

            Phase.Main -> mix(7)
            is Phase.RoadBuilding -> {
                mix(8); mix(phase.remaining); mixPhase(phase.returnTo, game)
            }

            is Phase.GameOver -> {
                mix(9); mix(phase.winner.ordinal)
            }
        }
    }

    private fun Fingerprint.mixCounts(counts: ResourceCounts) {
        for (resource in Resource.entries) mix(counts[resource])
    }

    private fun Fingerprint.mixCounts(counts: DevCardCounts) {
        for (type in DevelopmentCardType.entries) mix(counts[type])
    }

    // ---- Format ----

    /** Every kind of blob, with a snapshot and a view in every phase, built from fixed inputs. */
    private fun formatFingerprint(): Int {
        val blobs = mutableListOf(
            LayoutCodec.encode(fixedLayout()),
            GameRecord(
                1,
                listOf(RED, WHITE, BLUE),
                3L,
                4L,
                fixedLayout(),
                sampleActions().map { RecordedAction(RED, it) }).encode(),
        )
        blobs += sampleActions().map { ActionCodec.encode(ORANGE, it) }
        blobs += sampleActions().map { WireCodec.encode(ClientAction(5, it)) }
        blobs += EventCodec.encode(sampleEvents())
        blobs += RuleViolation.entries.map { WireCodec.encode(ServerMessage.Refused(6, it)) }
        blobs += WireCodec.encode(ServerMessage.Presence(setOf(RED, ORANGE)))
        blobs += WireCodec.encode(ServerMessage.Deadline(setOf(WHITE, BLUE), 30_000))
        blobs += updateAfterItsView()
        for (phase in samplePhases()) {
            val game = fixedGame(phase)
            val snapshot = SnapshotCodec.encode(game)
            // the decoder must accept every sample, or the sample isn't a state the engine can be in
            assertContentEquals(
                snapshot,
                SnapshotCodec.encode(SnapshotCodec.decode(snapshot)),
                "$phase"
            )
            blobs += withoutRulesVersion(snapshot)
            blobs += withoutRulesVersion(SnapshotCodec.encodeView(game.viewFor(WHITE)))
        }
        // appending a constant only changes bytes once it widens the enum, so pin every size directly
        val sizes = SERIALIZED_ENUMS.map { it.size }
        return blobs.fold(sizes.hashCode()) { hash, bytes -> hash * 31 + bytes.contentHashCode() }
    }

    @Test
    fun theFormatSamplesUseEveryConstantOfEverySerializedEnum() {
        // with enums written by ordinal, a reorder only shows in the bytes if the samples use the constants it moves
        val game = fixedGame(Phase.Main)
        assertEquals(PlayerColor.entries.toSet(), game.seatOrder.toSet())
        assertEquals(Building.entries.toSet(), game.board.buildings.values.toSet())
        assertEquals(DevelopmentCardType.entries.toSet(), game.devDeck.toSet())
        assertEquals(
            Resource.entries.size,
            Resource.entries.map { game.player(RED).hand[it] }.toSet().size,
            "distinct counts per resource"
        )
        assertEquals(Terrain.entries.toSet(), fixedLayout().terrain.values.toSet())
        assertEquals(DiceTotal.entries.toSet(), fixedLayout().numbers.values.toSet())
        assertEquals(FramePiece.entries.toSet(), fixedLayout().frame.toSet())
        // one sample of each of the 9 phase types, 18 action types and 22 event types, so every tag's ordinal is written
        assertEquals(9, samplePhases().map { it::class }.toSet().size)
        assertEquals(18, sampleActions().map { it::class }.toSet().size)
        assertEquals(22, sampleEvents().map { it::class }.toSet().size)
    }

    /**
     * What an update adds after its view. The view itself is pinned as its own blob, and it holds the rules version,
     * so pinning the whole update would make a rules bump alone trip the format guard.
     */
    private fun updateAfterItsView(): ByteArray {
        val view = fixedGame(Phase.Main).viewFor(WHITE)
        val update = WireCodec.encode(ServerMessage.Update(view, sampleEvents(), 8))
        return update.copyOfRange(SnapshotCodec.encodeView(view).size, update.size)
    }

    private fun sampleEvents(): List<GameEvent> {
        val offer = TradeOffer(2, BLUE, RED, of(Resource.BRICK, 1), of(Resource.ORE, 1))
        return listOf(
            GameEvent.TurnOrderDecided(listOf(WHITE, BLUE, ORANGE, RED)),
            GameEvent.TurnStarted(BLUE, 7),
            GameEvent.DiceRolled(RED, DiceTotal.EIGHT),
            GameEvent.ResourcesProduced(mapOf(RED to of(Resource.ORE, 2), ORANGE to of(Resource.WOOL, 1))),
            GameEvent.StartingResources(WHITE, of(Resource.BRICK to 1, Resource.GRAIN to 2)),
            GameEvent.Discarded(BLUE, of(Resource.LUMBER, 4)),
            GameEvent.RobberMoved(RED, Hex.K),
            GameEvent.Stole(RED, WHITE, Resource.ORE),
            GameEvent.Stole(RED, WHITE, null, tookCard = true),
            GameEvent.RoadBuilt(RED, Edge.E30, free = true),
            GameEvent.SettlementBuilt(WHITE, Vertex.V20, free = false),
            GameEvent.CityBuilt(BLUE, Vertex.V22),
            GameEvent.DevCardBought(ORANGE, DevelopmentCardType.KNIGHT),
            GameEvent.DevCardBought(ORANGE, null),
            GameEvent.DevCardPlayed(RED, DevelopmentCardType.MONOPOLY),
            GameEvent.YearOfPlenty(RED, of(Resource.ORE, Resource.BRICK)),
            GameEvent.Monopolized(RED, Resource.WOOL, mapOf(WHITE to 3)),
            GameEvent.MaritimeTraded(WHITE, of(Resource.WOOL, 4), of(Resource.ORE, 1)),
            GameEvent.TradeProposed(offer),
            GameEvent.TradeAccepted(offer),
            GameEvent.TradeCancelled(offer, RED),
            GameEvent.LongestRoadChanged(RED, 5),
            GameEvent.LargestArmyChanged(BLUE, 3),
            GameEvent.GameWon(ORANGE, 10),
        )
    }

    /** Snapshots and views embed the rules version at byte 2; drop it so a rules bump alone doesn't trip this. */
    private fun withoutRulesVersion(bytes: ByteArray): ByteArray {
        check(bytes[2].toInt() == Game.RULES_VERSION)
        return bytes.copyOfRange(0, 2) + bytes.copyOfRange(3, bytes.size)
    }

    private fun fixedLayout(): BoardLayout {
        val tiles = listOf(
            Terrain.HILL,
            Terrain.FOREST,
            Terrain.PASTURE,
            Terrain.FIELDS,
            Terrain.MOUNTAIN,
            Terrain.HILL,
            Terrain.FOREST,
            Terrain.PASTURE,
            Terrain.FIELDS,
            Terrain.DESERT,
            Terrain.MOUNTAIN,
            Terrain.HILL,
            Terrain.FOREST,
            Terrain.PASTURE,
            Terrain.FIELDS,
            Terrain.MOUNTAIN,
            Terrain.FOREST,
            Terrain.PASTURE,
            Terrain.FIELDS,
        )
        val numbers = listOf(
            5,
            2,
            6,
            3,
            8,
            10,
            9,
            12,
            11,
            7,
            4,
            8,
            10,
            9,
            4,
            5,
            6,
            3,
            11
        ).map { DiceTotal.fromInt(it) }
        val hexes = Hex.entries.sortedWith(compareBy({ it.r }, { it.q }))
        return BoardLayout.of(
            listOf(
                FramePiece.GENERIC,
                FramePiece.WOOL,
                FramePiece.LUMBER,
                FramePiece.BRICK,
                FramePiece.ORE,
                FramePiece.GRAIN
            ),
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
        Action.ProposeTrade(
            WHITE,
            of(Resource.ORE, 1),
            of(Resource.BRICK to 1, Resource.GRAIN to 1)
        ),
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
        // all four colors, every building, every card type and distinct counts per resource, so reordering any of
        // those enums changes these bytes (see theFormatSamplesUseEveryConstantOfEverySerializedEnum)
        val game = Game(
            listOf(RED, WHITE, BLUE, ORANGE),
            GameBoard(fixedLayout()),
            GameRandom.fromState(intArrayOf(1, 2, 3, 4)),
            shuffleDeck = false
        )
        game.devDeck.addAll(
            listOf(
                DevelopmentCardType.KNIGHT,
                DevelopmentCardType.MONOPOLY,
                DevelopmentCardType.VICTORY_POINT,
                DevelopmentCardType.YEAR_OF_PLENTY,
                DevelopmentCardType.ROAD_BUILDING,
            )
        )
        game.board.placeBuilding(Vertex.V20, Building.city(RED))
        game.board.placeBuilding(Vertex.V2, Building.settlement(RED))
        game.board.placeBuilding(Vertex.V40, Building.settlement(WHITE))
        game.board.placeBuilding(Vertex.V45, Building.city(WHITE))
        game.board.placeBuilding(Vertex.V12, Building.settlement(BLUE))
        game.board.placeBuilding(Vertex.V25, Building.city(BLUE))
        game.board.placeBuilding(Vertex.V30, Building.settlement(ORANGE))
        game.board.placeBuilding(Vertex.V50, Building.city(ORANGE))
        game.board.placeRoad(Edge.E30, RED)
        game.board.placeRoad(Edge.E50, WHITE)
        game.board.robber = Hex.K
        game.player(RED).hand = of(
            Resource.BRICK to 1,
            Resource.LUMBER to 2,
            Resource.ORE to 3,
            Resource.GRAIN to 4,
            Resource.WOOL to 5
        )
        game.player(WHITE).devCards = DevCardCounts.EMPTY + DevelopmentCardType.KNIGHT
        game.player(BLUE).knightsPlayed = 2
        game.phase = phase
        when (phase) {
            is Phase.RollForFirstPlayer -> game.currentPlayer = WHITE
            is Phase.SetupSettlement -> {
                // WHITE, BLUE, ORANGE, RED: the 6th placement is ORANGE's second settlement
                game.setFirstPlayer(WHITE)
                game.setupStep = 5
                game.currentPlayer = ORANGE
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
                if (phase is Phase.Discard) game.player(RED).hand =
                    of(Resource.ORE to 5, Resource.WOOL to 4)
            }
        }
        return game
    }

    private companion object {
        /** Test games for the rules fingerprint: 3 players, 4 players, and one whose first roll is tied. */
        val GUARD_GAME_SEEDS = listOf(1L, 2L, 6L)

        /** Every enum the codecs write by ordinal. */
        val SERIALIZED_ENUMS = listOf(
            BlobKind.entries,
            PlayerColor.entries,
            Resource.entries,
            DevelopmentCardType.entries,
            DiceTotal.entries,
            Terrain.entries,
            FramePiece.entries,
            Building.entries,
            Hex.entries,
            Vertex.entries,
            Edge.entries,
            RuleViolation.entries,
            ActionCodec.Tag.entries,
            SnapshotCodec.PhaseTag.entries,
            EventCodec.Tag.entries,
            WireCodec.Tag.entries,
        )

        /** Rules fingerprints by `Game.RULES_VERSION`. */
        val RULES_FINGERPRINTS = mapOf(1 to 1179605149, 2 to 564103760, 3 to 564103760)

        /** Format fingerprints by `FORMAT_VERSION`. */
        val FORMAT_FINGERPRINTS = mapOf(1 to -512077608, 2 to -1919847427, 3 to 421339757)
    }
}
