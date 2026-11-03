package com.tgaetaprojects.settledown

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.ui.game.BuildTool
import com.tgaetaprojects.settledown.ui.game.GameScreen
import com.tgaetaprojects.settledown.ui.game.GameSession
import com.tgaetaprojects.settledown.ui.theme.SettledownTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A game in progress on a real screen, in both themes, saved as screenshots for review. */
@RunWith(AndroidJUnit4::class)
class GameScreenDeviceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun drawLikeTheApp() {
        composeRule.runOnUiThread { composeRule.activity.drawEdgeToEdge() }
    }

    @Test
    fun game_screenshot() {
        val session = GameSession.demo().apply { selectTool(BuildTool.ROAD) }
        composeRule.setContent { SettledownTheme(darkTheme = false) { GameScreen(session, onNewGame = {}) } }

        composeRule.onNodeWithTag("board").assertIsDisplayed()
        composeRule.onRoot().saveScreenshot("device-game")
    }

    @Test
    fun game_darkScreenshot() {
        val session = GameSession.demo().apply { selectTool(BuildTool.ROAD) }
        composeRule.setContent { SettledownTheme(darkTheme = true) { GameScreen(session, onNewGame = {}) } }

        composeRule.onNodeWithTag("board").assertIsDisplayed()
        composeRule.onRoot().saveScreenshot("device-game-dark")
    }

    @Test
    fun handOff_screenshot() {
        val session = GameSession.new(players = 4, boardSeed = 2026L, gameSeed = 7L)
        composeRule.setContent { SettledownTheme(darkTheme = false) { GameScreen(session, onNewGame = {}) } }
        val roll = composeRule.onNodeWithText("Roll dice")
        // a few rounds at most, even with ties
        repeat(40) { if (session.phase is Phase.RollForFirstPlayer) roll.performClick() }

        composeRule.onNodeWithTag("reveal").assertIsDisplayed()
        composeRule.onRoot().saveScreenshot("device-handoff")
    }
}
