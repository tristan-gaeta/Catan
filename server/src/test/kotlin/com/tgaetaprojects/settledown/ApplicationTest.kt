package com.tgaetaprojects.settledown

import com.tgaetaprojects.settledown.api.AccountInfo
import com.tgaetaprojects.settledown.api.ApiError
import com.tgaetaprojects.settledown.api.GoogleSignInRequest
import com.tgaetaprojects.settledown.api.SignInResponse
import com.tgaetaprojects.settledown.api.UpdateAccountRequest
import com.tgaetaprojects.settledown.game.codec.WireCodec
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun health_noProtocolHeader_isOk() = testServer {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun request_noProtocolHeader_asksForUpdate() = testServer {
        val http = createClient { install(ContentNegotiation) { json() } }
        val response = http.post("/auth/google")
        assertEquals(HttpStatusCode.UpgradeRequired, response.status)
        assertEquals("update_required", response.body<ApiError>().code)
    }

    @Test
    fun request_otherProtocolVersion_asksForUpdate() = testServer {
        val response = client.get("/account") { header(WireCodec.VERSION_HEADER, "0.0") }
        assertEquals(HttpStatusCode.UpgradeRequired, response.status)
    }

    @Test
    fun signIn_invalidToken_isUnauthorized() = testServer {
        val response = anonymousClient().post("/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleSignInRequest("bad"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun signIn_sameIdentityTwice_reusesAccount() = testServer {
        suspend fun signIn(): SignInResponse = anonymousClient().post("/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleSignInRequest("alice"))
        }.body()

        val first = signIn()
        val second = signIn()
        assertTrue(first.isNewAccount)
        assertFalse(second.isNewAccount)
        assertEquals(first.account.id, second.account.id)
        assertTrue(first.token != second.token)
    }

    @Test
    fun account_noToken_isUnauthorized() = testServer {
        assertEquals(HttpStatusCode.Unauthorized, anonymousClient().get("/account").status)
    }

    @Test
    fun account_madeUpToken_isUnauthorized() = testServer {
        val response = anonymousClient().get("/account") { header("Authorization", "Bearer nope") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun account_rename_trimsTheName() = testServer {
        val alice = signedIn("alice")
        val response = alice.put("/account") {
            contentType(ContentType.Application.Json)
            setBody(UpdateAccountRequest("  Alice "))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Alice", alice.get("/account").body<AccountInfo>().displayName)
    }

    @Test
    fun account_renameInvalid_isRefused() = testServer {
        val alice = signedIn("alice")
        for (name in listOf("   ", "x".repeat(25), "tab\there")) {
            val response = alice.put("/account") {
                contentType(ContentType.Application.Json)
                setBody(UpdateAccountRequest(name))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, name)
        }
    }

    @Test
    fun session_signOut_stopsTheTokenWorking() = testServer {
        val alice = signedIn("alice")
        assertEquals(HttpStatusCode.NoContent, alice.delete("/auth/session").status)
        assertEquals(HttpStatusCode.Unauthorized, alice.get("/account").status)
    }

    @Test
    fun session_signOutLowerCaseScheme_stopsTheTokenWorking() = testServer {
        val session: SignInResponse = anonymousClient().post("/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleSignInRequest("alice"))
        }.body()
        val http = anonymousClient()
        val signOut = http.delete("/auth/session") { header("Authorization", "bearer ${session.token}") }
        assertEquals(HttpStatusCode.NoContent, signOut.status)
        val after = http.get("/account") { header("Authorization", "Bearer ${session.token}") }
        assertEquals(HttpStatusCode.Unauthorized, after.status)
    }

    private suspend fun ApplicationTestBuilder.signInFrom(forwardedFor: String): HttpStatusCode =
        anonymousClient().post("/auth/google") {
            contentType(ContentType.Application.Json)
            header("X-Forwarded-For", forwardedFor)
            setBody(GoogleSignInRequest("alice"))
        }.status

    @Test
    fun signInLimit_trustedProxy_ignoresAddressesTheClientAdds() = testServer(trustProxy = true) {
        val statuses = List(65) { signInFrom("10.0.0.$it, 203.0.113.1") }
        assertTrue(HttpStatusCode.TooManyRequests in statuses)
    }

    @Test
    fun signInLimit_trustedProxy_countsEachClientSeparately() = testServer(trustProxy = true) {
        repeat(65) { signInFrom("203.0.113.1") }
        assertEquals(HttpStatusCode.OK, signInFrom("203.0.113.2"))
    }

    @Test
    fun account_delete_stopsTheTokenWorking() = testServer {
        val alice = signedIn("alice")
        assertEquals(HttpStatusCode.NoContent, alice.delete("/account").status)
        assertEquals(HttpStatusCode.Unauthorized, alice.get("/account").status)
    }
}
