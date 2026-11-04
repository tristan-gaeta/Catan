package com.tgaetaprojects.settledown.games

import com.tgaetaprojects.settledown.api.GameStatus
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.PlayerColor.BLUE
import com.tgaetaprojects.settledown.game.PlayerColor.RED
import com.tgaetaprojects.settledown.game.PlayerColor.WHITE
import com.tgaetaprojects.settledown.game.codec.ClientAction
import com.tgaetaprojects.settledown.game.codec.GameRecorder
import com.tgaetaprojects.settledown.game.codec.ServerMessage
import com.tgaetaprojects.settledown.game.codec.SnapshotCodec
import com.tgaetaprojects.settledown.game.codec.WireCodec
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.ActionResult
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.RuleViolation
import com.tgaetaprojects.settledown.game.engine.viewFor
import com.tgaetaprojects.settledown.store.AccountStore
import com.tgaetaprojects.settledown.store.Database
import com.tgaetaprojects.settledown.store.GameStore
import com.tgaetaprojects.settledown.withTempDatabasePath
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A clock that only moves when a test fires it. */
private class ManualScheduler : Scheduler {
    private class Entry(val task: suspend () -> Unit, var cancelled: Boolean = false)

    private val entries = mutableListOf<Entry>()

    @Synchronized
    override fun schedule(after: Duration, task: suspend () -> Unit): () -> Unit {
        val entry = Entry(task)
        entries += entry
        return { synchronized(this) { entry.cancelled = true } }
    }

    private fun latestLive(): Entry? = synchronized(this) { entries.lastOrNull { !it.cancelled } }

    /** The pending timer's task; waits for one, since a host sets its first timer as it starts up. */
    suspend fun pendingTask(): suspend () -> Unit {
        while (true) {
            latestLive()?.let { return it.task }
            delay(5)
        }
    }

    /** Runs the pending timer, as if its time had come. */
    suspend fun fire() = pendingTask().invoke()
}

class GameHostTest {
    private val seatColors = listOf(RED, WHITE, BLUE)

    private class Fixture(val db: Database, limits: TurnLimits) {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val scheduler = ManualScheduler()
        val store = GameStore(db)
        val manager = GameManager(store, limits).also { it.attach(scope, scheduler) }

        fun restartedManager() = GameManager(store).also { it.attach(scope, ManualScheduler()) }
    }

    private fun withFixture(
        limits: TurnLimits = TurnLimits(),
        block: suspend Fixture.(gameId: Long, players: List<Long>) -> Unit,
    ) = withTempDatabasePath { path ->
        Database(path).use { db ->
            val fixture = Fixture(db, limits)
            try {
                runBlocking {
                    withTimeout(30.seconds) {
                        val accounts = AccountStore(db)
                        val players = listOf("a", "b", "c").map { accounts.findOrCreate(it).first.id }
                        val gameId = fixture.store.create(seatColors.zip(players), 11L, 22L)
                        fixture.manager.startHost(gameId)
                        fixture.block(gameId, players)
                    }
                }
            } finally {
                fixture.scope.cancel()
            }
        }
    }

    private suspend fun SeatConnection.next(): ServerMessage = WireCodec.decodeServerMessage(frames.receive())

    private suspend inline fun <reified T : ServerMessage> SeatConnection.nextOf(): T {
        while (true) {
            val message = next()
            if (message is T) return message
        }
    }

    /** A connected seat, past the view and deadline that greet every connection. */
    private suspend fun Fixture.connect(gameId: Long, seat: PlayerColor): SeatConnection {
        val connection = SeatConnection()
        assertTrue(manager.host(gameId)!!.connect(seat, connection))
        connection.nextOf<ServerMessage.Deadline>()
        return connection
    }

    /** Fires the timer and waits for the new one, which is the last thing a timeout does. */
    private suspend fun Fixture.timeOut(watcher: SeatConnection): ServerMessage.Update {
        scheduler.fire()
        val update = watcher.nextOf<ServerMessage.Update>()
        watcher.nextOf<ServerMessage.Deadline>()
        return update
    }

