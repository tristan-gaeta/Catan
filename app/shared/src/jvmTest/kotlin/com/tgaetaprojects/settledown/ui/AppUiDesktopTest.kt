package com.tgaetaprojects.settledown.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.tgaetaprojects.settledown.App
import com.tgaetaprojects.settledown.AppViewModel
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.ui.game.BoardGeometry
import com.tgaetaprojects.settledown.ui.game.BuildTool
import com.tgaetaprojects.settledown.ui.game.GameScreen
import com.tgaetaprojects.settledown.ui.game.GameSession
import com.tgaetaprojects.settledown.ui.game.label
import com.tgaetaprojects.settledown.ui.home.HomeScreen
import com.tgaetaprojects.settledown.ui.theme.SettledownTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppUiDesktopTest {

    @Test
    fun startGame_fromHome_showsFirstRoll() = runDesktopComposeUiTest(DESKTOP_WIDTH, DESKTOP_HEIGHT) {
        val model = AppViewModel()
        setContent { App(model) }

        onNodeWithTag("players-3").performClick()
        onNodeWithTag("start").performClick()

        onNodeWithTag("prompt").assertTextContains("roll to decide who goes first", substring = true)
        assertEquals(3, model.session!!.view.players.size)
    }

    @Test
    fun boardTap_onHighlightedVertex_placesSettlement() = runDesktopComposeUiTest(DESKTOP_WIDTH, DESKTOP_HEIGHT) {
        val session = GameSession.new(players = 4, boardSeed = 11L, gameSeed = 12L)
        setContent { SettledownTheme(darkTheme = false) { GameScreen(session, onNewGame = {}) } }
        while (session.phase is Phase.RollForFirstPlayer) onNodeWithText("Roll dice").performClick()
        onNodeWithTag("reveal").performClick()

        val vertex = session.targets().vertices.first()
        onNodeWithTag("board").performTouchInput {
            val scale = minOf(width / (2 * BoardGeometry.HALF_WIDTH), height / (2 * BoardGeometry.HALF_HEIGHT))
            click(center + BoardGeometry.position(vertex) * scale)
        }

        assertTrue(vertex in session.view.buildings)
        onNodeWithTag("prompt").assertTextContains("place a free road", substring = true)
    }

    @Test
    fun setup_newPlayer_coversCardsUntilRevealed() = runDesktopComposeUiTest(DESKTOP_WIDTH, DESKTOP_HEIGHT) {
        val session = GameSession.new(players = 3, boardSeed = 11L, gameSeed = 12L)
        setContent { SettledownTheme(darkTheme = false) { GameScreen(session, onNewGame = {}) } }
        while (session.phase is Phase.RollForFirstPlayer) onNodeWithText("Roll dice").performClick()

        onNodeWithTag("handOffPlayer").assertTextEquals(session.actor.label)
        onNodeWithTag("prompt").assertDoesNotExist()
        Screenshots.save(onRoot().captureToImage(), "desktop-handoff")

        onNodeWithTag("reveal").performClick()
        onNodeWithTag("prompt").assertIsDisplayed()
    }

    @Test
    fun newGame_inProgress_asksFirst() = runDesktopComposeUiTest(DESKTOP_WIDTH, DESKTOP_HEIGHT) {
        var left = false
        setContent { SettledownTheme(darkTheme = false) { GameScreen(GameSession.demo(), onNewGame = { left = true }) } }

        onNodeWithText("New game").performClick()
        assertFalse(left)
        onNodeWithText("Leave").performClick()
        assertTrue(left)
    }

    @Test
    fun home_screenshot() = runDesktopComposeUiTest(DESKTOP_WIDTH, DESKTOP_HEIGHT) {
        setContent {
            SettledownTheme(darkTheme = false) {
                HomeScreen(players = 4, boardSeed = 2026L, onPlayersChange = {}, onShuffleBoard = {}, onStart = {})
            }
        }
        onNodeWithText("Start game").assertIsDisplayed()
        Screenshots.save(onRoot().captureToImage(), "desktop-home")
    }

    @Test
    fun game_screenshot() = runDesktopComposeUiTest(DESKTOP_WIDTH, DESKTOP_HEIGHT) {
        val session = GameSession.demo().apply { selectTool(BuildTool.ROAD) }
        setContent { SettledownTheme(darkTheme = false) { GameScreen(session, onNewGame = {}) } }
        onNodeWithTag("endTurn").assertIsDisplayed()
        Screenshots.save(onRoot().captureToImage(), "desktop-game")
    }

    @Test
    fun game_darkScreenshot() = runDesktopComposeUiTest(DESKTOP_WIDTH, DESKTOP_HEIGHT) {
        val session = GameSession.demo().apply { selectTool(BuildTool.ROAD) }
        setContent { SettledownTheme(darkTheme = true) { GameScreen(session, onNewGame = {}) } }
        onNodeWithTag("endTurn").assertIsDisplayed()
        Screenshots.save(onRoot().captureToImage(), "desktop-game-dark")
    }

    @Test
    fun home_compactScreenshot() = runDesktopComposeUiTest(SMALL_WIDTH, SMALL_HEIGHT) {
        setContent {
            SettledownTheme(darkTheme = false) {
                HomeScreen(players = 4, boardSeed = 2026L, onPlayersChange = {}, onShuffleBoard = {}, onStart = {})
            }
        }
        onNodeWithTag("start").assertIsDisplayed()
        Screenshots.save(onRoot().captureToImage(), "desktop-home-small")
    }

    @Test
    fun game_compactScreenshot() = runDesktopComposeUiTest(COMPACT_WIDTH, COMPACT_HEIGHT) {
        val session = GameSession.demo().apply { selectTool(BuildTool.ROAD) }
        setContent { SettledownTheme(darkTheme = false) { GameScreen(session, onNewGame = {}) } }
        onNodeWithTag("board").assertIsDisplayed()
        Screenshots.save(onRoot().captureToImage(), "desktop-game-compact")
    }

    private companion object {
        const val DESKTOP_WIDTH = 1280
        const val DESKTOP_HEIGHT = 820
        // a phone-sized window, to check the stacked layout on the desktop renderer too
        const val COMPACT_WIDTH = 412
        const val COMPACT_HEIGHT = 900
        // the smallest phone CI emulates: 360x640dp
        const val SMALL_WIDTH = 360
        const val SMALL_HEIGHT = 640
    }
}
