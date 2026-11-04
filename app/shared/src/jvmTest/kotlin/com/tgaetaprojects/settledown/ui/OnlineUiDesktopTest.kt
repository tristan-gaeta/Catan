package com.tgaetaprojects.settledown.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import com.tgaetaprojects.settledown.App
import com.tgaetaprojects.settledown.AppViewModel
import com.tgaetaprojects.settledown.online.LocalGameServer
import com.tgaetaprojects.settledown.online.MemoryAuth
import com.tgaetaprojects.settledown.online.OnlineConfig
import com.tgaetaprojects.settledown.online.OnlineModel
import com.tgaetaprojects.settledown.online.OnlineScreen
import com.tgaetaprojects.settledown.ui.online.OnlineFlow
import com.tgaetaprojects.settledown.ui.theme.SettledownTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The online screens, clicked through against the real server. */
@OptIn(ExperimentalTestApi::class)
class OnlineUiDesktopTest {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val server = LocalGameServer()

    @AfterTest
    fun stopServer() {
        models.forEach { it.close() }
        scope.cancel()
        server.stop()
    }

    private val models = mutableListOf<OnlineModel>()

    private fun model(name: String) =
        OnlineModel(OnlineConfig("http://127.0.0.1:${server.port}", devUser = name), scope).also { models += it }

    @Test
    fun home_noOnlineConfig_hidesPlayOnline() = runDesktopComposeUiTest(1280, 820) {
        setContent { App(AppViewModel()) }
        onNodeWithTag("start").assertIsDisplayed()
        onNodeWithTag("playOnline").assertDoesNotExist()
    }

    @Test
    fun home_onlineConfig_showsPlayOnline() = runDesktopComposeUiTest(1280, 820) {
        setContent { App(AppViewModel(), onlineConfig = OnlineConfig("http://127.0.0.1:${server.port}")) }
        onNodeWithTag("playOnline").assertIsDisplayed()
    }

    @Test
    fun signIn_thenCreateRoom_showsTheCode() = runDesktopComposeUiTest(1280, 820) {
        val model = model("alice")
        val auth = MemoryAuth()
        model.open(auth)
        setContent { SettledownTheme(darkTheme = false) { OnlineFlow(model, auth, onExit = {}) } }

        waitUntilExactlyOneExists(hasTestTag("signIn"), TIMEOUT)
        onNodeWithTag("signIn").performClick()
        waitUntilExactlyOneExists(hasTestTag("createRoom-3"), TIMEOUT)
        onNodeWithTag("createRoom-3").performClick()
        waitUntilExactlyOneExists(hasTestTag("roomCodeDisplay"), TIMEOUT)

        val room = assertIs<OnlineScreen.Waiting>(model.screen).room
        assertEquals(3, room.seats)
        onNodeWithTag("roomCodeDisplay").assertIsDisplayed()
    }

    @Test
    fun joinRoom_unknownCode_showsTheError() = runDesktopComposeUiTest(1280, 820) {
        val model = model("bob")
        val auth = MemoryAuth()
        model.open(auth)
        setContent { SettledownTheme(darkTheme = false) { OnlineFlow(model, auth, onExit = {}) } }

        waitUntilExactlyOneExists(hasTestTag("signIn"), TIMEOUT)
        onNodeWithTag("signIn").performClick()
        waitUntilExactlyOneExists(hasTestTag("roomCode"), TIMEOUT)
        onNodeWithTag("roomCode").performScrollTo().performTextInput("ZZZZZZ")
        onNodeWithTag("joinRoom").performClick()

        waitUntilExactlyOneExists(hasTestTag("onlineError"), TIMEOUT)
    }

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
