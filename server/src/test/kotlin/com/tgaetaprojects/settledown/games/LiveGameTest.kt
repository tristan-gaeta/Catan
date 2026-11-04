package com.tgaetaprojects.settledown.games

import com.tgaetaprojects.settledown.api.CreateRoomRequest
import com.tgaetaprojects.settledown.api.GameStatus
import com.tgaetaprojects.settledown.api.GameSummary
import com.tgaetaprojects.settledown.api.RoomInfo
import com.tgaetaprojects.settledown.anonymousClient
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.codec.ClientAction
import com.tgaetaprojects.settledown.game.codec.ServerMessage
import com.tgaetaprojects.settledown.game.codec.WireCodec
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.GameEvent
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.PlayerView
import com.tgaetaprojects.settledown.signedIn
import com.tgaetaprojects.settledown.testServer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class LiveGameTest {

    /** A full room's players and its game id. */
    private suspend fun ApplicationTestBuilder.startGame(seats: Int): Pair<List<HttpClient>, Long> {
        val players = List(seats) { signedIn("player-$it") }
        val code = players[0].post("/rooms") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomRequest(seats))
        }.body<RoomInfo>().code
        var gameId: Long? = null
        for (player in players.drop(1)) gameId = player.post("/rooms/$code/join").body<RoomInfo>().gameId
        return players to assertNotNull(gameId)
    }

    private suspend fun DefaultClientWebSocketSession.nextMessage(): ServerMessage {
        val frame = incoming.receive()
        return WireCodec.decodeServerMessage(assertIs<Frame.Binary>(frame).readBytes())
    }

    private suspend fun DefaultClientWebSocketSession.firstUpdate(): ServerMessage.Update {
        while (true) (nextMessage() as? ServerMessage.Update)?.let { return it }
    }

    /** Nothing this seat is told may hold a card it isn't entitled to know. */
    private fun assertOnlyWhatThisSeatMayKnow(seat: PlayerColor, update: ServerMessage.Update) {
        assertEquals(seat, update.view.viewer)
        for (event in update.events) {
            when (event) {
                is GameEvent.Stole ->
                    if (seat != event.thief && seat != event.victim) assertNull(event.resource, "$seat saw a steal")

                is GameEvent.DevCardBought ->
                    if (seat != event.player) assertNull(event.card, "$seat saw a bought card")

                else -> Unit
            }
        }
    }

    /** Plays this seat until the game ends. Returns what it saw, for checks across seats. */
    private suspend fun DefaultClientWebSocketSession.playToTheEnd(): PlayerView {
        var seat: PlayerColor? = null
        var nextSeq = 1
        var awaiting: Int? = null
        while (true) {
            when (val message = nextMessage()) {
                is ServerMessage.Update -> {
                    val me = seat ?: message.view.viewer.also { seat = it }
                    assertOnlyWhatThisSeatMayKnow(me, message)
                    if (message.lastClientSeq == awaiting) awaiting = null
                    if (message.view.phase is Phase.GameOver) return message.view
                    if (awaiting == null && message.view.mustAct) {
                        val seq = nextSeq++
                        awaiting = seq
                        send(Frame.Binary(true, WireCodec.encode(ClientAction(seq, scriptedAction(message.view)))))
                    }
                }

                is ServerMessage.Refused -> fail("$seat was refused: $message")
                is ServerMessage.Presence, is ServerMessage.Deadline -> Unit
            }
        }
    }

    @Test
    fun fullGame_fourPlayers_playsToAWinnerOverWebSockets() = testServer {
        val (players, gameId) = startGame(4)
        val finals = withTimeout(120.seconds) {
            coroutineScope {
                players.map { player ->
                    async {
                        var final: PlayerView? = null
                        player.webSocket("/games/$gameId/ws") { final = playToTheEnd() }
                        assertNotNull(final)
                    }
                }.awaitAll()
            }
        }

        val winners = finals.map { (it.phase as Phase.GameOver).winner }.toSet()
        assertEquals(1, winners.size, "Every seat must agree who won")
        assertEquals(4, finals.map { it.viewer }.toSet().size)

        for (player in players) {
            val summary = waitForStatus(player, gameId, GameStatus.FINISHED)
            assertEquals(4, summary.seats.size)
        }
    }

    private suspend fun waitForStatus(player: HttpClient, gameId: Long, status: GameStatus): GameSummary {
        repeat(200) {
            val game = player.get("/me/games").body<List<GameSummary>>().first { it.id == gameId }
            if (game.status == status) return game
            delay(10)
        }
        fail("Game $gameId never became $status")
    }

    @Test
    fun finishedGame_connectingAfterwards_getsTheFinalViewThenClose() = testServer {
        val (players, gameId) = startGame(3)
        withTimeout(120.seconds) {
            coroutineScope {
                players.map { player ->
                    async { player.webSocket("/games/$gameId/ws") { playToTheEnd() } }
                }.awaitAll()
            }
        }
        waitForStatus(players[2], gameId, GameStatus.FINISHED)

        players[2].webSocket("/games/$gameId/ws") {
            val update = firstUpdate()
            assertIs<Phase.GameOver>(update.view.phase)
            assertEquals(WireCodec.CLOSE_GAME_UNAVAILABLE, closeReason.await()?.code)
        }
    }

    @Test
    fun socket_notSignedIn_isRefused() = testServer {
        val (players, gameId) = startGame(3)
        assertFails { anonymousClient().webSocket("/games/$gameId/ws") { fail("It connected") } }
        // the same address does work with a session, so the refusal above was about the missing one
        players[0].webSocket("/games/$gameId/ws") { firstUpdate() }
    }

    @Test
    fun socket_otherProtocolVersion_isRefused() = testServer {
        val (players, gameId) = startGame(3)
        assertFails {
            players[0].webSocket("/games/$gameId/ws", request = { header(WireCodec.VERSION_HEADER, "0.0") }) {
                fail("It connected")
            }
        }
        // the same player does get in with the right version, so the refusal above was about the version
        players[0].webSocket("/games/$gameId/ws") { firstUpdate() }
    }

    @Test
    fun socket_noSeatAtTheGame_isClosedWithNothingSent() = testServer {
        val (_, gameId) = startGame(3)
        signedIn("stranger").webSocket("/games/$gameId/ws") {
            assertTrue(incoming.receiveCatching().isClosed)
            assertEquals(WireCodec.CLOSE_GAME_UNAVAILABLE, closeReason.await()?.code)
        }
    }

    @Test
    fun socket_unknownGame_isClosed() = testServer {
        signedIn("stranger").webSocket("/games/999/ws") {
            assertTrue(incoming.receiveCatching().isClosed)
            assertEquals(WireCodec.CLOSE_GAME_UNAVAILABLE, closeReason.await()?.code)
        }
    }

    @Test
    fun socket_malformedFrame_closesOnlyThatConnection() = testServer {
        val (players, gameId) = startGame(3)
        players[0].webSocket("/games/$gameId/ws") {
            firstUpdate()
            send(Frame.Binary(true, byteArrayOf(1, 2, 3)))
            assertEquals(WireCodec.CLOSE_PROTOCOL_ERROR, closeReason.await()?.code)
        }
        players[1].webSocket("/games/$gameId/ws") {
            assertEquals(3, firstUpdate().view.seatOrder.size)
        }
    }

    @Test
    fun socket_textFrame_isAProtocolError() = testServer {
        val (players, gameId) = startGame(3)
        players[0].webSocket("/games/$gameId/ws") {
            firstUpdate()
            send(Frame.Text("hello"))
            assertEquals(WireCodec.CLOSE_PROTOCOL_ERROR, closeReason.await()?.code)
        }
    }

    @Test
    fun socket_tooManyActions_isClosed() = testServer(maxActionsPerSecond = 3) {
        val (players, gameId) = startGame(3)
        players[0].webSocket("/games/$gameId/ws") {
            firstUpdate()
            // refused actions count too: flooding is the problem, whatever the content
            repeat(50) { send(Frame.Binary(true, WireCodec.encode(ClientAction(it + 1, Action.EndTurn)))) }
            assertEquals(WireCodec.CLOSE_PROTOCOL_ERROR, closeReason.await()?.code)
        }
    }

    @Test
    fun socket_sameAccountTwice_replacesTheFirst() = testServer {
        val (players, gameId) = startGame(3)
        players[0].webSocket("/games/$gameId/ws") {
            firstUpdate()
            players[0].webSocket("/games/$gameId/ws") { firstUpdate() }
            assertEquals(WireCodec.CLOSE_REPLACED, closeReason.await()?.code)
        }
    }

    @Test
    fun signOut_closesTheirSocketAndTheyCantReconnect() = testServer {
        val (players, gameId) = startGame(3)
        players[0].webSocket("/games/$gameId/ws") {
            firstUpdate()
            players[0].delete("/auth/session")
            assertEquals(CloseReason.Codes.TRY_AGAIN_LATER.code, closeReason.await()?.code)
        }
        assertFails { players[0].webSocket("/games/$gameId/ws") { fail("It connected") } }
    }

    @Test
    fun account_deletedMidGame_closesTheirSocket() = testServer {
        val (players, gameId) = startGame(3)
        players[0].webSocket("/games/$gameId/ws") {
            firstUpdate()
            players[0].delete("/account")
            assertEquals(WireCodec.CLOSE_GAME_UNAVAILABLE, closeReason.await()?.code)
        }
    }
}
