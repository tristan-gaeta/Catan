package com.tgaetaprojects.settledown.rooms

import com.tgaetaprojects.settledown.ApiFailure
import com.tgaetaprojects.settledown.api.RoomInfo
import com.tgaetaprojects.settledown.api.RoomMember
import com.tgaetaprojects.settledown.store.AccountStore
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import kotlin.time.Duration.Companion.minutes

/**
 * Rooms waiting to fill. A player makes a room for 3 or 4, shares its code, and the game starts as soon as the last
 * seat is taken. Rooms live only in memory: a restart loses the ones still waiting, which costs people little, but
 * never a game in progress.
 *
 * @param startGame creates the game for these players and returns its id.
 */
class RoomService(
    private val accounts: AccountStore,
    private val startGame: suspend (members: List<Long>) -> Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Room(val code: String, val seats: Int, creator: Long, var lastChange: Long) {
        val members = mutableListOf(creator)
        var gameId: Long? = null
    }

    private val lock = Mutex()
    private val rooms = HashMap<String, Room>()

    /** Each player waits in at most one room, which also limits how many rooms one player can hold open. */
    private val waitingIn = HashMap<Long, Room>()
    private val random = SecureRandom()

    suspend fun create(accountId: Long, seats: Int): RoomInfo {
        if (seats !in 3..4) throw ApiFailure(HttpStatusCode.BadRequest, "invalid_seats", "A game is for 3 or 4 players")
        return lock.withLock {
            sweep()
            ensureNotWaiting(accountId)
            val room = Room(newCode(), seats, accountId, now())
            rooms[room.code] = room
            waitingIn[accountId] = room
            info(room)
        }
    }

    /** Takes a seat in the room, starting the game if it's the last one. Joining a room you're already in is fine. */
    suspend fun join(accountId: Long, rawCode: String): RoomInfo = lock.withLock {
        sweep()
        val room = rooms[normalize(rawCode)] ?: throw noSuchRoom()
        if (accountId in room.members) return@withLock info(room)
        ensureNotWaiting(accountId)
        if (room.gameId != null) throw ApiFailure(HttpStatusCode.Conflict, "room_full", "That game has already started")

        val members = room.members + accountId
        if (members.size == room.seats) {
            // starting comes first: if it fails, the room is left as it was
            room.gameId = startGame(members)
            room.members += accountId
            room.members.forEach(waitingIn::remove)
        } else {
            room.members += accountId
            waitingIn[accountId] = room
        }
        room.lastChange = now()
        info(room)
    }

    suspend fun leave(accountId: Long, rawCode: String): Unit = lock.withLock {
        val room = rooms[normalize(rawCode)] ?: return@withLock
        if (room.gameId == null && room.members.remove(accountId)) {
            waitingIn.remove(accountId)
            room.lastChange = now()
            if (room.members.isEmpty()) rooms.remove(room.code)
        }
    }

    /** The room, for its members only; to anyone else it doesn't exist, so codes can't be probed here. */
    suspend fun get(accountId: Long, rawCode: String): RoomInfo = lock.withLock {
        sweep()
        val room = rooms[normalize(rawCode)]?.takeIf { accountId in it.members } ?: throw noSuchRoom()
        info(room)
    }

    /** The room [accountId] is waiting in, if any. */
    suspend fun current(accountId: Long): RoomInfo? = lock.withLock {
        sweep()
        waitingIn[accountId]?.let { info(it) }
    }

    /** Takes a deleted account out of any room it's waiting in. */
    suspend fun removeAccount(accountId: Long): Unit = lock.withLock {
        val room = waitingIn.remove(accountId) ?: return@withLock
        room.members.remove(accountId)
        if (room.members.isEmpty()) rooms.remove(room.code)
    }

    private fun ensureNotWaiting(accountId: Long) {
        val room = waitingIn[accountId] ?: return
        throw ApiFailure(HttpStatusCode.Conflict, "already_in_room", "You're already waiting in room ${room.code}")
    }

    private fun noSuchRoom() = ApiFailure(HttpStatusCode.NotFound, "no_such_room", "There's no room with that code")

    private suspend fun info(room: Room): RoomInfo {
        val names = accounts.names(room.members)
        return RoomInfo(
            room.code,
            room.seats,
            room.members.map { RoomMember(it, names.getValue(it)) },
            room.gameId
        )
    }

    /** Drops rooms nobody has touched for a while. A started room stays briefly, so its members can see the game id. */
    private fun sweep() {
        val time = now()
        rooms.values.removeAll { room ->
            val limit = if (room.gameId == null) WAITING_LIFETIME else STARTED_LIFETIME
            val expired = time - room.lastChange > limit.inWholeMilliseconds
            if (expired && room.gameId == null) room.members.forEach(waitingIn::remove)
            expired
        }
    }

    private fun newCode(): String {
        while (true) {
            val code = String(CharArray(CODE_LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })
            if (code !in rooms) return code
        }
    }

    private fun normalize(code: String) = code.trim().uppercase()

    companion object {
        const val CODE_LENGTH = 6

        /** No 0/O, 1/I/L, so a code survives being read aloud. */
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val WAITING_LIFETIME = 30.minutes
        val STARTED_LIFETIME = 5.minutes
    }
}
