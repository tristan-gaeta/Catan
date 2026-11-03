package com.tgaetaprojects.settledown

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tgaetaprojects.settledown.ui.game.BuildTool
import com.tgaetaprojects.settledown.ui.game.GameScreen
import com.tgaetaprojects.settledown.ui.game.GameSession
import com.tgaetaprojects.settledown.ui.theme.SettledownTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A game in progress on a real screen, in both themes, saved as screenshots for review. */
@RunWith(AndroidJUnit4::class)
class GameScreenDeviceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun game_screenshot() {
        val session = GameSession.demo().apply { tool = BuildTool.ROAD }
        composeRule.setContent { SettledownTheme(darkTheme = false) { GameScreen(session, onNewGame = {}) } }

        composeRule.onNodeWithTag("board").assertIsDisplayed()
        composeRule.onRoot().saveScreenshot("device-game")
    }

    @Test
    fun game_darkScreenshot() {
        val session = GameSession.demo().apply { tool = BuildTool.SETTLEMENT }
        composeRule.setContent { SettledownTheme(darkTheme = true) { GameScreen(session, onNewGame = {}) } }

        composeRule.onNodeWithTag("board").assertIsDisplayed()
        composeRule.onRoot().saveScreenshot("device-game-dark")
    }
}
