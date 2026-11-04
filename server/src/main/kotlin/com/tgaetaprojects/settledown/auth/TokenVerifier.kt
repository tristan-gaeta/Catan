package com.tgaetaprojects.settledown.auth

import com.auth0.jwk.JwkException
import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.security.interfaces.RSAPublicKey
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Google's keys couldn't be fetched, so a token can't be judged either way. */
class VerifierUnavailableException(cause: Throwable) : RuntimeException(cause)

/** Turns a sign-in token into a stable identity. */
fun interface TokenVerifier {
    /** The token's subject, or `null` if the token isn't valid. */
    suspend fun verify(token: String): String?
}

/**
 * Verifies Google ID tokens: an RS256 signature from one of Google's keys, Google as issuer, one of [clientIds] as
 * audience, and not expired.
 *
 * @param publicKey finds Google's key by id, or returns `null` if there's no such key.
 */
class GoogleTokenVerifier(
    private val clientIds: Set<String>,
    private val publicKey: (keyId: String) -> RSAPublicKey?,
) : TokenVerifier {
    init {
        require(clientIds.isNotEmpty()) { "No Google client IDs are configured" }
    }

    override suspend fun verify(token: String): String? = withContext(Dispatchers.IO) {
        try {
            val decoded = JWT.decode(token)
            val key = decoded.keyId?.let(publicKey) ?: return@withContext null
            JWT.require(Algorithm.RSA256(key, null))
                .withIssuer(*ISSUERS)
                .withAnyOfAudience(*clientIds.toTypedArray())
                .withClaimPresence("exp")
                .acceptLeeway(CLOCK_LEEWAY_SECONDS)
                .build()
                .verify(decoded)
                .subject
                ?.takeIf { it.isNotEmpty() }
        } catch (_: JWTVerificationException) {
            null
        }
    }

    companion object {
        /** Google documents both forms of its issuer. */
        private val ISSUERS = arrayOf("https://accounts.google.com", "accounts.google.com")
        private const val CLOCK_LEEWAY_SECONDS = 30L
        private const val KEYS_URL = "https://www.googleapis.com/oauth2/v3/certs"

        /** A verifier using Google's published keys. */
        fun withGoogleKeys(clientIds: Set<String>): GoogleTokenVerifier {
            val provider = UrlJwkProvider(URI(KEYS_URL).toURL())
            val keys = CachedKeys(fetch = {
                provider.all.mapNotNull { jwk ->
                    val key = jwk.publicKey as? RSAPublicKey
                    if (jwk.id != null && key != null) jwk.id to key else null
                }.toMap()
            })
            return GoogleTokenVerifier(clientIds, keys::get)
        }
    }
}

/**
 * Google's signing keys, held in memory. Anyone can send a token naming a made-up key, so an unknown id only triggers
 * a fetch every [UNKNOWN_KEY_REFETCH] at most, which keeps such tokens from turning into a stream of requests to
 * Google. Google publishes new keys well before signing with them, so a real token's key is normally already here.
 *
 * If a fetch fails, keys already held keep working. A key that can't be judged throws [VerifierUnavailableException].
 */
internal class CachedKeys(
    private val fetch: () -> Map<String, RSAPublicKey>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var keys = emptyMap<String, RSAPublicKey>()
    private var fetchedAt: Long? = null

    @Synchronized
    fun get(keyId: String): RSAPublicKey? {
        val age = fetchedAt?.let { now() - it }
        val limit = if (keyId in keys) REFRESH_AFTER else UNKNOWN_KEY_REFETCH
        if (age == null || age > limit.inWholeMilliseconds) refresh(keyId)
        return keys[keyId]
    }

    private fun refresh(keyId: String) {
        try {
            keys = fetch()
            fetchedAt = now()
        } catch (e: JwkException) {
            // with no keys at all, try again on the next request: there's nothing to protect yet
            if (keys.isNotEmpty()) fetchedAt = now()
            if (keyId !in keys) throw VerifierUnavailableException(e)
        }
    }

    companion object {
        val REFRESH_AFTER = 12.hours
        val UNKNOWN_KEY_REFETCH = 5.minutes
    }
}

/**
 * Accepts any `dev:<name>` token as the identity `dev:<name>`, with no proof at all. It exists so the game can be run
 * locally without a Google project, and is only used when `SETTLEDOWN_DEV_AUTH=true`.
 */
object DevTokenVerifier : TokenVerifier {
    override suspend fun verify(token: String): String? = token.takeIf { it.startsWith("dev:") && it.length > 4 }
}