    private suspend fun Fixture.waitUntilGone(gameId: Long) {
        while (manager.host(gameId) != null) delay(5)
    }

    @Test
    fun connect_sendsTheViewThenTheDeadline() = withFixture { gameId, _ ->
        val connection = SeatConnection()
        manager.host(gameId)!!.connect(WHITE, connection)
        val update = assertIs<ServerMessage.Update>(connection.next())
        assertEquals(WHITE, update.view.viewer)
        assertEquals(emptyList(), update.events)
        val deadline = assertIs<ServerMessage.Deadline>(connection.next())
        assertEquals(setOf(RED), deadline.waitingOn)
        assertTrue(deadline.remainingMillis in 1..30_000)
    }

    @Test
    fun act_accepted_reachesEverySeatWithItsOwnView() = withFixture { gameId, _ ->
        val red = connect(gameId, RED)
        val white = connect(gameId, WHITE)
        manager.host(gameId)!!.act(RED, red, ClientAction(7, Action.RollDice))

        val toRed = red.nextOf<ServerMessage.Update>()
        val toWhite = white.nextOf<ServerMessage.Update>()
        assertEquals(RED, toRed.view.viewer)
        assertEquals(WHITE, toWhite.view.viewer)
        assertEquals(7, toRed.lastClientSeq)
        assertEquals(0, toWhite.lastClientSeq)
        assertEquals(1, toRed.events.size)
    }

    @Test
    fun act_refused_answersOnlyTheSender() = withFixture { gameId, _ ->
        val red = connect(gameId, RED)
        val white = connect(gameId, WHITE)
        val host = manager.host(gameId)!!

        host.act(WHITE, white, ClientAction(3, Action.RollDice))
        assertEquals(
            ServerMessage.Refused(3, RuleViolation.NOT_YOUR_TURN),
            white.nextOf<ServerMessage.Refused>()
        )

        // RED's next update is the result of RED's own roll, so nothing about the refusal reached them
        host.act(RED, red, ClientAction(1, Action.RollDice))
        while (true) {
            val message = red.next()
            assertFalse(message is ServerMessage.Refused)
            if (message is ServerMessage.Update) break
        }
    }

    @Test
    fun connect_sameSeatAgain_closesTheOldConnection() = withFixture { gameId, _ ->
        val old = connect(gameId, RED)
        val replacement = connect(gameId, RED)
        assertFailsWith<ClosedReceiveChannelException> { while (true) old.frames.receive() }
        assertEquals(WireCodec.CLOSE_REPLACED, old.closeCode)
        assertNull(replacement.closeCode)
    }

    @Test
    fun act_fromReplacedConnection_isIgnored() = withFixture { gameId, _ ->
        val old = connect(gameId, RED)
        val replacement = connect(gameId, RED)
        val host = manager.host(gameId)!!
        host.act(RED, old, ClientAction(1, Action.RollDice))
        host.act(RED, replacement, ClientAction(2, Action.RollDice))
        assertEquals(2, replacement.nextOf<ServerMessage.Update>().lastClientSeq)
        assertEquals(1, store.loadRecord(gameId).actions.size)
    }

    @Test
    fun presence_followsConnections() = withFixture { gameId, _ ->
        val red = connect(gameId, RED)
        assertEquals(setOf(RED), red.nextOf<ServerMessage.Presence>().connected)
        val white = connect(gameId, WHITE)
        assertEquals(setOf(RED, WHITE), red.nextOf<ServerMessage.Presence>().connected)
        manager.host(gameId)!!.disconnect(WHITE, white)
        assertEquals(setOf(RED), red.nextOf<ServerMessage.Presence>().connected)
    }

