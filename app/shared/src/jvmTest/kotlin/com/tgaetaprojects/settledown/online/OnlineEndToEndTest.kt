package com.tgaetaprojects.settledown.online

import com.tgaetaprojects.settledown.api.GameStatus
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Keeps the session token in memory, so each player in a test is their own device. */
internal class MemoryAuth : PlatformAuth {
    var saved: String? = null
    override suspend fun googleIdToken(config: OnlineConfig): String = error("Dev auth doesn't use Google")
    override fun loadSessionToken(): String? = saved
    override fun saveSessionToken(token: String?) {
        saved = token
    }
}

/** The app's real online code, against the real server, over real sockets on this machine. */
class OnlineEndToEndTest {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val server = LocalGameServer()
    private val port get() = server.port

    @AfterTest
    fun stopServer() {
        scope.cancel()
        server.stop()
    }

    private class Player(val model: OnlineModel, val auth: MemoryAuth)

    private fun player(name: String): Player {
        val config = OnlineConfig("http://127.0.0.1:$port", devUser = name)
        return Player(OnlineModel(config, scope), MemoryAuth())
    }

    private suspend fun Player.signIn() {
        model.signIn(auth)
        waitFor { model.screen is OnlineScreen.Lobby }
    }

    private suspend fun waitFor(condition: () -> Boolean) {
        while (!condition()) delay(5)
    }

    private fun e2e(block: suspend () -> Unit) = runBlocking { withTimeout(2.minutes) { block() } }

    /** Fills a room for three, returning each player's session once the game has reached them. */
    private suspend fun startGame(players: List<Player>): List<NetworkGameSession> {
        players.forEach { it.signIn() }
        players[0].model.createRoom(players[0].auth, 3)
        waitFor { players[0].model.screen is OnlineScreen.Waiting }
        val code = (players[0].model.screen as OnlineScreen.Waiting).room.code
        for (player in players.drop(1)) player.model.joinRoom(player.auth, code)
        waitFor { players.all { it.model.screen is OnlineScreen.Playing } }
        val sessions = players.map { (it.model.screen as OnlineScreen.Playing).session }
        waitFor { sessions.all { it.isReady } }
        return sessions
    }

    /** One legal move from what this seat can see, by the same tap and button paths the screen uses. */
    private fun NetworkGameSession.move() {
        when (val phase = phase) {
            is Phase.RollForFirstPlayer, Phase.PreRoll -> act(Action.RollDice)
            is Phase.SetupSettlement -> tapVertex(targets().vertices.first())
            is Phase.SetupRoad, is Phase.RoadBuilding -> tapEdge(targets().edges.first())
            is Phase.Discard -> act(Action.Discard(view.hand.firstCards(phase.pending.getValue(actor))))
            is Phase.MoveRobber -> {
                val hex = Hex.entries.first { it != view.robber }
                act(Action.MoveRobber(hex, robberVictims(hex).firstOrNull()))
            }

            Phase.Main -> act(Action.EndTurn)
            is Phase.GameOver -> {}
        }
    }

    /** Plays every seat until the game reaches [turn]. A move is only made on a view newer than the last one acted on. */
    private suspend fun playUntilTurn(sessions: List<NetworkGameSession>, turn: Int) {
        val actedOn = HashMap<NetworkGameSession, Any>()
        while (sessions.any { it.view.turn < turn }) {
            for (session in sessions) {
                val view = session.view
                if (session.canAct && actedOn[session] !== view) {
                    actedOn[session] = view
                    session.move()
                }
                assertNull(session.refusal?.violation, "${session.actor} was refused")
            }
            delay(1)
        }
    }

    @Test
    fun signIn_devUser_reachesTheLobbyAndSavesTheSession() = e2e {
        val alice = player("alice")
        alice.signIn()
        assertNotNull(alice.auth.saved)
        assertEquals("Player 1", (alice.model.screen as OnlineScreen.Lobby).account.displayName)
    }

    @Test
    fun open_savedSession_skipsSigningIn() = e2e {
        val alice = player("alice")
        alice.signIn()
        val again = Player(OnlineModel(OnlineConfig("http://127.0.0.1:$port"), scope), alice.auth)
        again.model.open(again.auth)
        waitFor { again.model.screen is OnlineScreen.Lobby }
    }

    @Test
    fun open_badSavedSession_asksToSignIn() = e2e {
        val alice = player("alice")
        alice.auth.saved = "not-a-real-session"
        alice.model.open(alice.auth)
        waitFor { alice.model.screen == OnlineScreen.SignIn }
        assertNull(alice.auth.saved)
    }

    @Test
    fun rename_showsInTheLobby() = e2e {
        val alice = player("alice")
        alice.signIn()
        alice.model.rename(alice.auth, "Alice")
        waitFor { (alice.model.screen as? OnlineScreen.Lobby)?.account?.displayName == "Alice" }
    }

