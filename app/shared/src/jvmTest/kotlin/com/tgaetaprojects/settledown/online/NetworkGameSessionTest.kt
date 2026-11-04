package com.tgaetaprojects.settledown.online

import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.PlayerColor.BLUE
import com.tgaetaprojects.settledown.game.PlayerColor.RED
import com.tgaetaprojects.settledown.game.PlayerColor.WHITE
import com.tgaetaprojects.settledown.game.codec.ClientAction
import com.tgaetaprojects.settledown.game.codec.ServerMessage
import com.tgaetaprojects.settledown.game.codec.WireCodec
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.Game
import com.tgaetaprojects.settledown.game.engine.GameEvent
import com.tgaetaprojects.settledown.game.engine.PlayerView
import com.tgaetaprojects.settledown.game.engine.RuleViolation
import com.tgaetaprojects.settledown.game.engine.viewFor
import com.tgaetaprojects.settledown.ui.game.BuildTool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Stands in for the server: a test drives each connection by hand. */
private class FakeServer : GameTransport {
    /** One connection: what the client sent, frames to send it, and how to end it. */
    class Connection(val fromClient: ReceiveChannel<ByteArray>) {
        val toClient = Channel<ByteArray>(Channel.UNLIMITED)
        val closeWith = CompletableDeferred<Short?>()

        fun send(message: ServerMessage) {
            toClient.trySend(WireCodec.encode(message))
        }
    }

    val connections = Channel<Connection>(Channel.UNLIMITED)

    override suspend fun connect(outgoing: ReceiveChannel<ByteArray>, onMessage: suspend (ByteArray) -> Unit): Short? =
        coroutineScope {
            val connection = Connection(outgoing)
            connections.send(connection)
            val reader = launch { for (bytes in connection.toClient) onMessage(bytes) }
            val code = connection.closeWith.await()
            // anything the test sent before closing is delivered first, as on a real socket
            connection.toClient.close()
            reader.join()
            code
        }
}

class NetworkGameSessionTest {
    private fun viewOf(game: Game = Game(listOf(RED, WHITE, BLUE), 1L, 2L), seat: PlayerColor = RED): PlayerView =
        game.viewFor(seat)

