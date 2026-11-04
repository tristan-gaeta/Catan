package com.tgaetaprojects.settledown.store

import com.tgaetaprojects.settledown.api.GameStatus
import com.tgaetaprojects.settledown.api.GameSummary
import com.tgaetaprojects.settledown.api.SeatInfo
import com.tgaetaprojects.settledown.game.BoardLayout
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.codec.ActionCodec
import com.tgaetaprojects.settledown.game.codec.CodecException
import com.tgaetaprojects.settledown.game.codec.GameRecord
import com.tgaetaprojects.settledown.game.codec.LayoutCodec
import com.tgaetaprojects.settledown.game.codec.RecordedAction
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.Game
import java.sql.Connection
import java.sql.Statement

/** A seat at a stored game; [accountId] is `null` once the player has deleted their account. */
data class StoredSeat(val color: PlayerColor, val accountId: Long?)

/**
 * Stores each game as its seeds and its actions, one row per accepted action, so a game can be rebuilt by replay. A
 * finished game also keeps its encoded [GameRecord], for later analysis.
 */
class GameStore(private val db: Database, private val now: () -> Long = System::currentTimeMillis) {

    /** Saves a new game with [seats] in seating order, and returns its id. */
    suspend fun create(seats: List<Pair<PlayerColor, Long>>, boardSeed: Long, gameSeed: Long): Long = db.transaction {
        val id = prepareStatement(
            """
            INSERT INTO games (rules_version, layout, board_seed, game_seed, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            Statement.RETURN_GENERATED_KEYS
        ).use { insert ->
            insert.setInt(1, Game.RULES_VERSION)
            insert.setBytes(2, LayoutCodec.encode(BoardLayout.generate(boardSeed)))
            insert.setLong(3, boardSeed)
            insert.setLong(4, gameSeed)
            insert.setString(5, GameStatus.ACTIVE.name)
            insert.setLong(6, now())
            insert.executeUpdate()
            insert.generatedKeys.use { it.next(); it.getLong(1) }
        }
        prepareStatement(
            "INSERT INTO game_seats (game_id, seat_index, color, account_id) VALUES (?, ?, ?, ?)"
        ).use { insert ->
            for ((index, seat) in seats.withIndex()) {
                insert.setLong(1, id)
                insert.setInt(2, index)
                insert.setString(3, seat.first.name)
                insert.setLong(4, seat.second)
                insert.executeUpdate()
            }
        }
        id
    }

    /** Saves accepted action number [seq]. A repeated number fails, so a bug can't silently fork a game's history. */
    suspend fun appendAction(gameId: Long, seq: Int, actor: PlayerColor, action: Action): Unit = db.transaction {
        prepareStatement("INSERT INTO game_actions (game_id, seq, bytes) VALUES (?, ?, ?)").use { insert ->
            insert.setLong(1, gameId)
            insert.setInt(2, seq)
            insert.setBytes(3, ActionCodec.encode(actor, action))
            insert.executeUpdate()
        }
    }

    /** Ends a game, keeping its [record] if it has one worth analyzing. */
    suspend fun finish(gameId: Long, status: GameStatus, record: GameRecord? = null): Unit = db.transaction {
        prepareStatement("UPDATE games SET status = ?, record = ? WHERE id = ?").use { update ->
            update.setString(1, status.name)
            update.setBytes(2, record?.encode())
            update.setLong(3, gameId)
            update.executeUpdate()
        }
    }

    suspend fun activeGameIds(): List<Long> = db.transaction {
        prepareStatement("SELECT id FROM games WHERE status = ?").use { query ->
            query.setString(1, GameStatus.ACTIVE.name)
            query.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1)) } }
        }
    }

    suspend fun seats(gameId: Long): List<StoredSeat> = db.transaction { readSeats(gameId) }

    /**
     * The record of everything saved for [gameId]. Throws [CodecException] if its stored bytes are from another
     * format version; `GameRecord.replay` makes the same refusal for another rules version.
     */
    suspend fun loadRecord(gameId: Long): GameRecord = db.transaction {
        val seats = readSeats(gameId).map { it.color }
        val actions = prepareStatement("SELECT bytes FROM game_actions WHERE game_id = ? ORDER BY seq").use { query ->
            query.setLong(1, gameId)
            query.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val (actor, action) = ActionCodec.decode(rows.getBytes(1))
                        add(RecordedAction(actor, action))
                    }
                }
            }
        }
        prepareStatement("SELECT rules_version, layout, board_seed, game_seed FROM games WHERE id = ?").use { query ->
            query.setLong(1, gameId)
            query.executeQuery().use { rows ->
                check(rows.next()) { "No game $gameId" }
                GameRecord(
                    rows.getInt(1),
                    seats,
                    rows.getLong(3),
                    rows.getLong(4),
                    LayoutCodec.decode(rows.getBytes(2)),
                    actions
                )
            }
        }
    }

    /** The games [accountId] has a seat at, newest first. */
    suspend fun gamesFor(accountId: Long): List<GameSummary> = db.transaction {
        val games = prepareStatement(
            """
            SELECT games.id, games.status FROM games
            JOIN game_seats ON game_seats.game_id = games.id
            WHERE game_seats.account_id = ?
            ORDER BY games.id DESC
            """.trimIndent()
        ).use { query ->
            query.setLong(1, accountId)
            query.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.getLong(1) to GameStatus.valueOf(rows.getString(2))) }
            }
        }
        games.map { (id, status) -> GameSummary(id, status, readSeatInfo(id, accountId)) }
    }

    private fun Connection.readSeats(gameId: Long): List<StoredSeat> =
        prepareStatement("SELECT color, account_id FROM game_seats WHERE game_id = ? ORDER BY seat_index").use { query ->
            query.setLong(1, gameId)
            query.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val account = rows.getLong(2).takeUnless { rows.wasNull() }
                        add(StoredSeat(PlayerColor.valueOf(rows.getString(1)), account))
                    }
                }
            }
        }

    private fun Connection.readSeatInfo(gameId: Long, viewer: Long): List<SeatInfo> =
        prepareStatement(
            """
            SELECT game_seats.color, game_seats.account_id, accounts.display_name FROM game_seats
            LEFT JOIN accounts ON accounts.id = game_seats.account_id
            WHERE game_seats.game_id = ?
            ORDER BY game_seats.seat_index
            """.trimIndent()
        ).use { query ->
            query.setLong(1, gameId)
            query.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val account = rows.getLong(2).takeUnless { rows.wasNull() }
                        add(SeatInfo(PlayerColor.valueOf(rows.getString(1)), rows.getString(3), account == viewer))
                    }
                }
            }
        }
}
