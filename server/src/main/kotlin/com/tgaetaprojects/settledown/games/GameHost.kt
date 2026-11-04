package com.tgaetaprojects.settledown.games

import com.tgaetaprojects.settledown.api.GameStatus
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.codec.ClientAction
import com.tgaetaprojects.settledown.game.codec.GameRecorder
import com.tgaetaprojects.settledown.game.codec.ServerMessage
import com.tgaetaprojects.settledown.game.codec.WireCodec
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.ActionResult
import com.tgaetaprojects.settledown.game.engine.Game
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.viewFor
import com.tgaetaprojects.settledown.store.GameStore
import com.tgaetaprojects.settledown.store.StoredSeat
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Runs [task] after a delay, and returns a function that cancels it. A seam for tests, which fire timers by hand. */
fun interface Scheduler {
    fun schedule(after: Duration, task: suspend () -> Unit): () -> Unit
}

/** How long the game waits on a player before moving for them. */
data class TurnLimits(
    val roll: Duration = 30.seconds,
    val setup: Duration = 60.seconds,
    val discard: Duration = 45.seconds,
    val robber: Duration = 30.seconds,
    val main: Duration = 120.seconds,
    /** Timeouts in a row, with no player acting, before the game is abandoned. About three rounds in a 4-player game. */
    val maxUnattendedTimeouts: Int = 24,
) {
    fun forPhase(phase: Phase): Duration = when (phase) {
        is Phase.RollForFirstPlayer, Phase.PreRoll -> roll
        is Phase.SetupSettlement, is Phase.SetupRoad -> setup
        is Phase.Discard -> discard
        is Phase.MoveRobber, is Phase.RoadBuilding -> robber
        Phase.Main -> main
        is Phase.GameOver -> Duration.ZERO
    }
}

/** A seat's live connection. The host never waits on one, so a stalled client can't hold up the game. */
class SeatConnection {
    private val outgoing = Channel<ByteArray>(OUTGOING_CAPACITY)

    /** The frames to write to the socket; it closes when the connection should. */
    val frames: Channel<ByteArray> get() = outgoing

    @Volatile
    var closeCode: Short? = null
        private set

    /** The newest action accepted from this connection. Only the host's coroutine touches it. */
    internal var lastClientSeq = 0

    internal fun send(message: ServerMessage) {
        // a client this far behind reconnects and gets the whole current view
        if (outgoing.trySend(WireCodec.encode(message)).isFailure) close(CloseReason.Codes.TRY_AGAIN_LATER.code)
    }

    internal fun close(code: Short) {
        if (closeCode == null) closeCode = code
        outgoing.close()
    }

    private companion object {
        const val OUTGOING_CAPACITY = 64
    }
}

/**
 * Plays one game. A single coroutine reads a queue of [Command]s, so the mutable [Game] is only ever touched from one
 * place, however many sockets and timers are involved.
 *
 * An accepted action is written to the database before anyone hears about it. If that write fails the game stops, and
 * the next restart resumes it from the last action that was saved.
 *
 * Everything sent to a seat comes from [viewFor] and `eventsFor` for that seat. Nothing else about the game leaves here.
 */