    private fun withSession(block: suspend (NetworkGameSession, FakeServer) -> Unit) = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val server = FakeServer()
        val session = NetworkGameSession(server, mapOf(RED to "Alice"), scope, retryDelay = { 1.milliseconds })
        try {
            session.start()
            withTimeout(20.seconds) { block(session, server) }
        } finally {
            scope.cancel()
        }
    }

    private suspend fun waitFor(condition: () -> Boolean) {
        while (!condition()) delay(2)
    }

    @Test
    fun view_beforeFirstUpdate_isNotReady() = withSession { session, _ ->
        assertFalse(session.isReady)
    }

    @Test
    fun update_replacesTheViewAndAddsToTheLog() = withSession { session, server ->
        val connection = server.connections.receive()
        connection.send(ServerMessage.Update(viewOf(), listOf(GameEvent.TurnStarted(RED, 1)), 0))
        waitFor { session.isReady && session.log.isNotEmpty() }
        assertEquals(RED, session.actor)
        assertEquals(NetworkGameSession.Connection.CONNECTED, session.connection)
    }

    @Test
    fun update_noEvents_leavesTheLogAlone() = withSession { session, server ->
        server.connections.receive().send(ServerMessage.Update(viewOf(), emptyList(), 0))
        waitFor { session.isReady }
        assertTrue(session.log.isEmpty())
    }

    @Test
    fun act_sendsAnActionWithNoActorAndARisingSeq() = withSession { session, server ->
        val connection = server.connections.receive()
        connection.send(ServerMessage.Update(viewOf(), emptyList(), 0))
        waitFor { session.isReady }

        assertNull(session.act(Action.RollDice))
        session.act(Action.EndTurn)

        assertEquals(ClientAction(1, Action.RollDice), WireCodec.decodeClientAction(connection.fromClient.receive()))
        assertEquals(ClientAction(2, Action.EndTurn), WireCodec.decodeClientAction(connection.fromClient.receive()))
    }

    @Test
    fun act_whileDisconnected_isDropped() = withSession { session, server ->
        val first = server.connections.receive()
        first.send(ServerMessage.Update(viewOf(), emptyList(), 0))
        waitFor { session.isReady }
        first.closeWith.complete(null)
        waitFor { session.connection == NetworkGameSession.Connection.RECONNECTING }

        session.act(Action.RollDice)

        val second = server.connections.receive()
        second.send(ServerMessage.Update(viewOf(), emptyList(), 0))
        waitFor { session.connection == NetworkGameSession.Connection.CONNECTED }
        assertTrue(second.fromClient.isEmpty, "A move from before the reconnect must not arrive after it")
    }

    @Test
    fun refused_becomesARefusal() = withSession { session, server ->
        server.connections.receive().send(ServerMessage.Refused(1, RuleViolation.NOT_YOUR_TURN))
        waitFor { session.refusal != null }
        assertEquals(RuleViolation.NOT_YOUR_TURN, session.refusal?.violation)
    }

    @Test
    fun refused_sameTwice_isANewRefusalEachTime() = withSession { session, server ->
        val connection = server.connections.receive()
        connection.send(ServerMessage.Refused(1, RuleViolation.NOT_YOUR_TURN))
        waitFor { session.refusal != null }
        val first = session.refusal
        connection.send(ServerMessage.Refused(2, RuleViolation.NOT_YOUR_TURN))
        waitFor { session.refusal !== first }
    }

    @Test
    fun presence_updatesWhoIsConnected() = withSession { session, server ->
        val connection = server.connections.receive()
        connection.send(ServerMessage.Presence(setOf(RED)))
        waitFor { session.connectedSeats != null }
        assertEquals(true, session.isConnected(RED))
        assertEquals(false, session.isConnected(WHITE))
    }

    @Test
    fun deadline_countsDownFromWhatTheServerSaid() = withSession { session, server ->
        server.connections.receive().send(ServerMessage.Deadline(setOf(RED), 30_000))
        waitFor { session.deadline != null }
        val deadline = assertNotNull(session.deadline)
        assertEquals(setOf(RED), deadline.waitingOn)
        assertTrue(deadline.secondsLeft() in 29..30)
    }

    @Test
    fun deadline_nobodyWaitedOn_clearsIt() = withSession { session, server ->
        val connection = server.connections.receive()
        connection.send(ServerMessage.Deadline(setOf(RED), 30_000))
        waitFor { session.deadline != null }
        connection.send(ServerMessage.Deadline(emptySet(), 0))
        waitFor { session.deadline == null }
    }

    @Test
    fun connection_droppedWithoutACode_reconnects() = withSession { session, server ->
        val first = server.connections.receive()
        first.send(ServerMessage.Update(viewOf(), emptyList(), 0))
        waitFor { session.isReady }
        first.closeWith.complete(null)
        val second = server.connections.receive()
        second.send(ServerMessage.Update(viewOf(seat = RED), emptyList(), 0))
        waitFor { session.connection == NetworkGameSession.Connection.CONNECTED }
    }

    @Test
    fun connection_retryableCode_reconnects() = withSession { _, server ->
        server.connections.receive().closeWith.complete(1013)
        assertNotNull(server.connections.receive())
    }

    @Test
    fun connection_finalCodes_stopReconnecting() {
        for (code in listOf(WireCodec.CLOSE_GAME_UNAVAILABLE, WireCodec.CLOSE_REPLACED, WireCodec.CLOSE_PROTOCOL_ERROR)) {
            withSession { session, server ->
                server.connections.receive().closeWith.complete(code)
                waitFor { session.connection == NetworkGameSession.Connection.ENDED }
                delay(20)
                assertTrue(server.connections.isEmpty, "Close code $code must not reconnect")
                assertNotNull(session.notice)
            }
        }
    }

    @Test
    fun stop_doesNotReconnect() = withSession { session, server ->
        val connection = server.connections.receive()
        session.stop()
        connection.closeWith.complete(null)
        delay(20)
        assertTrue(server.connections.isEmpty)
    }

    @Test
    fun act_afterStop_sendsNothing() = withSession { session, server ->
        val connection = server.connections.receive()
        connection.send(ServerMessage.Update(viewOf(), emptyList(), 0))
        waitFor { session.isReady }
        session.stop()
        session.act(Action.RollDice)
        assertTrue(connection.fromClient.isEmpty)
    }

    @Test
    fun sessionExpired_endsAndReportsIt() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        var expired = false
        var attempts = 0
        val session = NetworkGameSession(
            { _, _ ->
                attempts++
                throw SessionExpiredException()
            },
            emptyMap(),
            scope,
            retryDelay = { 1.milliseconds },
            onSessionExpired = { expired = true },
        )
        try {
            session.start()
            withTimeout(20.seconds) { waitFor { expired } }
            delay(20)
            assertEquals(1, attempts, "Retrying can't help an expired session")
            assertEquals(NetworkGameSession.Connection.ENDED, session.connection)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun nameOf_knownAndUnknownSeats() = withSession { session, _ ->
        assertEquals("Alice", session.nameOf(RED))
        assertEquals("White", session.nameOf(WHITE))
    }

    @Test
    fun targets_whenNotYourMove_isEmpty() = withSession { session, server ->
        val game = Game(listOf(RED, WHITE, BLUE), 1L, 2L)
        server.connections.receive().send(ServerMessage.Update(game.viewFor(WHITE), emptyList(), 0))
        waitFor { session.isReady }
        assertFalse(session.canAct)
        assertTrue(session.targets().isEmpty)
        assertTrue(session.statusText.startsWith("Waiting for"))
    }

    @Test
    fun tool_notYourMoveAnymore_isCleared() = withSession { session, server ->
        val game = Game(listOf(RED, WHITE, BLUE), 1L, 2L)
        val connection = server.connections.receive()
        connection.send(ServerMessage.Update(game.viewFor(RED), emptyList(), 0))
        waitFor { session.isReady }
        session.selectTool(BuildTool.ROAD)
        connection.send(ServerMessage.Update(game.viewFor(WHITE), emptyList(), 0))
        waitFor { !session.canAct }
        assertNull(session.tool)
    }
}
