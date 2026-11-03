package com.tgaetaprojects.settledown.game.codec

import com.tgaetaprojects.settledown.game.BoardLayout
import com.tgaetaprojects.settledown.game.DevelopmentCardType.KNIGHT
import com.tgaetaprojects.settledown.game.DevelopmentCardType.MONOPOLY
import com.tgaetaprojects.settledown.game.PlayerColor.BLUE
import com.tgaetaprojects.settledown.game.PlayerColor.RED
import com.tgaetaprojects.settledown.game.PlayerColor.WHITE
import com.tgaetaprojects.settledown.game.Resource.BRICK
import com.tgaetaprojects.settledown.game.Resource.ORE
import com.tgaetaprojects.settledown.game.Resource.WOOL
import com.tgaetaprojects.settledown.game.engine.ActionResult
import com.tgaetaprojects.settledown.game.engine.Chance
import com.tgaetaprojects.settledown.game.engine.Game
import com.tgaetaprojects.settledown.game.engine.GameEvent
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.RandomBots
import com.tgaetaprojects.settledown.game.engine.ResourceCounts
import com.tgaetaprojects.settledown.game.engine.ResourceCounts.Companion.of
import com.tgaetaprojects.settledown.game.engine.SeededChance
import com.tgaetaprojects.settledown.game.engine.accepted
import com.tgaetaprojects.settledown.game.engine.give
import com.tgaetaprojects.settledown.game.engine.giveDevCard
import com.tgaetaprojects.settledown.game.engine.newGame
import com.tgaetaprojects.settledown.game.engine.startTurnFor
import com.tgaetaprojects.settledown.game.engine.viewFor
import com.tgaetaprojects.settledown.game.random.GameRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotAndViewTest {

    // ---- Full snapshots ----

    @Test
    fun snapshotsRoundTripAtEveryStepOfRandomGames() {
        var largest = 0
        for (seed in 1L..8L) {
            val game = RandomBots.game(seed)
            RandomBots.play(game, seed) { _, _, _ ->
                val bytes = SnapshotCodec.encode(game)
                largest = maxOf(largest, bytes.size)
                val decoded = SnapshotCodec.decode(bytes)

                assertContentEquals(bytes, SnapshotCodec.encode(decoded), "seed $seed re-encoding")
                assertEquals(game.bank, decoded.bank)
                for (player in game.players) {
                    val other = decoded.player(player.color)
                    assertEquals(player.toString(), other.toString())
                    assertEquals(
                        listOf(
                            player.settlementsLeft,
                            player.citiesLeft,
                            player.roadsLeft,
                            player.longestRoad
                        ),
                        listOf(
                            other.settlementsLeft,
                            other.citiesLeft,
                            other.roadsLeft,
                            other.longestRoad
                        )
                    )
                }
            }
        }
        assertTrue(largest <= 200, "largest snapshot was $largest bytes")
    }

    @Test
    fun aRestoredGameContinuesExactlyLikeTheOriginal() {
        val seed = 5L
        val recorder = GameRecorder(RandomBots.colorsFor(seed), 11, 12)
        RandomBots.play(recorder.game, seed, maxActions = MID_GAME, apply = recorder::apply)
        val midGame = SnapshotCodec.encode(recorder.game)
        RandomBots.play(recorder.game, seed + 1, apply = recorder::apply)

        // the restored game, given the same later actions, must draw the same dice and steals
        val restored = SnapshotCodec.decode(midGame)
        for ((actor, action) in recorder.record().actions.drop(MID_GAME)) restored.apply(
            actor,
            action
        ).accepted()
        assertContentEquals(SnapshotCodec.encode(recorder.game), SnapshotCodec.encode(restored))
    }

    @Test
    fun snapshotsRejectBadInput() {
        val bytes = SnapshotCodec.encode(newGame())
        assertFailsWith<CodecException> { SnapshotCodec.decode(bytes.copyOf(bytes.size - 3)) }
        assertFailsWith<CodecException> { SnapshotCodec.decode(bytes.copyOf().also { it[2] = 99 }) }
        assertFailsWith<CodecException> { SnapshotCodec.decodeView(bytes) }

        // a game whose dice come from a bot can't be snapshot
        val scripted = newGame().copy(object : Chance {
            override fun roll() = error("unused")
            override fun stealFrom(hand: ResourceCounts) = error("unused")
        })
        assertFailsWith<IllegalStateException> { SnapshotCodec.encode(scripted) }
    }

    @Test
    fun snapshotsInPhasesTheEngineCanNeverReachAreRejected() {
        // a roll-off whose current player isn't the next to roll
        val rollOff = RandomBots.game(1).apply { currentPlayer = seatOrder.last() }
        assertFailsWith<CodecException> { SnapshotCodec.decode(SnapshotCodec.encode(rollOff)) }

        // a discard owed from a hand that can't cover it
        val discard = newGame().apply { startTurnFor(RED, Phase.Discard(mapOf(WHITE to 40))) }
        assertFailsWith<CodecException> { SnapshotCodec.decode(SnapshotCodec.encode(discard)) }

        // set-up out of snake order
        val setup = newGame().apply { currentPlayer = BLUE }
        assertFailsWith<CodecException> { SnapshotCodec.decode(SnapshotCodec.encode(setup)) }
    }

    @Test
    fun hugeClaimedCountsFailFastInsteadOfAllocating() {
        val bytes = BitWriter().apply { varint(Int.MAX_VALUE) }.toByteArray()
        assertFailsWith<CodecException> { BitReader(bytes).count(minBitsEach = 7) }

        val record = BitWriter().apply {
            header(BlobKind.RECORD)
            varint(Game.RULES_VERSION)
            bits(0, 1)
            for (color in listOf(RED, WHITE, BLUE)) code(color)
            long(1)
            long(2)
            with(LayoutCodec) { writeLayout(BoardLayout.generate(1)) }
            varint(Int.MAX_VALUE)
        }.toByteArray()
        assertFailsWith<CodecException> { GameRecord.decode(record) }
    }

    // ---- Copies ----

    @Test
    fun aCopyCannotDrawFromTheRealGamesGenerator() {
        val game = RandomBots.game(3)
        assertFailsWith<IllegalArgumentException> { game.copy(game.chance) }
    }

    /** Everything about a game except its generator: every seat's view, plus the deck order. */
    private fun Game.everything(): List<Any> =
        seatOrder.map { viewFor(it) } + listOf(devDeck.toList())

    @Test
    fun copiesAreIndependentAndContinueIdentically() {
        val game = RandomBots.game(4)
        RandomBots.play(game, 4, maxActions = MID_GAME)
        val before = game.everything()

        val copy = game.copy(SeededChance(GameRandom(1)))
        assertEquals(before, copy.everything())
        RandomBots.play(copy, 99)
        assertEquals(before, game.everything(), "playing the copy must not touch the original")

        val a = game.copy(SeededChance(GameRandom(5)))
        val b = game.copy(SeededChance(GameRandom(5)))
        RandomBots.play(a, 7)
        RandomBots.play(b, 7)
        assertEquals(a.everything(), b.everything())
    }

    @Test
    fun copiesCannotBeSnapshotAsIfTheyWereTheRealGame() {
        val copy = RandomBots.game(1).copy(SeededChance(GameRandom(1)))
        assertFailsWith<IllegalStateException> { SnapshotCodec.encode(copy) }
    }

    // ---- Player views ----

    @Test
    fun viewsRoundTripAtEveryStep() {
        val game = RandomBots.game(2)
        var largest = 0
        RandomBots.play(game, 2) { _, _, _ ->
            for (color in game.seatOrder) {
                val view = game.viewFor(color)
                val bytes = SnapshotCodec.encodeView(view)
                largest = maxOf(largest, bytes.size)
                assertEquals(view, SnapshotCodec.decodeView(bytes))
            }
        }
        assertTrue(largest <= 200, "largest view was $largest bytes")
    }

    @Test
    fun viewsHideOpponentsCardsTheDeckAndTheGenerator() {
        // same board, different game seeds: different deck order and dice, identical public state
        val one = Game(listOf(RED, WHITE, BLUE), boardSeed = 1, gameSeed = 2)
        val two = Game(listOf(RED, WHITE, BLUE), boardSeed = 1, gameSeed = 3)
        assertContentEquals(
            SnapshotCodec.encodeView(one.viewFor(RED)),
            SnapshotCodec.encodeView(two.viewFor(RED))
        )

        // opponents swap which resources they hold; bank and totals are unchanged
        one.give(WHITE, of(ORE, 2)); one.give(BLUE, of(WOOL, 2))
        two.give(WHITE, of(WOOL, 2)); two.give(BLUE, of(ORE, 2))
        one.giveDevCard(WHITE, KNIGHT); two.giveDevCard(WHITE, MONOPOLY)
        // the decks now differ in which card is missing, which is also hidden
        assertContentEquals(
            SnapshotCodec.encodeView(one.viewFor(RED)),
            SnapshotCodec.encodeView(two.viewFor(RED))
        )

        // but WHITE sees their own cards
        assertFalse(
            SnapshotCodec.encodeView(one.viewFor(WHITE))
                .contentEquals(SnapshotCodec.encodeView(two.viewFor(WHITE)))
        )
        assertEquals(of(ORE, 2), one.viewFor(WHITE).hand)
        assertEquals(2, one.viewFor(RED).summary(WHITE).handSize)
    }

    @Test
    fun privateEventsAreRedactedForOtherPlayers() {
        val stole = GameEvent.Stole(RED, WHITE, ORE)
        assertEquals(stole, stole.visibleTo(RED))
        assertEquals(stole, stole.visibleTo(WHITE))
        assertEquals(GameEvent.Stole(RED, WHITE, null, tookCard = true), stole.visibleTo(BLUE))

        val bought = GameEvent.DevCardBought(RED, KNIGHT)
        assertEquals(bought, bought.visibleTo(RED))
        assertEquals(GameEvent.DevCardBought(RED, null), bought.visibleTo(WHITE))

        val produced = GameEvent.ResourcesProduced(mapOf(RED to of(ORE, 1)))
        assertEquals(produced, produced.visibleTo(BLUE))

        val accepted = ActionResult.Accepted(listOf(stole, bought, produced))
        assertEquals(
            listOf(stole.visibleTo(BLUE), bought.visibleTo(BLUE), produced),
            accepted.eventsFor(BLUE)
        )
        assertEquals(listOf(stole, bought, produced), accepted.eventsFor(RED))
    }

    @Test
    fun publicView_privateEvents_hideDetailsFromEveryone() {
        assertEquals(GameEvent.Stole(RED, WHITE, null, tookCard = true), GameEvent.Stole(RED, WHITE, ORE).publicView())
        assertEquals(GameEvent.DevCardBought(RED, null), GameEvent.DevCardBought(RED, KNIGHT).publicView())
        val produced = GameEvent.ResourcesProduced(mapOf(RED to of(ORE, 1)))
        assertEquals(produced, produced.publicView())
    }

    @Test
    fun aViewCanBeTakenInAnyPhase() {
        val game = newGame()
        game.startTurnFor(RED, Phase.MoveRobber(Phase.PreRoll))
        game.give(WHITE, of(BRICK, 9))
        val view = game.viewFor(BLUE)
        assertEquals(view, SnapshotCodec.decodeView(SnapshotCodec.encodeView(view)))
        game.startTurnFor(RED, Phase.Discard(mapOf(WHITE to 4)))
        assertEquals(
            game.viewFor(RED),
            SnapshotCodec.decodeView(SnapshotCodec.encodeView(game.viewFor(RED)))
        )
        game.startTurnFor(RED, Phase.RoadBuilding(1, Phase.Main))
        assertEquals(game.phase, SnapshotCodec.decode(SnapshotCodec.encode(game)).phase)
    }

    private companion object {
        /** An action count that lands mid-game for the seeds used here. */
        const val MID_GAME = 400
    }
}
