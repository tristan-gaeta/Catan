package com.tgaetaprojects.settledown.online

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Composable
actual fun rememberPlatformAuth(): PlatformAuth = remember { DesktopAuth(File(System.getProperty("user.home"), ".settledown")) }

/**
 * Signs in the way Google asks installed apps to: in the system browser, returning to a one-time local port, with PKCE
 * and a state check. The session token sits in a file only this user can read.
 */
internal class DesktopAuth(private val directory: File) : PlatformAuth {
    private val tokenFile = File(directory, "session-token")
    private val random = SecureRandom()

    override suspend fun googleIdToken(config: OnlineConfig): String = withContext(Dispatchers.IO) {
        val clientId = config.googleClientId
        val secret = config.googleClientSecret
        if (clientId == null || secret == null) throw SignInFailedException("This build isn't set up for Google sign-in.")
        val desktop = Desktop.getDesktop().takeIf { Desktop.isDesktopSupported() && it.isSupported(Desktop.Action.BROWSE) }
            ?: throw SignInFailedException("Sign-in needs a web browser, and this system can't open one.")
        val verifier = randomString()
        val state = randomString()
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val redirect = "http://127.0.0.1:${server.localPort}"
            desktop.browse(URI(authorizationUrl(clientId, redirect, state, challengeFor(verifier))))
            val code = awaitCode(server, state, SIGN_IN_TIMEOUT_MILLIS)
            exchangeCode(code, clientId, secret, verifier, redirect)
        }
    }

    private fun exchangeCode(code: String, clientId: String, secret: String, verifier: String, redirect: String): String {
        val form = mapOf(
            "code" to code,
            "client_id" to clientId,
            "client_secret" to secret,
            "code_verifier" to verifier,
            "redirect_uri" to redirect,
            "grant_type" to "authorization_code",
        ).entries.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        val request = HttpRequest.newBuilder(URI(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(java.time.Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw SignInFailedException("Google refused the sign-in. Please try again.")
        return Json.parseToJsonElement(response.body()).jsonObject.getValue("id_token").jsonPrimitive.content
    }

    private fun randomString(): String =
        ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    override fun loadSessionToken(): String? =
        tokenFile.takeIf { it.exists() }?.readText()?.takeIf { it.isNotEmpty() }

    override fun saveSessionToken(token: String?) {
        if (token == null) {
            tokenFile.delete()
            return
        }
        directory.mkdirs()
        ownerOnly(directory)
        tokenFile.createNewFile()
        // restrict the file before the token goes in it
        ownerOnly(tokenFile)
        tokenFile.writeText(token)
    }

    private fun ownerOnly(file: File) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        if (file.isDirectory) file.setExecutable(true, true)
    }

    internal companion object {
        private const val SIGN_IN_TIMEOUT_MILLIS = 180_000L
        private const val HTTP_TIMEOUT_SECONDS = 30L

        /** How long one blocking wait lasts, so cancellation and the overall timeout are noticed. */
        private const val POLL_MILLIS = 500
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"

        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

        /** The PKCE S256 challenge for [verifier]. */
        fun challengeFor(verifier: String): String =
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

        /** Asks only for `openid`: the server needs a stable identity, and neither an email address nor a profile. */
        fun authorizationUrl(clientId: String, redirect: String, state: String, challenge: String): String =
            mapOf(
                "client_id" to clientId,
                "redirect_uri" to redirect,
                "response_type" to "code",
                "scope" to "openid",
                "state" to state,
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
            ).entries.joinToString("&", prefix = "$AUTH_URL?") { (key, value) -> "$key=${encode(value)}" }

        /** What a request to the local port turned out to be. */
        sealed interface Redirect {
            data class Code(val code: String) : Redirect
            data object Declined : Redirect

            /** Not from this sign-in: a browser's favicon request, say, or another process probing the port. */
            data object Unrelated : Redirect
        }

        /** Reads a request line like `GET /?state=s&code=c HTTP/1.1`. Only a matching [state] can end the sign-in. */
        fun parseRedirect(requestLine: String, state: String): Redirect {
            val query = requestLine.split(' ').getOrNull(1)?.substringAfter('?', "") ?: return Redirect.Unrelated
            val params = try {
                query.split('&').filter { it.contains('=') }.associate {
                    URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to
                            URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
                }
            } catch (_: IllegalArgumentException) {
                return Redirect.Unrelated
            }
            if (params["state"] != state) return Redirect.Unrelated
            return params["code"]?.let { Redirect.Code(it) } ?: Redirect.Declined
        }

        /**
         * Waits for the browser to come back with a code, ignoring anything unrelated. Throws
         * [SignInCancelledException] if the person declines or [timeoutMillis] passes.
         */
        suspend fun awaitCode(server: ServerSocket, state: String, timeoutMillis: Long): String {
            val deadline = System.nanoTime() + timeoutMillis * 1_000_000
            server.soTimeout = POLL_MILLIS
            while (System.nanoTime() < deadline) {
                currentCoroutineContext().ensureActive()
                val socket = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val redirect = socket.use { answer(it, state) }
                when (redirect) {
                    is Redirect.Code -> return redirect.code
                    Redirect.Declined -> throw SignInCancelledException()
                    Redirect.Unrelated -> Unit
                }
            }
            throw SignInCancelledException()
        }

        private fun answer(socket: Socket, state: String): Redirect {
            socket.soTimeout = POLL_MILLIS
            val redirect = try {
                parseRedirect(socket.getInputStream().bufferedReader().readLine().orEmpty(), state)
            } catch (_: IOException) {
                // a connection that opened and never spoke, which is what browsers do when preconnecting
                return Redirect.Unrelated
            }
            val (status, page) = when (redirect) {
                is Redirect.Code -> "200 OK" to "You're signed in. You can close this tab."
                Redirect.Declined -> "200 OK" to "Sign-in didn't finish. You can close this tab."
                Redirect.Unrelated -> "404 Not Found" to ""
            }
            socket.getOutputStream().write(
                "HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\n\r\n$page"
                    .toByteArray()
            )
            return redirect
        }
    }
}
