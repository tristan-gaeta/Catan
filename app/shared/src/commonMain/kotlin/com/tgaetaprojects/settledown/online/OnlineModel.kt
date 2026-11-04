package com.tgaetaprojects.settledown.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tgaetaprojects.settledown.api.AccountInfo
import com.tgaetaprojects.settledown.api.GameStatus
import com.tgaetaprojects.settledown.api.GameSummary
import com.tgaetaprojects.settledown.api.RoomInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Which online screen is showing. */
sealed interface OnlineScreen {
    data object Loading : OnlineScreen
    data object SignIn : OnlineScreen
    data class Lobby(val account: AccountInfo, val games: List<GameSummary>) : OnlineScreen
    data class Waiting(val room: RoomInfo) : OnlineScreen
    data class Playing(val session: NetworkGameSession) : OnlineScreen
}

/**
 * The online side of the app: signing in, finding a game and joining it.
 *
 * @param api must already point at the server in [config].
 */
class OnlineModel(
    private val config: OnlineConfig,
    parentScope: CoroutineScope,
    private val api: ServerApi = ServerApi(config.serverUrl),
) {
    /** Ends with [close], so nothing still in flight can touch a closed client or a discarded model. */
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    var screen: OnlineScreen by mutableStateOf(OnlineScreen.Loading)
        private set

    /** What went wrong with the last request, written for the player. */
    var error: String? by mutableStateOf(null)
        private set

    private var requestsRunning by mutableStateOf(0)

    val isBusy: Boolean get() = requestsRunning > 0

    private var roomPolling: Job? = null

    /** Picks up where this device left off: signed in or not, waiting in a room or not. */
    fun open(auth: PlatformAuth) = request(auth) {
        val saved = auth.loadSessionToken()
        if (saved == null) {
            screen = OnlineScreen.SignIn
            return@request
        }
        api.sessionToken = saved
        showHome(auth)
    }

    fun signIn(auth: PlatformAuth) = request(auth) {
        val idToken = config.devUser?.let { "dev:$it" } ?: auth.googleIdToken(config)
        val session = api.signIn(idToken)
        api.sessionToken = session.token
        auth.saveSessionToken(session.token)
        showHome(auth)
    }

    fun rename(auth: PlatformAuth, displayName: String) = request(auth) {
        val account = api.rename(displayName)
        (screen as? OnlineScreen.Lobby)?.let { screen = it.copy(account = account) }
    }

    fun createRoom(auth: PlatformAuth, seats: Int) = request(auth) { wait(auth, api.createRoom(seats)) }

    fun joinRoom(auth: PlatformAuth, code: String) = request(auth) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return@request
        wait(auth, api.joinRoom(trimmed))
    }

    fun leaveRoom(auth: PlatformAuth) = request(auth) {
        val room = (screen as? OnlineScreen.Waiting)?.room ?: return@request
        // polling stops only once this works: if it fails, the player is still in the room and must hear when it fills
        api.leaveRoom(room.code)
        roomPolling?.cancel()
        showHome(auth)
    }

    fun openGame(auth: PlatformAuth, gameId: Long) = request(auth) { play(auth, gameId) }

    /** Goes back to the lobby. The seat stays yours and you can come back, though the timer plays it meanwhile. */
    fun leaveGame(auth: PlatformAuth) = request(auth) {
        // the session stops only once this works: if it fails, the game on screen is still being played
        val session = (screen as? OnlineScreen.Playing)?.session
        showHome(auth)
        session?.stop()
    }

    fun signOut(auth: PlatformAuth) = request(auth) {
        api.signOut()
        forgetSession(auth)
    }

    /** Deletes the account for good. Past games keep the seat, with no name attached. */
    fun deleteAccount(auth: PlatformAuth) = request(auth) {
        api.deleteAccount()
        forgetSession(auth)
    }

    /** Leaves online play altogether: closes the socket and stops polling. */
    fun close() {
        scope.cancel()
        api.close()
    }

    private suspend fun showHome(auth: PlatformAuth) {
        val room = api.currentRoom()
        if (room != null) {
            screen = OnlineScreen.Waiting(room)
            startPolling(room, auth)
        } else {
            screen = OnlineScreen.Lobby(api.account(), api.myGames())
        }
    }

    private suspend fun wait(auth: PlatformAuth, room: RoomInfo) {
        val gameId = room.gameId
        if (gameId != null) {
            play(auth, gameId)
        } else {
            screen = OnlineScreen.Waiting(room)
            startPolling(room, auth)
        }
    }

    private fun startPolling(room: RoomInfo, auth: PlatformAuth) {
        roomPolling?.cancel()
        roomPolling = scope.launch {
            while (true) {
                delay(POLL_INTERVAL)
                try {
                    val latest = api.room(room.code)
                    val gameId = latest.gameId
                    if (gameId != null) {
                        request(auth) { play(auth, gameId) }
                        return@launch
                    }
                    screen = OnlineScreen.Waiting(latest)
                    error = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    // the room expired, or the session did
                    if (e.status == UNAUTHORIZED) forgetSession(auth) else request(auth) { showHome(auth) }
                    return@launch
                } catch (_: Exception) {
                    error = NETWORK_ERROR // keep trying: the network may well come back
                }
            }
        }
    }

    private suspend fun play(auth: PlatformAuth, gameId: Long) {
        val game = api.myGames().firstOrNull { it.id == gameId }
            ?: throw ApiException(404, "no_such_game", "That game isn't available.")
        val names = game.seats.mapNotNull { seat -> seat.displayName?.let { seat.color to it } }.toMap()
        val session = NetworkGameSession(
            api.transport(gameId),
            names,
            scope,
            onSessionExpired = { forgetSession(auth) },
        )
        (screen as? OnlineScreen.Playing)?.session?.stop()
        session.start()
        screen = OnlineScreen.Playing(session)
    }

    private fun forgetSession(auth: PlatformAuth) {
        roomPolling?.cancel()
        api.sessionToken = null
        auth.saveSessionToken(null)
        screen = OnlineScreen.SignIn
    }

    /** Runs one request, turning failures into [error] rather than crashes. */
    private fun request(auth: PlatformAuth, block: suspend () -> Unit) {
        scope.launch {
            requestsRunning++
            error = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: SignInCancelledException) {
                if (screen == OnlineScreen.Loading) screen = OnlineScreen.SignIn
            } catch (e: SignInFailedException) {
                error = e.message
                if (screen == OnlineScreen.Loading) screen = OnlineScreen.SignIn
            } catch (e: ApiException) {
                if (e.status == UNAUTHORIZED) forgetSession(auth)
                if (e.status != UNAUTHORIZED || e.code == "invalid_token") error = e.message
            } catch (_: Exception) {
                error = NETWORK_ERROR
                if (screen == OnlineScreen.Loading) screen = OnlineScreen.SignIn
            } finally {
                requestsRunning--
            }
        }
    }

    private companion object {
        val POLL_INTERVAL = 2.seconds
        const val UNAUTHORIZED = 401
        const val NETWORK_ERROR = "Can't reach the server. Check your connection and try again."
    }
}

/** Whether a game can still be opened: a finished one still shows its result. */
val GameSummary.canOpen: Boolean get() = status == GameStatus.ACTIVE || status == GameStatus.FINISHED
