package com.tgaetaprojects.biophilia

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Biophilia",
    ) {
        App()
    }
}