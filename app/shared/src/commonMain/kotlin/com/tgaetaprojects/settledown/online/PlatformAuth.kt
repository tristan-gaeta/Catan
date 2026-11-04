package com.tgaetaprojects.settledown.online

import androidx.compose.runtime.Composable

/**
 * How to reach the server. Online play is hidden when an app has no config.
 *
 * @param googleClientId on Android the Web client ID, and on desktop the Desktop client ID.
 * @param googleClientSecret desktop only. Google's token endpoint asks for it, but in an installed app it isn't really
 *   secret; PKCE is what protects the sign-in.
 * @param devUser signs in as `dev:<name>` instead of using Google. The server only accepts that with its own dev auth
 *   switched on.
 */
data class OnlineConfig(
    val serverUrl: String,
    val googleClientId: String? = null,
    val googleClientSecret: String? = null,
    val devUser: String? = null,
)

/** The person closed the sign-in, which isn't an error worth reporting. */
class SignInCancelledException : Exception()

/** Signing in went wrong before the server was involved. [message] is written for the player. */
class SignInFailedException(override val message: String) : Exception(message)

/** The saved session isn't valid any more: it expired, or was signed out or deleted from another device. */
class SessionExpiredException : Exception()

/** The parts of signing in that differ by platform. */
interface PlatformAuth {
    /** Asks the person to sign in with Google, and returns the ID token for the server. */
    suspend fun googleIdToken(config: OnlineConfig): String

    fun loadSessionToken(): String?

    /** Saves [token], or forgets the saved one if it's `null`. */
    fun saveSessionToken(token: String?)
}

@Composable
expect fun rememberPlatformAuth(): PlatformAuth