    @Test
    fun timeout_movesForTheSeatAndSavesIt() = withFixture { gameId, _ ->
        val white = connect(gameId, WHITE)
        scheduler.fire()
        assertEquals(1, white.nextOf<ServerMessage.Update>().events.size)
        assertEquals(setOf(WHITE), white.nextOf<ServerMessage.Deadline>().waitingOn)
        assertEquals(1, store.loadRecord(gameId).actions.size)
    }

    @Test
    fun timeout_firesAfterAMoveReplacedIt_doesNothing() = withFixture { gameId, _ ->
        val red = connect(gameId, RED)
        val stale = scheduler.pendingTask()
        manager.host(gameId)!!.act(RED, red, ClientAction(1, Action.RollDice))
        red.nextOf<ServerMessage.Deadline>()

        stale()
        // commands run in order, so by the time this connection is greeted the stale timer has been handled
        connect(gameId, WHITE)

        assertEquals(1, store.loadRecord(gameId).actions.size)
    }

    @Test
    fun timeouts_withNobodyActing_abandonTheGame() = withFixture(TurnLimits(maxUnattendedTimeouts = 3)) { gameId, _ ->
        val red = connect(gameId, RED)
        repeat(3) { timeOut(red) }

        scheduler.fire()

        waitUntilGone(gameId)
        assertEquals(GameStatus.ABANDONED, store.status(gameId))
        assertEquals(WireCodec.CLOSE_GAME_UNAVAILABLE, red.closeCode)
        assertEquals(3, store.loadRecord(gameId).actions.size)
    }

    @Test
    fun timeouts_playerActsInBetween_keepTheGameGoing() = withFixture(TurnLimits(maxUnattendedTimeouts = 2)) { gameId, _ ->
        val connections = seatColors.associateWith { connect(gameId, it) }
        val red = connections.getValue(RED)
        timeOut(red)
        val third = timeOut(red).view.currentPlayer

        // the third roll of the opening roll-off; it clears the count, so two more timeouts are allowed
        manager.host(gameId)!!.act(third, connections.getValue(third), ClientAction(1, Action.RollDice))
        red.nextOf<ServerMessage.Deadline>()
        timeOut(red)
        timeOut(red)

        assertEquals(GameStatus.ACTIVE, store.status(gameId))
        assertNotNull(manager.host(gameId))
    }

    @Test
    fun removeAccount_closesThatSeatAndKeepsPlaying() = withFixture { gameId, players ->
        val red = connect(gameId, RED)
        val white = connect(gameId, WHITE)
        val host = manager.host(gameId)!!
        assertEquals(RED, host.seatOf(players[0]))

        host.removeAccount(players[0])

        assertFailsWith<ClosedReceiveChannelException> { while (true) red.frames.receive() }
        assertEquals(WireCodec.CLOSE_GAME_UNAVAILABLE, red.closeCode)
        assertNull(host.seatOf(players[0]))
        // RED opens the roll-off, so this move is the timer playing the seat nobody holds now
        timeOut(white)
        assertEquals(RED, store.loadRecord(gameId).actions.single().actor)
    }

    @Test
    fun connect_afterAccountRemoved_isClosed() = withFixture { gameId, players ->
        val host = manager.host(gameId)!!
        host.removeAccount(players[0])
        val late = SeatConnection()
        host.connect(RED, late)
        assertFailsWith<ClosedReceiveChannelException> { late.frames.receive() }
        assertEquals(WireCodec.CLOSE_GAME_UNAVAILABLE, late.closeCode)
    }

    @Test
    fun disconnectAccount_keepsTheSeat() = withFixture { gameId, players ->
        val red = connect(gameId, RED)
        val host = manager.host(gameId)!!
        host.disconnectAccount(players[0])
        assertFailsWith<ClosedReceiveChannelException> { while (true) red.frames.receive() }
        assertEquals(CloseReason.Codes.TRY_AGAIN_LATER.code, red.closeCode)
        assertEquals(RED, host.seatOf(players[0]))
    }