class GameHost internal constructor(
    val gameId: Long,
    private val recorder: GameRecorder,
    storedSeats: List<StoredSeat>,
    private val store: GameStore,
    private val scheduler: Scheduler,
    private val limits: TurnLimits,
    private val now: () -> Long,
    private val onEnded: (GameHost) -> Unit,
) {
    private sealed interface Command {
        class Connect(val seat: PlayerColor, val connection: SeatConnection) : Command
        class Disconnect(val seat: PlayerColor, val connection: SeatConnection) : Command
        class Act(val seat: PlayerColor, val connection: SeatConnection, val message: ClientAction) : Command
        class DropConnection(val seat: PlayerColor, val code: Short) : Command
        class TimerFired(val generation: Int) : Command
    }

    /** What makes the clock restart. A discard's shrinking list and each build in a turn deliberately don't. */
    private data class TimerKey(val phase: Any, val currentPlayer: PlayerColor, val turn: Int)

    internal val game: Game get() = recorder.game

    private val seats = ConcurrentHashMap<Long, PlayerColor>().apply {
        for (seat in storedSeats) seat.accountId?.let { put(it, seat.color) }
    }
    private val inbox = Channel<Command>(INBOX_CAPACITY)
    private val connections = LinkedHashMap<PlayerColor, SeatConnection>()
    private var ended = false

    private var timerKey: TimerKey? = null
    private var timerGeneration = 0
    private var cancelTimer: () -> Unit = {}
    private var deadlineAt = 0L
    private var unattendedTimeouts = 0

    /** The seat [accountId] holds in this game, if any. */
    fun seatOf(accountId: Long): PlayerColor? = seats[accountId]

    /** Returns false if the game has already ended. */
    suspend fun connect(seat: PlayerColor, connection: SeatConnection): Boolean =
        submit(Command.Connect(seat, connection))

    suspend fun disconnect(seat: PlayerColor, connection: SeatConnection) {
        submit(Command.Disconnect(seat, connection))
    }

    suspend fun act(seat: PlayerColor, connection: SeatConnection, message: ClientAction) {
        submit(Command.Act(seat, connection, message))
    }

    /** A deleted account gives up its connection. The game goes on, and the seat is played by the timer. */
    suspend fun removeAccount(accountId: Long) {
        seats.remove(accountId)?.let { submit(Command.DropConnection(it, WireCodec.CLOSE_GAME_UNAVAILABLE)) }
    }

    /**
     * Closes the account's connection after it signs out, since a socket outlives the session that opened it. The
     * code invites a reconnect: that fails if this was the session signing out, and restores any other device.
     */
    suspend fun disconnectAccount(accountId: Long) {
        seats[accountId]?.let { submit(Command.DropConnection(it, CloseReason.Codes.TRY_AGAIN_LATER.code)) }
    }

    internal fun start(scope: CoroutineScope): Job = scope.launch {
        try {
            // the server stopped between saving the winning move and marking the game finished
            if (game.phase is Phase.GameOver) {
                store.finish(gameId, GameStatus.FINISHED, recorder.record())
                ended = true
                return@launch
            }
            restartTimer()
            for (command in inbox) {
                handle(command)
                if (ended) break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Game $gameId stopped; it will resume from its saved actions on the next restart", e)
        } finally {
            shutDown()
        }
    }

    private suspend fun submit(command: Command): Boolean =
        try {
            inbox.send(command)
            true
        } catch (_: ClosedSendChannelException) {
            false
        }

    private suspend fun handle(command: Command) {
        when (command) {
            is Command.Connect -> connected(command.seat, command.connection)
            is Command.Disconnect ->
                if (connections[command.seat] === command.connection) {
                    connections.remove(command.seat)
                    broadcastPresence()
                }

            is Command.DropConnection ->
                connections.remove(command.seat)?.let {
                    it.close(command.code)
                    broadcastPresence()
                }

            is Command.Act -> acted(command.seat, command.connection, command.message)
            is Command.TimerFired -> if (command.generation == timerGeneration) timedOut()
        }
    }

    private fun connected(seat: PlayerColor, connection: SeatConnection) {
        // the account may have been deleted after this connection was let in
        if (!seats.containsValue(seat)) {
            connection.close(WireCodec.CLOSE_GAME_UNAVAILABLE)
            return
        }
        connections.put(seat, connection)?.close(WireCodec.CLOSE_REPLACED)
        connection.send(ServerMessage.Update(game.viewFor(seat), emptyList(), 0))
        connection.send(deadlineMessage())
        broadcastPresence()
    }

    private suspend fun acted(seat: PlayerColor, connection: SeatConnection, message: ClientAction) {
        // a connection that has been replaced no longer speaks for the seat
        if (connections[seat] !== connection) return
        when (val result = recorder.apply(seat, message.action)) {
            is ActionResult.Rejected -> connection.send(ServerMessage.Refused(message.clientSeq, result.violation))
            is ActionResult.Accepted -> {
                connection.lastClientSeq = message.clientSeq
                unattendedTimeouts = 0
                commit(seat, message.action, result)
            }
        }
    }

    private suspend fun timedOut() {
        if (++unattendedTimeouts > limits.maxUnattendedTimeouts) {
            store.finish(gameId, GameStatus.ABANDONED)
            ended = true
            return
        }
        val generation = timerGeneration
        for (seat in AutoMove.waitingOn(game)) {
            if (ended) return
            val action = AutoMove.choose(game, seat)
            val result = recorder.apply(seat, action)
            check(result is ActionResult.Accepted) { "An automatic move was refused: $result" }
            commit(seat, action, result)
        }
        // the phase can stay the same (a second free road), and the expired clock still needs replacing
        if (!ended && generation == timerGeneration) restartTimer()
    }

    private suspend fun commit(seat: PlayerColor, action: Action, result: ActionResult.Accepted) {
        store.appendAction(gameId, recorder.actionCount - 1, seat, action)
        for ((color, connection) in connections) {
            connection.send(ServerMessage.Update(game.viewFor(color), result.eventsFor(color), connection.lastClientSeq))
        }
        if (game.phase is Phase.GameOver) {
            store.finish(gameId, GameStatus.FINISHED, recorder.record())
            ended = true
        } else if (currentTimerKey() != timerKey) {
            restartTimer()
        }
    }

    private fun currentTimerKey() = TimerKey(game.phase::class, game.currentPlayer, game.turn)

    private fun restartTimer() {
        cancelTimer()
        timerKey = currentTimerKey()
        val generation = ++timerGeneration
        val limit = limits.forPhase(game.phase)
        deadlineAt = now() + limit.inWholeMilliseconds
        cancelTimer = scheduler.schedule(limit) { submit(Command.TimerFired(generation)) }
        val message = deadlineMessage()
        connections.values.forEach { it.send(message) }
    }

    private fun deadlineMessage() = ServerMessage.Deadline(
        AutoMove.waitingOn(game),
        (deadlineAt - now()).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    )

    private fun broadcastPresence() {
        val message = ServerMessage.Presence(connections.keys.toSet())
        connections.values.forEach { it.send(message) }
    }

    /** Must not suspend: it also runs when the application is stopping. */
    private fun shutDown() {
        cancelTimer()
        inbox.close()
        // only a game that's really over is gone; otherwise it resumes on restart, and clients should come back
        val code = if (ended) WireCodec.CLOSE_GAME_UNAVAILABLE else CloseReason.Codes.TRY_AGAIN_LATER.code
        // anyone still queued to connect would otherwise wait forever on a host that has stopped reading
        while (true) {
            val command = inbox.tryReceive().getOrNull() ?: break
            if (command is Command.Connect) command.connection.close(code)
        }
        connections.values.forEach { it.close(code) }
        connections.clear()
        onEnded(this)
    }

    internal companion object {
        private const val INBOX_CAPACITY = 64
        private val log = LoggerFactory.getLogger(GameHost::class.java)

        /** The real clock, on coroutines of [scope]. */
        fun scopeScheduler(scope: CoroutineScope) = Scheduler { after, task ->
            val job = scope.launch {
                delay(after)
                task()
            }
            return@Scheduler { job.cancel() }
        }
    }
}
