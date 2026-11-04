package com.tgaetaprojects.settledown.games

import com.tgaetaprojects.settledown.game.codec.CodecException
import com.tgaetaprojects.settledown.game.codec.WireCodec
import com.tgaetaprojects.settledown.store.Account
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The live game socket. Must sit inside `authenticate`, so the handshake itself needs a valid session.
 *
 * @param maxActionsPerSecond far above what a person can do. A client past it is broken or hostile, and every accepted
 *   action costs a database write.
 */
fun Route.gameSocket(games: GameManager, maxActionsPerSecond: Int, now: () -> Long) {
    webSocket("/games/{id}/ws") {
        val account = call.principal<Account>()!!
        val gameId = call.parameters["id"]?.toLongOrNull()
        val host = gameId?.let(games::host)
        val seat = host?.seatOf(account.id)
        val connection = SeatConnection()
        if (seat == null || !host.connect(seat, connection)) {
            // not revealing whether the game exists: to anyone without a seat, it's simply unavailable
            gameId?.let { games.finalUpdate(it, account.id) }?.let { send(Frame.Binary(true, WireCodec.encode(it))) }
            val resumes = gameId != null && games.isWaitingToResume(gameId, account.id)
            closeWith(
                if (resumes) CloseReason.Codes.TRY_AGAIN_LATER.code else WireCodec.CLOSE_GAME_UNAVAILABLE,
                "This game isn't available"
            )
            return@webSocket
        }

        val writer = launch {
            for (bytes in connection.frames) send(Frame.Binary(true, bytes))
            closeWith(connection.closeCode ?: WireCodec.CLOSE_GAME_UNAVAILABLE, "Connection closed by the server")
        }
        var windowStart = now()
        var actionsInWindow = 0
        try {
            for (frame in incoming) {
                if (now() - windowStart >= 1000) {
                    windowStart = now()
                    actionsInWindow = 0
                }
                val message = (frame as? Frame.Binary)?.let {
                    try {
                        WireCodec.decodeClientAction(it.readBytes())
                    } catch (_: CodecException) {
                        null
                    }
                }
                if (message == null || ++actionsInWindow > maxActionsPerSecond) {
                    closeWith(WireCodec.CLOSE_PROTOCOL_ERROR, "Invalid message")
                    break
                }
                host.act(seat, connection, message)
            }
        } finally {
            writer.cancel()
            withContext(NonCancellable) { host.disconnect(seat, connection) }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.closeWith(code: Short, message: String) =
    close(CloseReason(code, message))
