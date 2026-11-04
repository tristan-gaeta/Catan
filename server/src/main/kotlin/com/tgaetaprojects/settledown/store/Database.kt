package com.tgaetaprojects.settledown.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager

/**
 * One SQLite connection, used by one coroutine at a time. Everything runs in a transaction on a single thread of the
 * IO pool, so a blocking query never holds up a game's coroutine or a request thread.
 *
 * Tables hold secrets: `games.game_seed` decides every remaining roll of a running game. Never expose them directly.
 */
class Database(path: String) : AutoCloseable {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$path")
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    init {
        connection.createStatement().use {
            it.execute("PRAGMA journal_mode = WAL")
            it.execute("PRAGMA foreign_keys = ON")
        }
        connection.autoCommit = false
        migrate()
    }

    /** Runs [block] in a transaction, rolling it back if it throws. */
    suspend fun <T> transaction(block: Connection.() -> T): T = withContext(dispatcher) {
        try {
            connection.block().also { connection.commit() }
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        }
    }

    override fun close() = connection.close()

    private fun migrate() {
        val applied = connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) }
        }
        check(applied <= MIGRATIONS.size) { "The database is newer than this server: version $applied" }
        for ((index, migration) in MIGRATIONS.withIndex().drop(applied)) {
            connection.createStatement().use { statement ->
                migration.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach(statement::execute)
                statement.execute("PRAGMA user_version = ${index + 1}")
            }
        }
        connection.commit()
    }

    private companion object {
        /** Applied in order, once each. Add new ones to the end; never edit one that has shipped. */
        val MIGRATIONS = listOf(
            """
            CREATE TABLE accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                google_sub TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            CREATE TABLE sessions (
                token_hash BLOB PRIMARY KEY,
                account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                expires_at INTEGER NOT NULL
            );
            CREATE TABLE games (
                id INTEGER PRIMARY KEY,
                rules_version INTEGER NOT NULL,
                layout BLOB NOT NULL,
                board_seed INTEGER NOT NULL,
                game_seed INTEGER NOT NULL,
                status TEXT NOT NULL,
                record BLOB,
                created_at INTEGER NOT NULL
            );
            CREATE TABLE game_seats (
                game_id INTEGER NOT NULL REFERENCES games(id),
                seat_index INTEGER NOT NULL,
                color TEXT NOT NULL,
                account_id INTEGER REFERENCES accounts(id) ON DELETE SET NULL,
                PRIMARY KEY (game_id, seat_index)
            );
            CREATE INDEX game_seats_by_account ON game_seats(account_id);
            CREATE TABLE game_actions (
                game_id INTEGER NOT NULL REFERENCES games(id),
                seq INTEGER NOT NULL,
                bytes BLOB NOT NULL,
                PRIMARY KEY (game_id, seq)
            )
            """.trimIndent(),
        )
    }
}
