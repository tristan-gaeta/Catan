package com.tgaetaprojects.settledown

import com.tgaetaprojects.settledown.api.ApiError
import com.tgaetaprojects.settledown.api.CreateRoomRequest
import com.tgaetaprojects.settledown.api.GameStatus
import com.tgaetaprojects.settledown.api.GameSummary
import com.tgaetaprojects.settledown.api.RoomInfo
import com.tgaetaprojects.settledown.rooms.RoomService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class RoomsTest {

    private suspend fun HttpClient.createRoom(seats: Int = 3): RoomInfo = post("/rooms") {
        contentType(ContentType.Application.Json)
        setBody(CreateRoomRequest(seats))
    }.body()

    private suspend fun HttpClient.joinRoom(code: String): RoomInfo = post("/rooms/$code/join").body()

    @Test
    fun create_validSeats_makesAWaitingRoom() = testServer {
        val room = signedIn("alice").createRoom(4)
        assertEquals(RoomService.CODE_LENGTH, room.code.length)
        assertEquals(4, room.seats)
        assertEquals(1, room.members.size)
        assertNull(room.gameId)
    }

    @Test
    fun create_invalidSeats_isRefused() = testServer {
        val alice = signedIn("alice")
        for (seats in listOf(2, 5)) {
            val response = alice.post("/rooms") {
                contentType(ContentType.Application.Json)
                setBody(CreateRoomRequest(seats))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, "$seats seats")
        }
    }

    @Test
    fun create_alreadyWaiting_isRefused() = testServer {
        val alice = signedIn("alice")
        alice.createRoom()
        val response = alice.post("/rooms") {
            contentType(ContentType.Application.Json)
            setBody(CreateRoomRequest(3))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("already_in_room", response.body<ApiError>().code)
    }

    @Test
    fun join_lowerCaseCode_findsTheRoom() = testServer {
        val code = signedIn("alice").createRoom().code
        val room = signedIn("bob").joinRoom(code.lowercase())
        assertEquals(2, room.members.size)
    }

    @Test
    fun join_unknownCode_isNotFound() = testServer {
        assertEquals(HttpStatusCode.NotFound, signedIn("bob").post("/rooms/ZZZZZZ/join").status)
    }

    @Test
    fun join_twice_keepsOneSeat() = testServer {
        val code = signedIn("alice").createRoom().code
        val bob = signedIn("bob")
        bob.joinRoom(code)
        assertEquals(2, bob.joinRoom(code).members.size)
    }

    @Test
    fun join_lastSeat_startsTheGameForEveryone() = testServer {
        val alice = signedIn("alice")
        val bob = signedIn("bob")
        val carol = signedIn("carol")
        val code = alice.createRoom().code
        assertNull(bob.joinRoom(code).gameId)

        val gameId = assertNotNull(carol.joinRoom(code).gameId)

        assertEquals(gameId, alice.get("/rooms/$code").body<RoomInfo>().gameId)
        for (player in listOf(alice, bob, carol)) {
            val game = player.get("/me/games").body<List<GameSummary>>().single()
            assertEquals(gameId, game.id)
            assertEquals(GameStatus.ACTIVE, game.status)
            assertEquals(3, game.seats.size)
            assertEquals(3, game.seats.map { it.color }.toSet().size)
            assertEquals(1, game.seats.count { it.isYou })
        }
    }

    @Test
    fun join_afterStart_isRefused() = testServer {
        val code = signedIn("alice").createRoom().code
        signedIn("bob").joinRoom(code)
        signedIn("carol").joinRoom(code)
        val response = signedIn("dave").post("/rooms/$code/join")
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("room_full", response.body<ApiError>().code)
    }

    @Test
    fun start_seatedPlayers_canWaitInANewRoom() = testServer {
        val alice = signedIn("alice")
        val code = alice.createRoom().code
        signedIn("bob").joinRoom(code)
        signedIn("carol").joinRoom(code)
        assertEquals(HttpStatusCode.NoContent, alice.get("/me/room").status)
        assertEquals(1, alice.createRoom().members.size)
    }

    @Test
    fun get_notAMember_isNotFound() = testServer {
        val code = signedIn("alice").createRoom().code
        assertEquals(HttpStatusCode.NotFound, signedIn("bob").get("/rooms/$code").status)
    }

    @Test
    fun leave_lastMember_removesTheRoom() = testServer {
        val alice = signedIn("alice")
        val code = alice.createRoom().code
        assertEquals(HttpStatusCode.NoContent, alice.post("/rooms/$code/leave").status)
        assertEquals(HttpStatusCode.NotFound, signedIn("bob").post("/rooms/$code/join").status)
    }

    @Test
    fun leave_oneMember_freesTheSeat() = testServer {
        val alice = signedIn("alice")
        val bob = signedIn("bob")
        val code = alice.createRoom().code
        bob.joinRoom(code)
        bob.post("/rooms/$code/leave")
        assertEquals(1, alice.get("/rooms/$code").body<RoomInfo>().members.size)
        assertEquals(HttpStatusCode.NoContent, bob.get("/me/room").status)
    }

    @Test
    fun room_idleTooLong_expires() {
        var time = 1_000L
        testServer(now = { time }) {
            val alice = signedIn("alice")
            val code = alice.createRoom().code
            time += RoomService.WAITING_LIFETIME.inWholeMilliseconds + 1.minutes.inWholeMilliseconds
            assertEquals(HttpStatusCode.NotFound, signedIn("bob").post("/rooms/$code/join").status)
            assertEquals(HttpStatusCode.NoContent, alice.get("/me/room").status)
        }
    }

    @Test
    fun join_tooManyTries_isLimited() = testServer {
        val bob = signedIn("bob")
        val statuses = List(25) { bob.post("/rooms/ZZZZZZ/join").status }
        assertTrue(HttpStatusCode.TooManyRequests in statuses)
    }

    @Test
    fun account_deleted_keepsTheSeatWithoutAName() = testServer {
        val alice = signedIn("alice")
        val bob = signedIn("bob")
        val code = alice.createRoom().code
        bob.joinRoom(code)
        signedIn("carol").joinRoom(code)

        alice.delete("/account")

        val seats = bob.get("/me/games").body<List<GameSummary>>().single().seats
        assertEquals(3, seats.size)
        assertEquals(1, seats.count { it.displayName == null })
    }

    @Test
    fun account_deletedWhileWaiting_leavesTheRoom() = testServer {
        val alice = signedIn("alice")
        val bob = signedIn("bob")
        val code = alice.createRoom().code
        bob.joinRoom(code)
        bob.delete("/account")
        assertEquals(1, alice.get("/rooms/$code").body<RoomInfo>().members.size)
    }
}
