package com.tgaetaprojects.settledown

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_showsHome() {
        composeRule.onNodeWithText("Settledown").assertIsDisplayed()
        composeRule.onNodeWithTag("start").assertIsDisplayed()
        composeRule.onRoot().saveScreenshot("device-home")
    }

    @Test
    fun startGame_showsFirstRoll() {
        composeRule.onNodeWithTag("start").performClick()

        composeRule.onNodeWithTag("prompt").assertTextContains("roll to decide who goes first", substring = true)
        composeRule.onNodeWithTag("board").assertIsDisplayed()
        composeRule.onRoot().saveScreenshot("device-game-start")
    }
}
