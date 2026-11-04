package com.tgaetaprojects.settledown.auth

import com.auth0.jwk.JwkException
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CachedKeysTest {
    private val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey
    private var time = 1_000L
    private var fetches = 0
    private var failing = false

    private val keys = CachedKeys(
        fetch = {
            fetches++
            if (failing) throw JwkException("Google is down")
            mapOf("known" to key)
        },
        now = { time },
    )

    @Test
    fun get_knownKey_fetchesOnce() {
        assertEquals(key, keys.get("known"))
        assertEquals(key, keys.get("known"))
        assertEquals(1, fetches)
    }

    @Test
    fun get_manyUnknownKeys_fetchesOnce() {
        repeat(100) { assertNull(keys.get("made-up-$it")) }
        assertEquals(1, fetches)
    }

    @Test
    fun get_unknownKeyAfterTheInterval_fetchesAgain() {
        keys.get("known")
        time += CachedKeys.UNKNOWN_KEY_REFETCH.inWholeMilliseconds + 1
        assertNull(keys.get("rotated"))
        assertEquals(2, fetches)
    }

    @Test
    fun get_knownKeyPastRefreshAge_fetchesAgain() {
        keys.get("known")
        time += CachedKeys.REFRESH_AFTER.inWholeMilliseconds + 1
        keys.get("known")
        assertEquals(2, fetches)
    }

    @Test
    fun get_fetchFailsWithNoKeys_isUnavailable() {
        failing = true
        assertFailsWith<VerifierUnavailableException> { keys.get("known") }
    }

    @Test
    fun get_fetchFailsThenRecovers_retriesAtOnce() {
        failing = true
        assertFailsWith<VerifierUnavailableException> { keys.get("known") }
        failing = false
        assertEquals(key, keys.get("known"))
    }

    @Test
    fun get_refreshFailsForKnownKey_keepsWorking() {
        keys.get("known")
        failing = true
        time += CachedKeys.REFRESH_AFTER.inWholeMilliseconds + 1
        assertEquals(key, keys.get("known"))
    }

    @Test
    fun get_refreshFailsForUnknownKey_isUnavailable() {
        keys.get("known")
        failing = true
        time += CachedKeys.UNKNOWN_KEY_REFETCH.inWholeMilliseconds + 1
        assertFailsWith<VerifierUnavailableException> { keys.get("rotated") }
    }
}