    @Test
    fun saveFailure_stopsTheGameAndClosesConnections() = withFixture { gameId, _ ->
        val red = connect(gameId, RED)
        db.close()
        manager.host(gameId)!!.act(RED, red, ClientAction(1, Action.RollDice))
        waitUntilGone(gameId)
        // it resumes on restart, so clients should keep trying, and nobody heard about the move that wasn't saved
        assertEquals(CloseReason.Codes.TRY_AGAIN_LATER.code, red.closeCode)
        try {
            while (true) assertFalse(red.next() is ServerMessage.Update)
        } catch (_: ClosedReceiveChannelException) {
        }
    }

    @Test
    fun isWaitingToResume_onlyForSeatedPlayersOfActiveGames() = withFixture { gameId, players ->
        val stopped = restartedManager()
        assertTrue(stopped.isWaitingToResume(gameId, players[0]))
        assertFalse(stopped.isWaitingToResume(gameId, players[0] + 100))
        assertFalse(manager.isWaitingToResume(gameId, players[0]), "It's running")
        store.finish(gameId, GameStatus.ABANDONED)
        assertFalse(stopped.isWaitingToResume(gameId, players[0]))
    }

    @Test
    fun connect_afterTheGameEnded_returnsFalse() = withFixture(TurnLimits(maxUnattendedTimeouts = 0)) { gameId, _ ->
        val host = manager.host(gameId)!!
        scheduler.fire()
        waitUntilGone(gameId)
        assertFalse(host.connect(RED, SeatConnection()))
    }

    @Test
    fun resumeAll_afterRestart_continuesFromTheSameState() = withFixture { gameId, _ ->
        val red = connect(gameId, RED)
        val host = manager.host(gameId)!!
        repeat(3) { timeOut(red) }
        val before = SnapshotCodec.encode(host.game)

        val restarted = restartedManager()
        restarted.resumeAll()

        assertContentEquals(before, SnapshotCodec.encode(restarted.host(gameId)!!.game))
        assertEquals(RED, restarted.host(gameId)!!.seatOf(store.seats(gameId).first().accountId!!))
    }

    @Test
    fun resumeAll_winningMoveSavedButNotMarked_finishesTheGame() = withFixture { _, players ->
        val recorder = GameRecorder(seatColors, boardSeed = 5L, gameSeed = 6L)
        val gameId = store.create(seatColors.zip(players), 5L, 6L)
        while (recorder.game.phase !is Phase.GameOver) {
            for (seat in AutoMove.waitingOn(recorder.game)) {
                val action = scriptedAction(recorder.game.viewFor(seat))
                val seq = recorder.actionCount
                assertIs<ActionResult.Accepted>(recorder.apply(seat, action))
                store.appendAction(gameId, seq, seat, action)
            }
        }
        assertEquals(GameStatus.ACTIVE, store.status(gameId))

        val restarted = restartedManager()
        restarted.resumeAll()
        while (restarted.host(gameId) != null) delay(5)

        assertEquals(GameStatus.FINISHED, store.status(gameId))
    }

    @Test
    fun resumeAll_otherRulesVersion_marksTheGameUnavailable() = withFixture { gameId, _ ->
        db.transaction {
            prepareStatement("UPDATE games SET rules_version = 0 WHERE id = ?").use {
                it.setLong(1, gameId)
                it.executeUpdate()
            }
        }
        val restarted = restartedManager()
        restarted.resumeAll()
        assertNull(restarted.host(gameId))
        assertEquals(GameStatus.UNAVAILABLE, store.status(gameId))
    }

    @Test
    fun resumeAll_corruptAction_marksTheGameUnavailable() = withFixture { gameId, _ ->
        db.transaction {
            prepareStatement("INSERT INTO game_actions (game_id, seq, bytes) VALUES (?, 0, ?)").use {
                it.setLong(1, gameId)
                it.setBytes(2, byteArrayOf(99, 99))
                it.executeUpdate()
            }
        }
        val restarted = restartedManager()
        restarted.resumeAll()
        assertNull(restarted.host(gameId))
        assertEquals(GameStatus.UNAVAILABLE, store.status(gameId))
    }
}
