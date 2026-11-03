package com.tgaetaprojects.settledown.game.codec

import com.tgaetaprojects.settledown.game.BoardLayout
import com.tgaetaprojects.settledown.game.Edge
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor.BLUE
import com.tgaetaprojects.settledown.game.PlayerColor.ORANGE
import com.tgaetaprojects.settledown.game.PlayerColor.RED
import com.tgaetaprojects.settledown.game.Resource.BRICK
import com.tgaetaprojects.settledown.game.Resource.GRAIN
import com.tgaetaprojects.settledown.game.Resource.LUMBER
import com.tgaetaprojects.settledown.game.Resource.ORE
import com.tgaetaprojects.settledown.game.Resource.WOOL
import com.tgaetaprojects.settledown.game.Vertex
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.ResourceCounts
import com.tgaetaprojects.settledown.game.engine.ResourceCounts.Companion.of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LayoutAndActionCodecTest {

    @Test
    fun layoutsRoundTripIn21Bytes() {
        for (seed in 0L until 200L) {
            val layout = BoardLayout.generate(seed)
            val bytes = LayoutCodec.encode(layout)
            assertEquals(21, bytes.size, "seed $seed")
            assertEquals(layout, LayoutCodec.decode(bytes), "seed $seed")
        }
    }

    @Test
    fun corruptLayoutsAreRejected() {
        val bytes = LayoutCodec.encode(BoardLayout.generate(1))

        assertFailsWith<CodecException> { LayoutCodec.decode(bytes.copyOf(bytes.size - 1)) }
        assertFailsWith<CodecException> { LayoutCodec.decode(bytes + 0) }
        assertFailsWith<CodecException> { LayoutCodec.decode(bytes.copyOf().also { it[0] = 99 }) }
        assertFailsWith<CodecException> {
            LayoutCodec.decode(
                bytes.copyOf().also { it[1] = BlobKind.ACTION.ordinal.toByte() })
        }
        // changing the first frame piece repeats a piece (or names none), which decoding re-validates
        assertFailsWith<CodecException> {
            LayoutCodec.decode(
                bytes.copyOf().also { it[2] = (it[2].toInt() xor 0b111).toByte() })
        }
    }

    @Test
    fun everyActionRoundTrips() {
        val actions = listOf(
            Action.PlaceSettlement(Vertex.V53),
            Action.PlaceRoad(Edge.E71),
            Action.RollDice,
            Action.Discard(of(BRICK to 3, WOOL to 1)),
            Action.MoveRobber(Hex.S, BLUE),
            Action.MoveRobber(Hex.A, null),
            Action.BuildRoad(Edge.E0),
            Action.BuildSettlement(Vertex.V0),
            Action.BuildCity(Vertex.V17),
            Action.BuyDevCard,
            Action.PlayKnight,
            Action.PlayRoadBuilding,
            Action.PlayYearOfPlenty(ORE, ORE),
            Action.PlayMonopoly(GRAIN),
            Action.MaritimeTrade(LUMBER, BRICK, lots = 300),
            Action.ProposeTrade(ORANGE, of(ORE, 2), of(WOOL to 1, GRAIN to 1)),
            Action.AcceptTrade(1_000_000),
            Action.CancelTrade(0),
            Action.EndTurn,
        )
        for (action in actions) {
            assertEquals(
                ORANGE to action,
                ActionCodec.decode(ActionCodec.encode(ORANGE, action)),
                "$action"
            )
        }
    }

    @Test
    fun commonActionsAreTiny() {
        // 2 header bytes, then the 2-bit actor, 5-bit tag and operands
        assertEquals(3, ActionCodec.encode(RED, Action.RollDice).size)
        assertEquals(4, ActionCodec.encode(RED, Action.BuildRoad(Edge.E40)).size)
        assertEquals(3, ActionCodec.encode(RED, Action.EndTurn).size)
    }

    @Test
    fun corruptActionsAreRejected() {
        assertFailsWith<CodecException> { ActionCodec.decode(byteArrayOf()) }
        // tag 31 doesn't exist
        assertFailsWith<CodecException> {
            ActionCodec.decode(
                byteArrayOf(
                    FORMAT_VERSION.toByte(),
                    BlobKind.ACTION.ordinal.toByte(),
                    0x7C
                )
            )
        }
        assertFailsWith<CodecException> {
            ActionCodec.decode(
                ActionCodec.encode(
                    RED,
                    Action.BuildRoad(Edge.E5)
                ).dropLast(1).toByteArray()
            )
        }
        assertFailsWith<CodecException> {
            ActionCodec.decode(
                LayoutCodec.encode(
                    BoardLayout.generate(
                        1
                    )
                )
            )
        }
    }

    @Test
    fun countsUseAll25Bits() {
        val full = ResourceCounts.FULL_BANK
        assertEquals(
            Action.Discard(full),
            ActionCodec.decode(ActionCodec.encode(RED, Action.Discard(full))).second
        )
    }
}
