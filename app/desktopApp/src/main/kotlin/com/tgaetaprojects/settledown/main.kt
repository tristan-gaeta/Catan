package com.tgaetaprojects.settledown

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tgaetaprojects.settledown.online.OnlineConfig
import java.awt.Dimension

/** Online play stays hidden unless `SETTLEDOWN_SERVER_URL` is set. */
private val onlineConfig = System.getenv("SETTLEDOWN_SERVER_URL")?.let { url ->
    OnlineConfig(
        serverUrl = url,
        googleClientId = System.getenv("SETTLEDOWN_GOOGLE_CLIENT_ID"),
        googleClientSecret = System.getenv("SETTLEDOWN_GOOGLE_CLIENT_SECRET"),
        devUser = System.getenv("SETTLEDOWN_DEV_USER"),
    )
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Settledown",
        state = rememberWindowState(width = 1280.dp, height = 820.dp),
    ) {
        window.minimumSize = Dimension(640, 560)
        App(onlineConfig = onlineConfig)
    }
}
