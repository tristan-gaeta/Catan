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
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
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

/** Runs the whole application against a temporary database. */
fun testServer(
    now: () -> Long = System::currentTimeMillis,
    trustProxy: Boolean = false,
    block: suspend ApplicationTestBuilder.() -> Unit,
) = withTempDatabasePath { path ->
    testApplication {
        application { module(Services(Database(path), AnyToken, now), trustProxy) }
        block()
    }
}

/** A client that speaks this build's protocol but isn't signed in. */
fun ApplicationTestBuilder.anonymousClient(): HttpClient = createClient {
    install(ContentNegotiation) { json() }
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
        defaultRequest {
            header(WireCodec.VERSION_HEADER, WireCodec.PROTOCOL_VERSION)
            header("Authorization", "Bearer ${session.token}")
        }
    }
}
