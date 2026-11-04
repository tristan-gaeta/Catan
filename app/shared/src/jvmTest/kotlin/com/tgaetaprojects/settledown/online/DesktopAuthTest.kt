package com.tgaetaprojects.settledown.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class DesktopAuthTest {

    @Test
    fun challengeFor_rfc7636Example_matches() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            DesktopAuth.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        )
    }

    @Test
    fun parseRedirect_matchingState_returnsTheCode() {
        assertEquals(
            DesktopAuth.Companion.Redirect.Code("the/code"),
            DesktopAuth.parseRedirect("GET /?state=s1&code=the%2Fcode HTTP/1.1", "s1")
        )
    }

    @Test
    fun parseRedirect_personDeclined_isDeclined() {
        assertEquals(
            DesktopAuth.Companion.Redirect.Declined,
            DesktopAuth.parseRedirect("GET /?state=s1&error=access_denied HTTP/1.1", "s1")
        )
    }

    @Test
    fun parseRedirect_notFromThisSignIn_isUnrelated() {
        for (line in listOf(
            "GET /?state=forged&code=abc HTTP/1.1",
            "GET /?code=abc HTTP/1.1",
            "GET /favicon.ico HTTP/1.1",
            "GET /?%zz=1 HTTP/1.1",
            "",
        )) {
            assertEquals(DesktopAuth.Companion.Redirect.Unrelated, DesktopAuth.parseRedirect(line, "s1"), line)
        }
    }

    private fun request(port: Int, line: String) {
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            socket.getOutputStream().write("$line\r\n\r\n".toByteArray())
            socket.getOutputStream().flush()
            socket.getInputStream().readBytes()
        }
    }

    private fun localServer() = ServerSocket(0, 5, InetAddress.getLoopbackAddress())

    @Test
    fun awaitCode_strayRequestsFirst_stillGetsTheCode() = runBlocking {
        localServer().use { server ->
            val result = async(Dispatchers.IO) { DesktopAuth.awaitCode(server, "s1", 10_000) }
            withContext(Dispatchers.IO) {
                request(server.localPort, "GET /favicon.ico HTTP/1.1")
                request(server.localPort, "GET /?state=forged&code=evil HTTP/1.1")
                request(server.localPort, "GET /?state=s1&code=real HTTP/1.1")
            }
            assertEquals("real", result.await())
        }
    }

    @Test
    fun awaitCode_silentConnection_doesNotBlockTheRealOne() = runBlocking {
        localServer().use { server ->
            val result = async(Dispatchers.IO) { DesktopAuth.awaitCode(server, "s1", 10_000) }
            withContext(Dispatchers.IO) {
                Socket(InetAddress.getLoopbackAddress(), server.localPort).use {
                    request(server.localPort, "GET /?state=s1&code=real HTTP/1.1")
                }
            }
            assertEquals("real", result.await())
        }
    }

    @Test
    fun awaitCode_nobodyComes_timesOut() = runBlocking {
        localServer().use { server ->
            assertFailsWith<SignInCancelledException> {
                withContext(Dispatchers.IO) { DesktopAuth.awaitCode(server, "s1", 100) }
            }
        }
        Unit
    }

    @Test
    fun awaitCode_personDeclines_isCancelled() = runBlocking {
        localServer().use { server ->
            val result = async(Dispatchers.IO) {
                runCatching { DesktopAuth.awaitCode(server, "s1", 10_000) }
            }
            withContext(Dispatchers.IO) { request(server.localPort, "GET /?state=s1&error=access_denied HTTP/1.1") }
            assertIs<SignInCancelledException>(result.await().exceptionOrNull())
        }
        Unit
    }

    @Test
    fun authorizationUrl_asksForOnlyOpenIdWithPkce() {
        val url = DesktopAuth.authorizationUrl("client", "http://127.0.0.1:5000", "st", "ch")
        assertContains(url, "scope=openid&")
        assertContains(url, "code_challenge=ch")
        assertContains(url, "code_challenge_method=S256")
        assertContains(url, "state=st")
        assertContains(url, "redirect_uri=http%3A%2F%2F127.0.0.1%3A5000")
        assertFalse(url.contains("email"))
    }

    @Test
    fun sessionToken_saved_loadsBackAndOnlyTheOwnerCanReadIt() {
        val directory = Files.createTempDirectory("settledown-auth").resolve("nested")
        val auth = DesktopAuth(directory.toFile())
        assertNull(auth.loadSessionToken())

        auth.saveSessionToken("secret")

        assertEquals("secret", auth.loadSessionToken())
        val permissions = Files.getPosixFilePermissions(directory.resolve("session-token"))
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions)

        auth.saveSessionToken(null)
        assertNull(auth.loadSessionToken())
    }
}
