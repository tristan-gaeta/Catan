package com.tgaetaprojects.settledown.game.codec

import com.tgaetaprojects.settledown.game.BoardLayout
import com.tgaetaprojects.settledown.game.PlayerColor.BLUE
import com.tgaetaprojects.settledown.game.PlayerColor.RED
import com.tgaetaprojects.settledown.game.PlayerColor.WHITE
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.Game
import com.tgaetaprojects.settledown.game.engine.GameEvent
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.RandomBots
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GameRecordTest {

    private fun recordGame(seed: Long): Pair<GameRecorder, List<GameEvent>> {
        val recorder =
            GameRecorder(RandomBots.colorsFor(seed), boardSeed = seed * 31, gameSeed = seed * 17)
        val events = mutableListOf<GameEvent>()
        RandomBots.play(
            recorder.game,
            seed,
            apply = recorder::apply
        ) { _, _, produced -> events += produced }
        return recorder to events
    }

    @Test
    fun recordsReplayWholeGamesExactly() {
        var totalBytes = 0
        var totalActions = 0
        for (seed in 1L..20L) {
            val (recorder, events) = recordGame(seed)
            val bytes = recorder.record().encode()
            totalBytes += bytes.size
            totalActions += recorder.record().actions.size

            val replayedEvents = mutableListOf<GameEvent>()
            val replayed =
                GameRecord.decode(bytes).replay { _, _, produced -> replayedEvents += produced }

            assertTrue(recorder.game.phase is Phase.GameOver, "seed $seed should finish")
            assertEquals(events, replayedEvents, "seed $seed events")
            assertContentEquals(
                SnapshotCodec.encode(recorder.game),
                SnapshotCodec.encode(replayed),
                "seed $seed final state"
            )
        }
        val bytesPerAction = totalBytes.toDouble() / totalActions
        assertTrue(bytesPerAction <= 2.5, "records average $bytesPerAction bytes per action")
    }

    @Test
    fun resume_partWayThrough_matchesAnUninterruptedGame() {
        for (seed in 1L..6L) {
            val (whole, _) = recordGame(seed)
            val allActions = whole.record().actions
            val splitAt = allActions.size / 2

            val first = GameRecorder(RandomBots.colorsFor(seed), boardSeed = seed * 31, gameSeed = seed * 17)
            for (recorded in allActions.take(splitAt)) first.apply(recorded.actor, recorded.action)

            val resumed = GameRecorder.resume(GameRecord.decode(first.record().encode()))
            assertEquals(splitAt, resumed.actionCount)
            for (recorded in allActions.drop(splitAt)) resumed.apply(recorded.actor, recorded.action)

            assertEquals(allActions, resumed.record().actions, "seed $seed actions")
            assertContentEquals(
                SnapshotCodec.encode(whole.game),
                SnapshotCodec.encode(resumed.game),
                "seed $seed final state"
            )
        }
    }

    @Test
    fun actionCount_refusedAction_doesNotCount() {
        val recorder = GameRecorder(listOf(RED, WHITE, BLUE), 1L, 2L)
        recorder.apply(WHITE, Action.RollDice) // it's RED's roll
        assertEquals(0, recorder.actionCount)
        recorder.apply(RED, Action.RollDice)
        assertEquals(1, recorder.actionCount)
    }

    @Test
    fun theHeaderRoundTrips() {
        val record = recordGame(3).first.record()
        val decoded = GameRecord.decode(record.encode())
        assertEquals(record.rulesVersion, decoded.rulesVersion)
        assertEquals(record.seats, decoded.seats)
        assertEquals(record.boardSeed, decoded.boardSeed)
        assertEquals(record.gameSeed, decoded.gameSeed)
        assertEquals(record.layout, decoded.layout)
        assertEquals(record.actions, decoded.actions)
    }

    @Test
    fun refusedActionsAreNotRecorded() {
        val recorder = GameRecorder(listOf(RED, WHITE, BLUE), 1, 2)
        recorder.apply(WHITE, Action.RollDice)
        recorder.apply(RED, Action.EndTurn)
        assertTrue(recorder.record().actions.isEmpty())

        recorder.apply(RED, Action.RollDice)
        assertEquals(listOf(RecordedAction(RED, Action.RollDice)), recorder.record().actions)
    }

    @Test
    fun replayRefusesRecordsItCannotReproduce() {
        val record = recordGame(1).first.record()

        val otherRules = GameRecord(
            Game.RULES_VERSION + 1,
            record.seats,
            record.boardSeed,
            record.gameSeed,
            record.layout,
            record.actions
        )
        assertFailsWith<IllegalStateException> { otherRules.replay() }

        val otherBoard = GameRecord(
            record.rulesVersion,
            record.seats,
            record.boardSeed,
            record.gameSeed,
            BoardLayout.generate(12345),
            record.actions
        )
        assertFailsWith<IllegalStateException> { otherBoard.replay() }

        val otherDice = GameRecord(
            record.rulesVersion,
            record.seats,
            record.boardSeed,
            record.gameSeed + 1,
            record.layout,
            record.actions
        )
        assertFailsWith<IllegalStateException> { otherDice.replay() }
    }

    @Test
    fun corruptRecordsAreRejected() {
        val bytes = recordGame(2).first.record().encode()
        assertFailsWith<CodecException> { GameRecord.decode(bytes.copyOf(bytes.size / 2)) }
        val otherVersion = bytes.copyOf().also { it[0] = (FORMAT_VERSION + 1).toByte() }
        assertFailsWith<CodecException> { GameRecord.decode(otherVersion) }
        assertFailsWith<CodecException> {
            GameRecord.decode(
                SnapshotCodec.encode(
                    Game(
                        listOf(
                            RED,
                            WHITE,
                            BLUE
                        ), 1, 2
                    )
                )
            )
        }
    }
}
