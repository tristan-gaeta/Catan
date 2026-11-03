package com.tgaetaprojects.settledown.game.codec

import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.engine.Action

/**
 * Encodes who did what: the 2-bit actor, a 5-bit action tag, then the action's operands. Rolling the dice takes
 * 7 bits and building a road 14; only trade offers, which carry two hands of cards, run to about 8 bytes.
 *
 * Diagram: [Bit layout of an encoded action](https://github.com/tristan-gaeta/Catan/blob/main/docs/images/action-bits.svg).
 */
object ActionCodec {
    /** One tag per action type, written as its ordinal (see [FORMAT_VERSION]). */
    private enum class Tag {
        PLACE_SETTLEMENT,
        PLACE_ROAD,
        ROLL_DICE,
        DISCARD,
        MOVE_ROBBER,
        BUILD_ROAD,
        BUILD_SETTLEMENT,
        BUILD_CITY,
        BUY_DEV_CARD,
        PLAY_KNIGHT,
        PLAY_ROAD_BUILDING,
        PLAY_YEAR_OF_PLENTY,
        PLAY_MONOPOLY,
        MARITIME_TRADE,
        PROPOSE_TRADE,
        ACCEPT_TRADE,
        CANCEL_TRADE,
        END_TURN,
    }

    /** The smallest possible encoded action: an actor and a tag with no operands. */
    internal val MIN_ACTION_BITS = bitsFor(PlayerColor.entries.size) + bitsFor(Tag.entries.size)

    fun encode(actor: PlayerColor, action: Action): ByteArray = BitWriter().apply {
        header(BlobKind.ACTION)
        writeAction(actor, action)
    }.toByteArray()

    fun decode(bytes: ByteArray): Pair<PlayerColor, Action> = decoding("action", bytes) {
        header(BlobKind.ACTION)
        readAction()
    }

    internal fun BitWriter.writeAction(actor: PlayerColor, action: Action) {
        code(actor)
        code(tagOf(action))
        when (action) {
            is Action.PlaceSettlement -> code(action.vertex)
            is Action.PlaceRoad -> code(action.edge)
            Action.RollDice -> Unit
            is Action.Discard -> counts(action.cards)
            is Action.MoveRobber -> {
                code(action.hex)
                optional(action.victim)
            }

            is Action.BuildRoad -> code(action.edge)
            is Action.BuildSettlement -> code(action.vertex)
            is Action.BuildCity -> code(action.vertex)
            Action.BuyDevCard, Action.PlayKnight, Action.PlayRoadBuilding, Action.EndTurn -> Unit
            is Action.PlayYearOfPlenty -> {
                code(action.first)
                code(action.second)
            }

            is Action.PlayMonopoly -> code(action.resource)
            is Action.MaritimeTrade -> {
                code(action.give)
                code(action.get)
                varint(action.lots)
            }

            is Action.ProposeTrade -> {
                code(action.to)
                counts(action.give)
                counts(action.get)
            }

            is Action.AcceptTrade -> varint(action.offerId)
            is Action.CancelTrade -> varint(action.offerId)
        }
    }

    internal fun BitReader.readAction(): Pair<PlayerColor, Action> {
        val actor = code<PlayerColor>()
        val action = when (code<Tag>()) {
            Tag.PLACE_SETTLEMENT -> Action.PlaceSettlement(code())
            Tag.PLACE_ROAD -> Action.PlaceRoad(code())
            Tag.ROLL_DICE -> Action.RollDice
            Tag.DISCARD -> Action.Discard(resourceCounts())
            Tag.MOVE_ROBBER -> Action.MoveRobber(code(), optional())
            Tag.BUILD_ROAD -> Action.BuildRoad(code())
            Tag.BUILD_SETTLEMENT -> Action.BuildSettlement(code())
            Tag.BUILD_CITY -> Action.BuildCity(code())
            Tag.BUY_DEV_CARD -> Action.BuyDevCard
            Tag.PLAY_KNIGHT -> Action.PlayKnight
            Tag.PLAY_ROAD_BUILDING -> Action.PlayRoadBuilding
            Tag.PLAY_YEAR_OF_PLENTY -> Action.PlayYearOfPlenty(code(), code())
            Tag.PLAY_MONOPOLY -> Action.PlayMonopoly(code())
            Tag.MARITIME_TRADE -> Action.MaritimeTrade(code(), code(), varint())
            Tag.PROPOSE_TRADE -> Action.ProposeTrade(code(), resourceCounts(), resourceCounts())
            Tag.ACCEPT_TRADE -> Action.AcceptTrade(varint())
            Tag.CANCEL_TRADE -> Action.CancelTrade(varint())
            Tag.END_TURN -> Action.EndTurn
        }
        return actor to action
    }

    private fun tagOf(action: Action): Tag = when (action) {
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
