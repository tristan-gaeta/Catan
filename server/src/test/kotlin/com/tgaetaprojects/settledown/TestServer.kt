package com.tgaetaprojects.settledown

import com.tgaetaprojects.settledown.api.GoogleSignInRequest
import com.tgaetaprojects.settledown.api.SignInResponse
import com.tgaetaprojects.settledown.auth.TokenVerifier
import com.tgaetaprojects.settledown.game.codec.WireCodec
import com.tgaetaprojects.settledown.store.Database
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.util.Random
import kotlin.io.path.deleteIfExists

/** Accepts any token as the identity of the same name, so tests can sign in as whoever they need. */
val AnyToken = TokenVerifier { token -> token.takeIf { it != "bad" } }

/** A temporary database file, deleted after [block]. */
fun <T> withTempDatabasePath(block: (String) -> T): T {
    val path = Files.createTempFile("settledown-test", ".db")
    try {
        return block(path.toString())
    } finally {
        path.deleteIfExists()
        path.resolveSibling("${path.fileName}-wal").deleteIfExists()
        path.resolveSibling("${path.fileName}-shm").deleteIfExists()
    }
}

/**
 * Runs the whole application against a temporary database, with a fixed seed so a failure replays exactly.
 *
 * @param maxActionsPerSecond high by default, since scripted players act far faster than people.
 */
fun testServer(
    now: () -> Long = System::currentTimeMillis,
    trustProxy: Boolean = false,
    maxActionsPerSecond: Int = 100_000,
    block: suspend ApplicationTestBuilder.() -> Unit,
) = withTempDatabasePath { path ->
    testApplication {
        application {
            module(
                Services(Database(path), AnyToken, now, Random(42), maxActionsPerSecond = maxActionsPerSecond),
                trustProxy
            )
        }
        block()
    }
}

/** A client that speaks this build's protocol but isn't signed in. */
fun ApplicationTestBuilder.anonymousClient(): HttpClient = createClient {
    install(ContentNegotiation) { json() }
    install(WebSockets)
    defaultRequest { header(WireCodec.VERSION_HEADER, WireCodec.PROTOCOL_VERSION) }
}

/** A client signed in as [identity]. */
suspend fun ApplicationTestBuilder.signedIn(identity: String): HttpClient {
    val session: SignInResponse = anonymousClient().post("/auth/google") {
        contentType(ContentType.Application.Json)
        setBody(GoogleSignInRequest(identity))
    }.body()
    return createClient {
        install(ContentNegotiation) { json() }
        install(WebSockets)
        defaultRequest {
            header(WireCodec.VERSION_HEADER, WireCodec.PROTOCOL_VERSION)
            header("Authorization", "Bearer ${session.token}")
        }
    }
}
