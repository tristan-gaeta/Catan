package com.tgaetaprojects.settledown.api

import com.tgaetaprojects.settledown.game.PlayerColor
import kotlinx.serialization.Serializable

/*
 * The JSON bodies of the server's account, room and game-list requests, shared by the server and the apps. Live game
 * messages are binary instead; see WireCodec. JSON writes enums by name, so these don't affect FORMAT_VERSION.
 */

@Serializable
data class GoogleSignInRequest(val idToken: String)

@Serializable
data class AccountInfo(val id: Long, val displayName: String)

/** [token] is the session's bearer token; only this response ever contains it. */
@Serializable
data class SignInResponse(val token: String, val account: AccountInfo, val isNewAccount: Boolean)

@Serializable
data class UpdateAccountRequest(val displayName: String)

@Serializable
data class CreateRoomRequest(val seats: Int)

@Serializable
data class RoomMember(val accountId: Long, val displayName: String)

/** A room waiting to fill. The game starts the moment it's full, and [gameId] then names it. */
@Serializable
data class RoomInfo(
    val code: String,
    val seats: Int,
    val members: List<RoomMember>,
    val gameId: Long? = null,
)

@Serializable
enum class GameStatus {
    ACTIVE,
    FINISHED,

    /** Everyone left, so the server stopped playing it. */
    ABANDONED,

    /** It can't continue on this version of the server. */
    UNAVAILABLE,
}

/** [displayName] is `null` for a player who deleted their account. */
@Serializable
data class SeatInfo(val color: PlayerColor, val displayName: String?, val isYou: Boolean)

@Serializable
data class GameSummary(val id: Long, val status: GameStatus, val seats: List<SeatInfo>)

@Serializable
data class ApiError(val code: String, val message: String)
