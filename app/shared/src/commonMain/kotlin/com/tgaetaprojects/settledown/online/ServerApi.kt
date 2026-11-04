package com.tgaetaprojects.settledown.online

import com.tgaetaprojects.settledown.api.AccountInfo
import com.tgaetaprojects.settledown.api.ApiError
import com.tgaetaprojects.settledown.api.CreateRoomRequest
import com.tgaetaprojects.settledown.api.GameSummary
import com.tgaetaprojects.settledown.api.GoogleSignInRequest
import com.tgaetaprojects.settledown.api.RoomInfo
import com.tgaetaprojects.settledown.api.SignInResponse
import com.tgaetaprojects.settledown.api.UpdateAccountRequest
import com.tgaetaprojects.settledown.game.codec.WireCodec
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

/** The server said no. [code] is stable for code to check, and [message] is written for the player. */
class ApiException(val status: Int, val code: String, override val message: String) : Exception(message)

/** The server's account, room and game-list requests, and the live game socket. */
class ServerApi(serverUrl: String, engine: HttpClientEngine = CIO.create()) {
    private val baseUrl = serverUrl.trimEnd('/')

    /** The session this client speaks for. */
    var sessionToken: String? = null

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json() }
        // the server's pings can't tell this side the network died, say when a phone changes networks
        install(WebSockets) { pingIntervalMillis = PING_INTERVAL_MILLIS }
        defaultRequest {
            header(WireCodec.VERSION_HEADER, WireCodec.PROTOCOL_VERSION)
            sessionToken?.let { bearerAuth(it) }
        }
    }

    suspend fun signIn(idToken: String): SignInResponse =
        client.post("$baseUrl/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleSignInRequest(idToken))
        }.parsed()

    suspend fun signOut() {
        client.delete("$baseUrl/auth/session").ensureSuccess()
    }

    suspend fun account(): AccountInfo = client.get("$baseUrl/account").parsed()

    suspend fun rename(displayName: String): AccountInfo =
        client.put("$baseUrl/account") {
            contentType(ContentType.Application.Json)
            setBody(UpdateAccountRequest(displayName))
        }.parsed()

    suspend fun deleteAccount() {
        client.delete("$baseUrl/account").ensureSuccess()
    }

    suspend fun createRoom(seats: Int): RoomInfo =
        client.post("$baseUrl/rooms") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomRequest(seats))
        }.parsed()

    suspend fun room(code: String): RoomInfo = client.get("$baseUrl/rooms/$code").parsed()

    suspend fun joinRoom(code: String): RoomInfo = client.post("$baseUrl/rooms/$code/join").parsed()

    suspend fun leaveRoom(code: String) {
        client.post("$baseUrl/rooms/$code/leave").ensureSuccess()
    }

    /** The room this player is waiting in, if any. */
    suspend fun currentRoom(): RoomInfo? {
        val response = client.get("$baseUrl/me/room")
        return if (response.status == HttpStatusCode.NoContent) null else response.parsed()
    }

    suspend fun myGames(): List<GameSummary> = client.get("$baseUrl/me/games").parsed()

    /** The live socket of game [gameId]. */
    fun transport(gameId: Long) = GameTransport { outgoing, onMessage ->
        var closeCode: Short? = null
        try {
            client.webSocket(urlString = "${baseUrl.replaceFirst("http", "ws")}/games/$gameId/ws") {
                val sender = launch { sendAll(outgoing) }
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Binary) onMessage(frame.readBytes())
                    }
                    closeCode = closeReason.await()?.code
                } finally {
                    sender.cancel()
                }
            }
        } catch (e: WebSocketException) {
            // a refused handshake doesn't say why, so ask: an ended session would otherwise retry forever
            if (client.get("$baseUrl/account").status == HttpStatusCode.Unauthorized) throw SessionExpiredException()
            throw e
        }
        closeCode
    }

    private suspend fun io.ktor.websocket.WebSocketSession.sendAll(outgoing: ReceiveChannel<ByteArray>) {
        for (bytes in outgoing) send(Frame.Binary(true, bytes))
    }

    fun close() = client.close()

    private suspend inline fun <reified T> HttpResponse.parsed(): T {
        ensureSuccess()
        return body()
    }

    private suspend fun HttpResponse.ensureSuccess() {
        if (status.isSuccess()) return
        val error = try {
            body<ApiError>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ApiError("unexpected", "The server had a problem. Please try again.")
        }
        throw ApiException(status.value, error.code, error.message)
    }

    private companion object {
        const val PING_INTERVAL_MILLIS = 20_000L
    }
}
