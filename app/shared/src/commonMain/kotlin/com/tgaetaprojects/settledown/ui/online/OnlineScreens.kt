package com.tgaetaprojects.settledown.ui.online

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tgaetaprojects.settledown.api.AccountInfo
import com.tgaetaprojects.settledown.api.GameStatus
import com.tgaetaprojects.settledown.api.GameSummary
import com.tgaetaprojects.settledown.api.RoomInfo
import com.tgaetaprojects.settledown.online.NetworkGameSession
import com.tgaetaprojects.settledown.online.OnlineModel
import com.tgaetaprojects.settledown.online.OnlineScreen
import com.tgaetaprojects.settledown.online.PlatformAuth
import com.tgaetaprojects.settledown.online.canOpen
import com.tgaetaprojects.settledown.ui.game.GameScreen

/** Whichever online screen [model] is on. [onExit] goes back to the app's home screen. */
@Composable
fun OnlineFlow(model: OnlineModel, auth: PlatformAuth, onExit: () -> Unit) {
    when (val screen = model.screen) {
        OnlineScreen.Loading -> Centered { CircularProgressIndicator() }
        OnlineScreen.SignIn -> SignInScreen(
            error = model.error,
            isBusy = model.isBusy,
            onSignIn = { model.signIn(auth) },
            onBack = onExit,
        )

        is OnlineScreen.Lobby -> LobbyScreen(
            account = screen.account,
            games = screen.games,
            error = model.error,
            isBusy = model.isBusy,
            onRename = { model.rename(auth, it) },
            onCreateRoom = { model.createRoom(auth, it) },
            onJoinRoom = { model.joinRoom(auth, it) },
            onOpenGame = { model.openGame(auth, it) },
            onSignOut = { model.signOut(auth) },
            onDeleteAccount = { model.deleteAccount(auth) },
            onBack = onExit,
        )

        is OnlineScreen.Waiting -> WaitingRoomScreen(
            room = screen.room,
            error = model.error,
            onLeave = { model.leaveRoom(auth) },
        )

        is OnlineScreen.Playing -> PlayingScreen(screen.session, onLeave = { model.leaveGame(auth) })
    }
}

@Composable
private fun PlayingScreen(session: NetworkGameSession, onLeave: () -> Unit) {
    if (session.isReady) {
        GameScreen(session, onNewGame = onLeave, leaveLabel = "Leave", leaveWarning = LEAVE_WARNING)
    } else {
        Centered {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val notice = session.notice) {
                    null -> CircularProgressIndicator()
                    else -> Text(notice, textAlign = TextAlign.Center, modifier = Modifier.testTag("sessionNotice"))
                }
                OutlinedButton(onClick = onLeave) { Text("Back") }
            }
        }
    }
}

private const val LEAVE_WARNING =
    "You can come back from the game list. While you're away, the timer moves for you when your time runs out."

@Composable
private fun SignInScreen(error: String?, isBusy: Boolean, onSignIn: () -> Unit, onBack: () -> Unit) {
    Centered {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Play online", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Sign in to join a game with friends, and to come back to it from any of your devices.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            ErrorText(error)
            Button(onClick = onSignIn, enabled = !isBusy, modifier = Modifier.testTag("signIn")) {
                Text("Sign in with Google")
            }
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun LobbyScreen(
    account: AccountInfo,
    games: List<GameSummary>,
    error: String?,
    isBusy: Boolean,
    onRename: (String) -> Unit,
    onCreateRoom: (Int) -> Unit,
    onJoinRoom: (String) -> Unit,
    onOpenGame: (Long) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember(account.displayName) { mutableStateOf(account.displayName) }
    var code by remember { mutableStateOf("") }
    var confirmingDelete by remember { mutableStateOf(false) }
    Scrolling {
        Text("Play online", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        ErrorText(error)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("New game", style = MaterialTheme.typography.titleMedium)
                Text("Make a room, then share its code with your friends.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (seats in listOf(3, 4)) {
                        Button(
                            onClick = { onCreateRoom(seats) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("createRoom-$seats"),
                        ) { Text("$seats players") }
                    }
                }
                HorizontalDivider()
                Text("Have a code?", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OnlineTextField(
                        value = code,
                        onValueChange = { code = it.take(ROOM_CODE_LENGTH) },
                        placeholder = "Room code",
                        modifier = Modifier.weight(1f).testTag("roomCode"),
                    )
                    Button(
                        onClick = { onJoinRoom(code) },
                        enabled = !isBusy && code.isNotBlank(),
                        modifier = Modifier.testTag("joinRoom"),
                    ) { Text("Join") }
                }
            }
        }

        if (games.isNotEmpty()) {
            Text("Your games", style = MaterialTheme.typography.titleMedium)
            for (game in games) GameRow(game, isBusy, onOpenGame)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your name", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OnlineTextField(
                        value = name,
                        onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                        placeholder = "Your name",
                        modifier = Modifier.weight(1f).testTag("displayName"),
                    )
                    OutlinedButton(
                        onClick = { onRename(name) },
                        enabled = !isBusy && name.isNotBlank() && name != account.displayName,
                    ) { Text("Save") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSignOut, enabled = !isBusy) { Text("Sign out") }
                    TextButton(
                        onClick = { confirmingDelete = true },
                        enabled = !isBusy,
                        modifier = Modifier.testTag("deleteAccount"),
                    ) { Text("Delete account") }
                }
            }
        }
        TextButton(onClick = onBack) { Text("Back") }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    "This can't be undone. Your name comes off past games, and the timer plays your seat in " +
                            "any game still running."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmingDelete = false; onDeleteAccount() },
                    modifier = Modifier.testTag("confirmDelete"),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun GameRow(game: GameSummary, isBusy: Boolean, onOpen: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    game.seats.joinToString { it.displayName ?: "Deleted player" },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    when (game.status) {
                        GameStatus.ACTIVE -> "In progress"
                        GameStatus.FINISHED -> "Finished"
                        GameStatus.ABANDONED -> "Abandoned"
                        GameStatus.UNAVAILABLE -> "Unavailable on this version"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (game.canOpen) {
                Button(
                    onClick = { onOpen(game.id) },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("openGame-${game.id}"),
                ) { Text(if (game.status == GameStatus.ACTIVE) "Rejoin" else "View") }
            }
        }
    }
}

@Composable
private fun WaitingRoomScreen(room: RoomInfo, error: String?, onLeave: () -> Unit) {
    Centered {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Share this code", style = MaterialTheme.typography.titleLarge)
            Text(
                room.code,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("roomCodeDisplay"),
            )
            Text("${room.members.size} of ${room.seats} players are here. The game starts when everyone's in.")
            for (member in room.members) Text(member.displayName, style = MaterialTheme.typography.bodyLarge)
            ErrorText(error)
            CircularProgressIndicator()
            OutlinedButton(onClick = onLeave, modifier = Modifier.testTag("leaveRoom")) { Text("Leave") }
        }
    }
}

/**
 * A one-line text field built on foundation. Material's own text fields crash here with `AbstractMethodError`: the
 * pinned `material3` alpha was built against an older foundation than the project resolves.
 */
@Composable
private fun OnlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier,
        decorationBox = { field ->
            Box(
                Modifier.border(1.dp, colors.outline, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                field()
            }
        },
    )
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(
            error,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("onlineError"),
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 480.dp)) { content() }
        }
    }
}

@Composable
private fun Scrolling(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding().fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
        }
    }
}

private const val ROOM_CODE_LENGTH = 6
private const val MAX_NAME_LENGTH = 24
