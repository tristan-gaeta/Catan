package com.tgaetaprojects.settledown

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppUiDesktopTest {

    @Test
    fun clickingButtonShowsGreeting() = runComposeUiTest {
        setContent { App() }

        onNodeWithText("Click me!").performClick()
        onNodeWithText("Compose: ${Greeting().greet()}").assertIsDisplayed()
    }
}
