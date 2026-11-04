package com.tgaetaprojects.settledown

import com.tgaetaprojects.settledown.auth.DevTokenVerifier
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerConfigTest {

    @Test
    fun from_noWayToSignIn_fails() {
        assertFailsWith<IllegalArgumentException> { ServerConfig.from(emptyMap()) }
    }

    @Test
    fun from_clientIds_leavesDevAuthAndProxyTrustOff() {
        val config = ServerConfig.from(mapOf("SETTLEDOWN_GOOGLE_CLIENT_IDS" to "one, two,"))
        assertEquals(setOf("one", "two"), config.googleClientIds)
        assertFalse(config.devAuth)
        assertFalse(config.trustProxy)
    }

    @Test
    fun from_devAuthAnythingButTrue_staysOff() {
        val env = mapOf("SETTLEDOWN_GOOGLE_CLIENT_IDS" to "one", "SETTLEDOWN_DEV_AUTH" to "1")
        assertFalse(ServerConfig.from(env).devAuth)
    }

    @Test
    fun from_devAuthTrue_needsNoClientIds() {
        assertTrue(ServerConfig.from(mapOf("SETTLEDOWN_DEV_AUTH" to "true")).devAuth)
    }

    @Test
    fun devVerifier_prefixedToken_isItsOwnIdentity() = runBlocking {
        assertEquals("dev:alice", DevTokenVerifier.verify("dev:alice"))
    }

    @Test
    fun devVerifier_otherToken_isRefused() = runBlocking {
        assertNull(DevTokenVerifier.verify("alice"))
        assertNull(DevTokenVerifier.verify("dev:"))
    }
}
