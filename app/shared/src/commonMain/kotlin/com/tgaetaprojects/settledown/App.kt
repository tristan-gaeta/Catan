package com.tgaetaprojects.settledown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tgaetaprojects.settledown.online.OnlineConfig
import com.tgaetaprojects.settledown.online.OnlineModel
import com.tgaetaprojects.settledown.online.PlatformAuth
import com.tgaetaprojects.settledown.online.rememberPlatformAuth
import com.tgaetaprojects.settledown.ui.game.GameScreen
import com.tgaetaprojects.settledown.ui.game.GameSession
import com.tgaetaprojects.settledown.ui.home.HomeScreen
import com.tgaetaprojects.settledown.ui.online.OnlineFlow
import com.tgaetaprojects.settledown.ui.theme.SettledownTheme
import kotlin.random.Random

/**
 * Holds the game across configuration changes such as rotation.
 *
 * Every seat is on this device, so the session already holds every secret and the seeds have nothing to hide;
 * [Random.Default] is enough. A networked game draws its seeds on the server instead.
 */
class AppViewModel : ViewModel() {
    var players by mutableIntStateOf(4)
    var boardSeed by mutableLongStateOf(Random.nextLong())
        private set
    var session by mutableStateOf<GameSession?>(null)
        private set

    fun shuffleBoard() {
        boardSeed = Random.nextLong()
    }

    fun start() {
        session = GameSession.new(players, boardSeed, gameSeed = Random.nextLong())
    }

    fun quit() {
        session = null
        shuffleBoard()
    }

    /** The online side, while the player is in it. */
    var online by mutableStateOf<OnlineModel?>(null)
        private set

    fun playOnline(config: OnlineConfig, auth: PlatformAuth) {
        online = OnlineModel(config, viewModelScope).also { it.open(auth) }
    }

    fun leaveOnline() {
        online?.close()
        online = null
    }

    override fun onCleared() {
        online?.close()
    }
}

@Composable
@Preview
fun App(model: AppViewModel = viewModel { AppViewModel() }, onlineConfig: OnlineConfig? = null) {
    SettledownTheme {
        val session = model.session
        val online = model.online
        val auth = rememberPlatformAuth()
        if (online != null) {
            OnlineFlow(online, auth, onExit = model::leaveOnline)
        } else if (session == null) {
            HomeScreen(
                players = model.players,
                boardSeed = model.boardSeed,
                onPlayersChange = { model.players = it },
                onShuffleBoard = model::shuffleBoard,
                onStart = model::start,
                onPlayOnline = onlineConfig?.let { config -> { model.playOnline(config, auth) } },
            )
        } else {
            GameScreen(session, onNewGame = model::quit)
        }
    }
}