    @Test
    fun joinRoom_unknownCode_showsTheServersMessage() = e2e {
        val alice = player("alice")
        alice.signIn()
        alice.model.joinRoom(alice.auth, "ZZZZZZ")
        waitFor { alice.model.error != null }
        assertEquals("There's no room with that code", alice.model.error)
        assertIs<OnlineScreen.Lobby>(alice.model.screen)
    }

    @Test
    fun leaveRoom_goesBackToTheLobby() = e2e {
        val alice = player("alice")
        alice.signIn()
        alice.model.createRoom(alice.auth, 3)
        waitFor { alice.model.screen is OnlineScreen.Waiting }
        alice.model.leaveRoom(alice.auth)
        waitFor { alice.model.screen is OnlineScreen.Lobby }
    }

    @Test
    fun game_threePlayers_stayInStepForManyTurns() = e2e {
        val players = listOf(player("alice"), player("bob"), player("carol"))
        val sessions = startGame(players)

        playUntilTurn(sessions, turn = 12)

        waitFor { sessions.map { it.view.turn }.toSet().size == 1 && sessions.map { it.view.currentPlayer }.toSet().size == 1 }
        assertEquals(3, sessions.map { it.actor }.toSet().size)
        assertEquals(1, sessions.map { it.view.roads }.toSet().size, "Every seat must see the same board")
        for (session in sessions) {
            // each seat holds its own cards, and only counts of everyone else's
            assertEquals(session.view.hand.total, session.view.summary(session.actor).handSize)
            assertTrue(session.log.isNotEmpty())
        }
    }

    @Test
    fun game_leavingAndRejoining_picksUpTheSameGame() = e2e {
        val players = listOf(player("alice"), player("bob"), player("carol"))
        val sessions = startGame(players)
        playUntilTurn(sessions, turn = 2)
        val alice = players[0]
        val turn = sessions[0].view.turn

        alice.model.leaveGame(alice.auth)
        waitFor { alice.model.screen is OnlineScreen.Lobby }
        val game = (alice.model.screen as OnlineScreen.Lobby).games.single()
        assertEquals(GameStatus.ACTIVE, game.status)
        alice.model.openGame(alice.auth, game.id)
        waitFor { alice.model.screen is OnlineScreen.Playing }

        val rejoined = (alice.model.screen as OnlineScreen.Playing).session
        waitFor { rejoined.isReady }
        assertEquals(sessions[0].actor, rejoined.actor)
        assertTrue(rejoined.view.turn >= turn)
    }

    @Test
    fun sessionRevokedDuringAGame_returnsToSignIn() = e2e {
        val players = listOf(player("alice"), player("bob"), player("carol"))
        startGame(players)
        val alice = players[0]

        // signing out from another device ends the session this one is using
        val otherDevice = ServerApi("http://127.0.0.1:$port")
        otherDevice.sessionToken = alice.auth.saved
        otherDevice.signOut()
        otherDevice.close()

        waitFor { alice.model.screen == OnlineScreen.SignIn }
        assertNull(alice.auth.saved)
    }

    @Test
    fun leaveRoom_serverDown_staysInTheRoomWithAMessage() = e2e {
        val alice = player("alice")
        alice.signIn()
        alice.model.createRoom(alice.auth, 3)
        waitFor { alice.model.screen is OnlineScreen.Waiting }

        server.stop()
        alice.model.leaveRoom(alice.auth)

        waitFor { alice.model.error != null && !alice.model.isBusy }
        assertIs<OnlineScreen.Waiting>(alice.model.screen)
    }

    @Test
    fun close_duringARequest_leavesTheModelAlone() = e2e {
        val alice = player("alice")
        alice.signIn()
        val before = alice.model.screen
        alice.model.createRoom(alice.auth, 3)
        alice.model.close()
        delay(100)
        assertEquals(before, alice.model.screen)
    }

    @Test
    fun deleteAccount_returnsToSignInAndForgetsTheSession() = e2e {
        val alice = player("alice")
        alice.signIn()
        alice.model.deleteAccount(alice.auth)
        waitFor { alice.model.screen == OnlineScreen.SignIn }
        assertNull(alice.auth.saved)
    }

    @Test
    fun signOut_returnsToSignIn() = e2e {
        val alice = player("alice")
        alice.signIn()
        alice.model.signOut(alice.auth)
        waitFor { alice.model.screen == OnlineScreen.SignIn }
        assertNull(alice.auth.saved)
    }

    @Test
    fun serverUnreachable_showsAMessageAndAllowsSigningIn() = e2e {
        val model = OnlineModel(OnlineConfig("http://127.0.0.1:1", devUser = "alice"), scope)
        val auth = MemoryAuth().also { it.saved = "token" }
        model.open(auth)
        withTimeout(30.seconds) { waitFor { model.error != null } }
        assertEquals(OnlineScreen.SignIn, model.screen)
        // the saved session may be fine; only the network failed
        assertEquals("token", auth.saved)
    }
}
