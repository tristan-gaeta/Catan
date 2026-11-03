package com.tgaetaprojects.settledown

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Settledown",
        state = rememberWindowState(width = 1280.dp, height = 820.dp),
    ) {
        window.minimumSize = Dimension(640, 560)
        App()
    }
}
