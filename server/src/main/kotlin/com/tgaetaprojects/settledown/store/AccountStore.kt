package com.tgaetaprojects.settledown.store

import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Statement
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

data class Account(val id: Long, val displayName: String)

/** Accounts are keyed on Google's stable `sub` identifier. The email address is never stored. */
class AccountStore(private val db: Database, private val now: () -> Long = System::currentTimeMillis) {

    /** The account for [googleSub], created on first sign-in; the flag says whether it's new. */
    suspend fun findOrCreate(googleSub: String): Pair<Account, Boolean> = db.transaction {
        prepareStatement("SELECT id, display_name FROM accounts WHERE google_sub = ?").use { query ->
            query.setString(1, googleSub)
            query.executeQuery().use { rows ->
                if (rows.next()) return@transaction Account(rows.getLong(1), rows.getString(2)) to false
            }
        }
        val id = prepareStatement(
            "INSERT INTO accounts (google_sub, display_name, created_at) VALUES (?, '', ?)",
            Statement.RETURN_GENERATED_KEYS
        ).use { insert ->
            insert.setString(1, googleSub)
            insert.setLong(2, now())
            insert.executeUpdate()
            insert.generatedKeys.use { it.next(); it.getLong(1) }
        }
        val name = "Player $id"
        prepareStatement("UPDATE accounts SET display_name = ? WHERE id = ?").use { update ->
            update.setString(1, name)
            update.setLong(2, id)
            update.executeUpdate()
        }
        Account(id, name) to true
    }

    suspend fun rename(id: Long, displayName: String): Unit = db.transaction {
        prepareStatement("UPDATE accounts SET display_name = ? WHERE id = ?").use { update ->
            update.setString(1, displayName)
            update.setLong(2, id)
            update.executeUpdate()
        }
    }

    suspend fun names(ids: Collection<Long>): Map<Long, String> = db.transaction {
        prepareStatement("SELECT display_name FROM accounts WHERE id = ?").use { query ->
            ids.associateWith { id ->
                query.setLong(1, id)
                query.executeQuery().use { if (it.next()) it.getString(1) else "" }
            }
        }
    }

    /** Deletes the account and its sessions. Past games keep the seat, with no name attached. */
    suspend fun delete(id: Long): Unit = db.transaction {
        prepareStatement("DELETE FROM accounts WHERE id = ?").use { delete ->
            delete.setLong(1, id)
            delete.executeUpdate()
        }
    }

    companion object {
        private const val MAX_NAME_LENGTH = 24

        /** [raw] trimmed, or `null` if it's empty, too long, or holds control characters. */
        fun normalizeDisplayName(raw: String): String? =
            raw.trim().takeIf { name ->
                name.length in 1..MAX_NAME_LENGTH && name.none { it.isISOControl() }
            }
    }
}

/**
 * Sign-in sessions. A token is 256 random bits, and only its SHA-256 hash is stored, so a copy of the database can't
 * be used to sign in as anyone.
 */
class SessionStore(
    private val db: Database,
    private val now: () -> Long = System::currentTimeMillis,
    private val lifetime: Duration = 90.days,
) {
    private val random = SecureRandom()

    suspend fun create(accountId: Long): String {
        val token = ByteArray(32).also(random::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        db.transaction {
            prepareStatement("INSERT INTO sessions (token_hash, account_id, expires_at) VALUES (?, ?, ?)").use { insert ->
                insert.setBytes(1, hash(token))
                insert.setLong(2, accountId)
                insert.setLong(3, now() + lifetime.inWholeMilliseconds)
                insert.executeUpdate()
            }
            prepareStatement("DELETE FROM sessions WHERE expires_at <= ?").use { sweep ->
                sweep.setLong(1, now())
                sweep.executeUpdate()
            }
        }
        return token
    }

    /** The account [token] signs in, or `null` if it's unknown or has expired. */
    suspend fun accountFor(token: String): Account? = db.transaction {
        prepareStatement(
            """
            SELECT accounts.id, accounts.display_name FROM sessions
            JOIN accounts ON accounts.id = sessions.account_id
            WHERE sessions.token_hash = ? AND sessions.expires_at > ?
            """.trimIndent()
        ).use { query ->
            query.setBytes(1, hash(token))
            query.setLong(2, now())
            query.executeQuery().use { if (it.next()) Account(it.getLong(1), it.getString(2)) else null }
        }
    }

    suspend fun revoke(token: String): Unit = db.transaction {
        prepareStatement("DELETE FROM sessions WHERE token_hash = ?").use { delete ->
            delete.setBytes(1, hash(token))
            delete.executeUpdate()
        }
    }

    private fun hash(token: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
}
