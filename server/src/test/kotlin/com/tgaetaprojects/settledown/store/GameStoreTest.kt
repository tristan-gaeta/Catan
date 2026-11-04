package com.tgaetaprojects.settledown.store

import com.tgaetaprojects.settledown.api.GameStatus
import com.tgaetaprojects.settledown.game.PlayerColor.BLUE
import com.tgaetaprojects.settledown.game.PlayerColor.RED
import com.tgaetaprojects.settledown.game.PlayerColor.WHITE
import com.tgaetaprojects.settledown.game.codec.GameRecorder
import com.tgaetaprojects.settledown.game.codec.SnapshotCodec
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.withTempDatabasePath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GameStoreTest {

    private fun withStores(block: suspend (AccountStore, GameStore) -> Unit) = withTempDatabasePath { path ->
        Database(path).use { db ->
            runBlocking { block(AccountStore(db), GameStore(db)) }
        }
    }

    private suspend fun AccountStore.threePlayers(): List<Long> =
        listOf("a", "b", "c").map { findOrCreate(it).first.id }

    @Test
    fun loadRecord_afterReopening_rebuildsTheGame() = withTempDatabasePath { path ->
        val seats = listOf(RED, WHITE, BLUE)
        val recorder = GameRecorder(seats, boardSeed = 11L, gameSeed = 22L)
        val id = Database(path).use { db ->
            runBlocking {
                val games = GameStore(db)
                val id = games.create(seats.zip(AccountStore(db).threePlayers()), 11L, 22L)
                for (actor in seats) {
                    val seq = recorder.actionCount
                    recorder.apply(actor, Action.RollDice)
                    games.appendAction(id, seq, actor, Action.RollDice)
                }
                id
            }
        }

        val record = Database(path).use { db -> runBlocking { GameStore(db).loadRecord(id) } }

        assertEquals(seats, record.seats)
        assertEquals(3, record.actions.size)
        assertContentEquals(SnapshotCodec.encode(recorder.game), SnapshotCodec.encode(record.replay()))
    }

    @Test
    fun appendAction_repeatedSeq_fails() = withStores { accounts, games ->
        val seats = listOf(RED, WHITE, BLUE).zip(accounts.threePlayers())
        val id = games.create(seats, 1L, 2L)
        games.appendAction(id, 0, RED, Action.RollDice)
        assertFailsWith<java.sql.SQLException> { games.appendAction(id, 0, WHITE, Action.RollDice) }
    }

    @Test
    fun activeGameIds_finishedGame_isLeftOut() = withStores { accounts, games ->
        val seats = listOf(RED, WHITE, BLUE).zip(accounts.threePlayers())
        val finished = games.create(seats, 1L, 2L)
        val active = games.create(seats, 3L, 4L)
        games.finish(finished, GameStatus.ABANDONED)
        assertEquals(listOf(active), games.activeGameIds())
    }

    @Test
    fun seats_deletedAccount_keepsTheSeat() = withStores { accounts, games ->
        val players = accounts.threePlayers()
        val id = games.create(listOf(RED, WHITE, BLUE).zip(players), 1L, 2L)
        accounts.delete(players[0])
        val seats = games.seats(id)
        assertEquals(listOf(RED, WHITE, BLUE), seats.map { it.color })
        assertEquals(listOf(null, players[1], players[2]), seats.map { it.accountId })
    }

    @Test
    fun sessions_expired_stopWorking() = withTempDatabasePath { path ->
        var time = 1_000L
        Database(path).use { db ->
            runBlocking {
                val account = AccountStore(db).findOrCreate("a").first
                val sessions = SessionStore(db, now = { time })
                val token = sessions.create(account.id)
                assertEquals(account, sessions.accountFor(token))
                time += 91L * 24 * 60 * 60 * 1000
                assertEquals(null, sessions.accountFor(token))
            }
        }
    }

    @Test
    fun sessions_tokenItself_isNotStored() = withTempDatabasePath { path ->
        Database(path).use { db ->
            runBlocking {
                val account = AccountStore(db).findOrCreate("a").first
                val token = SessionStore(db).create(account.id)
                val stored = db.transaction {
                    prepareStatement("SELECT token_hash FROM sessions").use { query ->
                        query.executeQuery().use { it.next(); it.getBytes(1) }
                    }
                }
                assertTrue(!stored.contentEquals(token.toByteArray()))
                assertEquals(32, stored.size)
            }
        }
    }
}
