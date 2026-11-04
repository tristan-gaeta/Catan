package com.tgaetaprojects.settledown.game.codec

import com.tgaetaprojects.settledown.game.DevelopmentCardType.KNIGHT
import com.tgaetaprojects.settledown.game.DevelopmentCardType.MONOPOLY
import com.tgaetaprojects.settledown.game.DiceTotal
import com.tgaetaprojects.settledown.game.Edge
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.PlayerColor.BLUE
import com.tgaetaprojects.settledown.game.PlayerColor.ORANGE
import com.tgaetaprojects.settledown.game.PlayerColor.RED
import com.tgaetaprojects.settledown.game.PlayerColor.WHITE
import com.tgaetaprojects.settledown.game.Resource.BRICK
import com.tgaetaprojects.settledown.game.Resource.ORE
import com.tgaetaprojects.settledown.game.Resource.WOOL
import com.tgaetaprojects.settledown.game.Vertex
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.ActionResult
import com.tgaetaprojects.settledown.game.engine.Game
import com.tgaetaprojects.settledown.game.engine.GameEvent
import com.tgaetaprojects.settledown.game.engine.RandomBots
import com.tgaetaprojects.settledown.game.engine.ResourceCounts.Companion.of
import com.tgaetaprojects.settledown.game.engine.RuleViolation
import com.tgaetaprojects.settledown.game.engine.TradeOffer
import com.tgaetaprojects.settledown.game.engine.viewFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WireCodecTest {

    private val offer = TradeOffer(3, RED, WHITE, of(ORE, 1), of(BRICK to 2))

    /** One of every event type, and both forms of the ones with private details. */
    private val everyEvent = listOf(
        GameEvent.TurnOrderDecided(listOf(WHITE, BLUE, RED)),
        GameEvent.TurnOrderDecided(listOf(ORANGE, RED, WHITE, BLUE)),
        GameEvent.TurnStarted(BLUE, 300),
        GameEvent.DiceRolled(RED, DiceTotal.SEVEN),
        GameEvent.ResourcesProduced(mapOf(RED to of(ORE, 2), ORANGE to of(WOOL, 1))),
        GameEvent.ResourcesProduced(emptyMap()),
        GameEvent.StartingResources(WHITE, of(BRICK to 1, ORE to 2)),
        GameEvent.Discarded(BLUE, of(WOOL, 4)),
        GameEvent.RobberMoved(RED, Hex.K),
        GameEvent.Stole(RED, WHITE, ORE),
        GameEvent.Stole(RED, WHITE, null, tookCard = true),
        GameEvent.Stole(RED, WHITE, null, tookCard = false),
        GameEvent.RoadBuilt(RED, Edge.E30, free = true),
        GameEvent.SettlementBuilt(WHITE, Vertex.V20, free = false),
        GameEvent.CityBuilt(BLUE, Vertex.V22),
        GameEvent.DevCardBought(ORANGE, KNIGHT),
        GameEvent.DevCardBought(ORANGE, null),
        GameEvent.DevCardPlayed(RED, MONOPOLY),
        GameEvent.YearOfPlenty(RED, of(ORE, BRICK)),
        GameEvent.Monopolized(RED, WOOL, mapOf(WHITE to 3, BLUE to 0)),
        GameEvent.MaritimeTraded(WHITE, of(WOOL, 4), of(ORE, 1)),
        GameEvent.TradeProposed(offer),
        GameEvent.TradeAccepted(offer),
        GameEvent.TradeCancelled(offer, WHITE),
        GameEvent.LongestRoadChanged(RED, 5),
        GameEvent.LongestRoadChanged(null, 0),
        GameEvent.LargestArmyChanged(BLUE, 3),
        GameEvent.GameWon(ORANGE, 10),
    )

    private fun view() = Game(listOf(RED, WHITE, BLUE), 5L, 6L).viewFor(WHITE)

    // ---- Events ----

    @Test
    fun eventCodec_everyEventType_roundTrips() {
        assertEquals(everyEvent, EventCodec.decode(EventCodec.encode(everyEvent)))
    }

    @Test
    fun eventCodec_emptyList_roundTrips() {
        assertEquals(emptyList(), EventCodec.decode(EventCodec.encode(emptyList())))
    }

    @Test
    fun eventCodec_randomGames_roundTripForEverySeat() {
        for (seed in 1L..6L) {
            val game = RandomBots.game(seed)
            RandomBots.play(game, seed) { _, _, events ->
                val accepted = ActionResult.Accepted(events)
                for (seat in game.seatOrder) {
                    val visible = accepted.eventsFor(seat)
                    assertEquals(visible, EventCodec.decode(EventCodec.encode(visible)), "seed $seed")
                }
            }
        }
    }

    @Test
    fun eventCodec_stolenCardTakenFalse_isRejected() {
        // the field order is thief, victim, resource, tookCard, so flipping the last bit of this event's data
        // produces a card that was stolen and yet not taken
        val bytes = EventCodec.encode(listOf(GameEvent.Stole(RED, WHITE, ORE)))
        val lastBitSet = (bytes.size * 8 - 1 downTo 0).first { bytes[it / 8].toInt() shr (it % 8) and 1 != 0 }
        val corrupted = bytes.copyOf().also { it[lastBitSet / 8] = (it[lastBitSet / 8].toInt() xor (1 shl (lastBitSet % 8))).toByte() }
        assertFailsWith<CodecException> { EventCodec.decode(corrupted) }
    }

    @Test
    fun eventCodec_claimedCountBeyondData_isRejected() {
        val bytes = BitWriter().apply {
            header(BlobKind.EVENTS)
            varint(1_000_000)
        }.toByteArray()
        assertFailsWith<CodecException> { EventCodec.decode(bytes) }
    }

    // ---- Client messages ----

    @Test
    fun clientAction_roundTrips() {
        val actions = listOf(
            Action.RollDice,
            Action.BuildRoad(Edge.E31),
            Action.MoveRobber(Hex.K, BLUE),
            Action.ProposeTrade(WHITE, of(ORE, 1), of(BRICK, 1)),
        )
        for ((index, action) in actions.withIndex()) {
            val message = ClientAction(index * 1000 + 1, action)
            assertEquals(message, WireCodec.decodeClientAction(WireCodec.encode(message)))
        }
    }

    @Test
    fun clientAction_seqZero_isRejected() {
        val bytes = BitWriter().apply {
            header(BlobKind.CLIENT_MESSAGE)
            varint(0)
        }.toByteArray() + WireCodec.encode(ClientAction(1, Action.RollDice)).copyOfRange(3, 4)
        assertFailsWith<CodecException> { WireCodec.decodeClientAction(bytes) }
    }

    @Test
    fun clientAction_everyTruncation_isRejected() {
        val bytes = WireCodec.encode(ClientAction(9, Action.ProposeTrade(WHITE, of(ORE, 1), of(BRICK, 1))))
        for (length in 0 until bytes.size) {
            assertFailsWith<CodecException>("length $length") {
                WireCodec.decodeClientAction(bytes.copyOf(length))
            }
        }
    }

    @Test
    fun clientAction_anotherBlobKind_isRejected() {
        val action = ActionCodec.encode(RED, Action.RollDice)
        assertFailsWith<CodecException> { WireCodec.decodeClientAction(action) }
    }

    // ---- Server messages ----

    @Test
    fun update_roundTrips() {
        val message = ServerMessage.Update(view(), everyEvent, lastClientSeq = 42)
        assertEquals(message, WireCodec.decodeServerMessage(WireCodec.encode(message)))
    }

    @Test
    fun refused_everyViolation_roundTrips() {
        for (violation in RuleViolation.entries) {
            val message = ServerMessage.Refused(7, violation)
            assertEquals(message, WireCodec.decodeServerMessage(WireCodec.encode(message)))
        }
    }

    @Test
    fun presence_roundTrips() {
        for (seats in listOf(emptySet(), setOf(WHITE), PlayerColor.entries.toSet())) {
            val message = ServerMessage.Presence(seats)
            assertEquals(message, WireCodec.decodeServerMessage(WireCodec.encode(message)))
        }
    }

    @Test
    fun deadline_roundTrips() {
        for (message in listOf(
            ServerMessage.Deadline(setOf(RED, ORANGE), 90_000),
            ServerMessage.Deadline(emptySet(), 0),
        )) {
            assertEquals(message, WireCodec.decodeServerMessage(WireCodec.encode(message)))
        }
    }

    @Test
    fun serverMessage_everyTruncation_isRejected() {
        val bytes = WireCodec.encode(ServerMessage.Update(view(), everyEvent, 1))
        for (length in 0 until bytes.size) {
            assertFailsWith<CodecException>("length $length") {
                WireCodec.decodeServerMessage(bytes.copyOf(length))
            }
        }
    }

    @Test
    fun update_viewForOneSeat_holdsOnlyCountsOfOthersCards() {
        val game = Game(listOf(RED, WHITE, BLUE), 5L, 6L)
        game.player(RED).hand = of(ORE, 3)
        val decoded = WireCodec.decodeServerMessage(WireCodec.encode(ServerMessage.Update(game.viewFor(WHITE), emptyList(), 0)))
        val update = assertIs<ServerMessage.Update>(decoded)
        assertEquals(WHITE, update.view.viewer)
        assertEquals(0, update.view.hand.total)
        assertEquals(3, update.view.summary(RED).handSize)
    }
}
