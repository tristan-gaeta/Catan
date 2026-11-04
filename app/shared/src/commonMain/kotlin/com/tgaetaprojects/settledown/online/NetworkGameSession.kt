package com.tgaetaprojects.settledown.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.codec.ClientAction
import com.tgaetaprojects.settledown.game.codec.ServerMessage
import com.tgaetaprojects.settledown.game.codec.WireCodec
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.PlayerView
import com.tgaetaprojects.settledown.game.engine.RuleViolation
import com.tgaetaprojects.settledown.ui.game.GameController
import com.tgaetaprojects.settledown.ui.game.LateRefusal
import com.tgaetaprojects.settledown.ui.game.TurnDeadline
import com.tgaetaprojects.settledown.ui.game.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** One way to reach a game. A seam for tests, which stand in for the server. */
fun interface GameTransport {
    /**
     * Connects, sends what arrives on [outgoing], and passes each incoming frame to [onMessage], until the connection
     * closes. Returns the close code if there was one; throwing means the connection failed, and
     * [SessionExpiredException] that retrying can't help.
     */
    suspend fun connect(outgoing: ReceiveChannel<ByteArray>, onMessage: suspend (ByteArray) -> Unit): Short?
}

/**
 * A seat in a game on the server. It holds only that seat's [PlayerView] and never decides anything: an action goes to
 * the server, and the result comes back as a new view or a [refusal].
 *
 * It reconnects by itself whenever the connection drops, unless the server says not to. Each connection starts with
 * the whole current view, so nothing needs repairing. The log restarts, because events aren't kept for clients.
 *
 * @param names who sits where, for [nameOf]; a color with no name is a player who deleted their account.
 */
class NetworkGameSession(
    private val transport: GameTransport,
    private val names: Map<PlayerColor, String>,
    private val scope: CoroutineScope,
    private val retryDelay: (attempt: Int) -> Duration = { attempt -> (1 shl attempt.coerceAtMost(5)).seconds },
    private val onSessionExpired: () -> Unit = {},
) : GameController() {
    enum class Connection { CONNECTING, CONNECTED, RECONNECTING, ENDED }

    private var latestView by mutableStateOf<PlayerView?>(null)

    /** Whether the first view has arrived; [view] can't be read until then. */
    val isReady: Boolean get() = latestView != null

    override val view: PlayerView get() = checkNotNull(latestView) { "No view has arrived yet" }

    var connection by mutableStateOf(Connection.CONNECTING)
        private set

    /** The seats with a live connection; `null` until the server says. */
    var connectedSeats by mutableStateOf<Set<PlayerColor>?>(null)
        private set

    private var deadlineState by mutableStateOf<TurnDeadline?>(null)
    override val deadline: TurnDeadline? get() = deadlineState

    private var refusalState by mutableStateOf<LateRefusal?>(null)
    override val refusal: LateRefusal? get() = refusalState

    override val notice: String?
        get() = when (connection) {
            Connection.CONNECTING, Connection.CONNECTED -> null
            Connection.RECONNECTING -> "Reconnecting…"
            Connection.ENDED -> if (isReady && phase is Phase.GameOver) null else "This game is no longer available."
        }

    private var link: Channel<ByteArray>? = null
    private var nextSeq = 0
    private var newestAccepted = 0
    private var job: Job? = null

    override fun nameOf(color: PlayerColor): String = names[color] ?: color.label

    override fun isConnected(color: PlayerColor): Boolean? = connectedSeats?.let { color in it }

    /** An action sent while disconnected is dropped, because the turn may have moved on by the time it could arrive. */
    override fun act(action: Action): RuleViolation? {
        val seq = ++nextSeq
        link?.trySend(WireCodec.encode(ClientAction(seq, action)))
        return null
    }

    fun start() {
        check(job == null) { "Already started" }
        job = scope.launch { connectUntilEnded() }
    }

    /** Stops connecting and closes the socket. The seat stays yours, and the game goes on without you. */
    fun stop() {
        job?.cancel()
        link = null
    }

    private suspend fun connectUntilEnded() {
        var attempt = 0
        while (true) {
            val outgoing = Channel<ByteArray>(OUTGOING_CAPACITY)
            val closeCode = try {
                transport.connect(outgoing) { bytes ->
                    if (link !== outgoing) {
                        // the server only speaks once the socket is really up, so this is the moment it counts
                        link = outgoing
                        newestAccepted = 0
                        attempt = 0
                        connection = Connection.CONNECTED
                    }
                    handle(WireCodec.decodeServerMessage(bytes))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                link = null
                connection = Connection.ENDED
                onSessionExpired()
                return
            } catch (_: Exception) {
                null
            }
            link = null
            outgoing.close()
            deadlineState = null
            if (closeCode in FINAL_CLOSE_CODES) {
                connection = Connection.ENDED
                return
            }
            connection = Connection.RECONNECTING
            delay(retryDelay(attempt++))
        }
    }

    private fun handle(message: ServerMessage) {
        when (message) {
            is ServerMessage.Update -> {
                latestView = message.view
                if (message.events.isNotEmpty()) recordAccepted(message.events, message.view.phase)
                if (message.lastClientSeq > newestAccepted || !message.view.mustAct) {
                    newestAccepted = message.lastClientSeq
                    clearTool()
                }
            }

            is ServerMessage.Refused -> refusalState = LateRefusal(message.violation)
            is ServerMessage.Presence -> connectedSeats = message.connected
            is ServerMessage.Deadline ->
                deadlineState = message.waitingOn.takeIf { it.isNotEmpty() }?.let { seats ->
                    TurnDeadline(seats, TimeSource.Monotonic.markNow() + message.remainingMillis.milliseconds)
                }
        }
    }

    private companion object {
        const val OUTGOING_CAPACITY = 16

        /** The server is saying not to come back. */
        val FINAL_CLOSE_CODES = setOf(
            WireCodec.CLOSE_GAME_UNAVAILABLE,
            WireCodec.CLOSE_REPLACED,
            WireCodec.CLOSE_PROTOCOL_ERROR,
        )
    }
}
