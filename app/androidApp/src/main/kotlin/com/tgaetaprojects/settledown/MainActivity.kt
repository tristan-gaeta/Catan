package com.tgaetaprojects.settledown

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tgaetaprojects.settledown.online.OnlineConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        drawEdgeToEdge()

        setContent {
            App(onlineConfig = onlineConfig())
        }
    }
}

private fun onlineConfig(): OnlineConfig? =
    BuildConfig.SERVER_URL.takeIf { it.isNotEmpty() }?.let { url ->
        OnlineConfig(
            serverUrl = url,
            googleClientId = BuildConfig.GOOGLE_CLIENT_ID.takeIf { it.isNotEmpty() },
            devUser = BuildConfig.DEV_USER.takeIf { it.isNotEmpty() },
        )
    }

/**
 * Draws behind both system bars with no scrim; the game screen pads its panel for the navigation bar. Device tests
 * call this on their host activity too, so their screenshots match the app.
 */
internal fun ComponentActivity.drawEdgeToEdge() {
    enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}