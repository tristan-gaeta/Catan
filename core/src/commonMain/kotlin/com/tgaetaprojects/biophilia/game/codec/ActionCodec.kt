package com.tgaetaprojects.biophilia.game.codec

import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.engine.Action

/**
 * Encodes who did what: the 2-bit actor code, a 5-bit action tag, then the action's operands. Rolling the dice
 * takes 7 bits and building a road 14; only trade offers, which carry two hands of cards, run to about 8 bytes.
 */
object ActionCodec {
    /** One fixed tag per action type. Tags are part of the format: never renumber them, only append. */
    private object Tag {
        const val WIDTH = 5

        const val PLACE_SETTLEMENT = 0
        const val PLACE_ROAD = 1
        const val ROLL_DICE = 2
        const val DISCARD = 3
        const val MOVE_ROBBER = 4
        const val BUILD_ROAD = 5
        const val BUILD_SETTLEMENT = 6
        const val BUILD_CITY = 7
        const val BUY_DEV_CARD = 8
        const val PLAY_KNIGHT = 9
        const val PLAY_ROAD_BUILDING = 10
        const val PLAY_YEAR_OF_PLENTY = 11
        const val PLAY_MONOPOLY = 12
        const val MARITIME_TRADE = 13
        const val PROPOSE_TRADE = 14
        const val ACCEPT_TRADE = 15
        const val CANCEL_TRADE = 16
        const val END_TURN = 17
    }

    fun encode(actor: PlayerColor, action: Action): ByteArray = BitWriter().apply {
        header(BlobKind.ACTION)
        writeAction(actor, action)
    }.toByteArray()

    fun decode(bytes: ByteArray): Pair<PlayerColor, Action> = decoding("action", bytes) {
        header(BlobKind.ACTION)
        readAction()
    }

    internal fun BitWriter.writeAction(actor: PlayerColor, action: Action) {
        code(actor, Codes.COLOR)
        bits(tagOf(action), Tag.WIDTH)
        when (action) {
            is Action.PlaceSettlement -> vertex(action.vertex)
            is Action.PlaceRoad -> edge(action.edge)
            Action.RollDice -> Unit
            is Action.Discard -> counts(action.cards)
            is Action.MoveRobber -> {
                code(action.hex, Codes.HEX)
                optional(action.victim, Codes.COLOR)
            }
            is Action.BuildRoad -> edge(action.edge)
            is Action.BuildSettlement -> vertex(action.vertex)
            is Action.BuildCity -> vertex(action.vertex)
            Action.BuyDevCard, Action.PlayKnight, Action.PlayRoadBuilding, Action.EndTurn -> Unit
            is Action.PlayYearOfPlenty -> {
                code(action.first, Codes.RESOURCE)
                code(action.second, Codes.RESOURCE)
            }
            is Action.PlayMonopoly -> code(action.resource, Codes.RESOURCE)
            is Action.MaritimeTrade -> {
                code(action.give, Codes.RESOURCE)
                code(action.get, Codes.RESOURCE)
                varint(action.lots)
            }
            is Action.ProposeTrade -> {
                code(action.to, Codes.COLOR)
                counts(action.give)
                counts(action.get)
            }
            is Action.AcceptTrade -> varint(action.offerId)
            is Action.CancelTrade -> varint(action.offerId)
        }
    }

    internal fun BitReader.readAction(): Pair<PlayerColor, Action> {
        val actor = code(Codes.COLOR)
        val action = when (val tag = bits(Tag.WIDTH)) {
            Tag.PLACE_SETTLEMENT -> Action.PlaceSettlement(vertex())
            Tag.PLACE_ROAD -> Action.PlaceRoad(edge())
            Tag.ROLL_DICE -> Action.RollDice
            Tag.DISCARD -> Action.Discard(resourceCounts())
            Tag.MOVE_ROBBER -> Action.MoveRobber(code(Codes.HEX), optional(Codes.COLOR))
            Tag.BUILD_ROAD -> Action.BuildRoad(edge())
            Tag.BUILD_SETTLEMENT -> Action.BuildSettlement(vertex())
            Tag.BUILD_CITY -> Action.BuildCity(vertex())
            Tag.BUY_DEV_CARD -> Action.BuyDevCard
            Tag.PLAY_KNIGHT -> Action.PlayKnight
            Tag.PLAY_ROAD_BUILDING -> Action.PlayRoadBuilding
            Tag.PLAY_YEAR_OF_PLENTY -> Action.PlayYearOfPlenty(code(Codes.RESOURCE), code(Codes.RESOURCE))
            Tag.PLAY_MONOPOLY -> Action.PlayMonopoly(code(Codes.RESOURCE))
            Tag.MARITIME_TRADE -> Action.MaritimeTrade(code(Codes.RESOURCE), code(Codes.RESOURCE), varint())
            Tag.PROPOSE_TRADE -> Action.ProposeTrade(code(Codes.COLOR), resourceCounts(), resourceCounts())
            Tag.ACCEPT_TRADE -> Action.AcceptTrade(varint())
            Tag.CANCEL_TRADE -> Action.CancelTrade(varint())
            Tag.END_TURN -> Action.EndTurn
            else -> throw CodecException("Unknown action tag $tag")
        }
        return actor to action
    }

    private fun tagOf(action: Action): Int = when (action) {
        is Action.PlaceSettlement -> Tag.PLACE_SETTLEMENT
        is Action.PlaceRoad -> Tag.PLACE_ROAD
        Action.RollDice -> Tag.ROLL_DICE
        is Action.Discard -> Tag.DISCARD
        is Action.MoveRobber -> Tag.MOVE_ROBBER
        is Action.BuildRoad -> Tag.BUILD_ROAD
        is Action.BuildSettlement -> Tag.BUILD_SETTLEMENT
        is Action.BuildCity -> Tag.BUILD_CITY
        Action.BuyDevCard -> Tag.BUY_DEV_CARD
        Action.PlayKnight -> Tag.PLAY_KNIGHT
        Action.PlayRoadBuilding -> Tag.PLAY_ROAD_BUILDING
        is Action.PlayYearOfPlenty -> Tag.PLAY_YEAR_OF_PLENTY
        is Action.PlayMonopoly -> Tag.PLAY_MONOPOLY
        is Action.MaritimeTrade -> Tag.MARITIME_TRADE
        is Action.ProposeTrade -> Tag.PROPOSE_TRADE
        is Action.AcceptTrade -> Tag.ACCEPT_TRADE
        is Action.CancelTrade -> Tag.CANCEL_TRADE
        Action.EndTurn -> Tag.END_TURN
    }
}
