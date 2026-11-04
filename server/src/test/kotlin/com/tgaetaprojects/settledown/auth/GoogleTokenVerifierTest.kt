package com.tgaetaprojects.settledown.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.coroutines.runBlocking
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoogleTokenVerifierTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = pair.public as RSAPublicKey
    private val privateKey = pair.private as RSAPrivateKey

    private val verifier = GoogleTokenVerifier(setOf("our-client", "our-other-client")) { keyId ->
        publicKey.takeIf { keyId == "key-1" }
    }

    private fun token(
        issuer: String? = "https://accounts.google.com",
        audience: String = "our-client",
        subject: String? = "google-user",
        expiresInSeconds: Long? = 600,
        keyId: String = "key-1",
        algorithm: Algorithm = Algorithm.RSA256(publicKey, privateKey),
    ): String = JWT.create()
        .withKeyId(keyId)
        .withAudience(audience)
        .apply { issuer?.let(::withIssuer) }
        .apply { subject?.let(::withSubject) }
        .apply { expiresInSeconds?.let { withExpiresAt(Date(System.currentTimeMillis() + it * 1000)) } }
        .sign(algorithm)

    private fun verify(token: String) = runBlocking { verifier.verify(token) }

    @Test
    fun verify_validToken_returnsTheSubject() {
        assertEquals("google-user", verify(token()))
    }

    @Test
    fun verify_otherAllowedClient_returnsTheSubject() {
        assertEquals("google-user", verify(token(audience = "our-other-client")))
    }

    @Test
    fun verify_shortIssuerForm_returnsTheSubject() {
        assertEquals("google-user", verify(token(issuer = "accounts.google.com")))
    }

    @Test
    fun verify_otherAudience_isRefused() {
        assertNull(verify(token(audience = "someone-elses-app")))
    }

    @Test
    fun verify_otherIssuer_isRefused() {
        assertNull(verify(token(issuer = "https://accounts.example.com")))
    }

    @Test
    fun verify_missingIssuer_isRefused() {
        assertNull(verify(token(issuer = null)))
    }

    @Test
    fun verify_expired_isRefused() {
        assertNull(verify(token(expiresInSeconds = -3600)))
    }

    @Test
    fun verify_noExpiry_isRefused() {
        assertNull(verify(token(expiresInSeconds = null)))
    }

    @Test
    fun verify_noSubject_isRefused() {
        assertNull(verify(token(subject = null)))
    }

    @Test
    fun verify_unknownKey_isRefused() {
        assertNull(verify(token(keyId = "key-2")))
    }

    @Test
    fun verify_signedByAnotherKey_isRefused() {
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val forged = Algorithm.RSA256(other.public as RSAPublicKey, other.private as RSAPrivateKey)
        assertNull(verify(token(algorithm = forged)))
    }

    @Test
    fun verify_symmetricAlgorithm_isRefused() {
        assertNull(verify(token(algorithm = Algorithm.HMAC256(publicKey.encoded))))
    }

    @Test
    fun verify_notAToken_isRefused() {
        assertNull(verify("not a token"))
    }
}
