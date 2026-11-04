package com.tgaetaprojects.settledown.game.codec

import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.codec.SnapshotCodec.readOffer
import com.tgaetaprojects.settledown.game.codec.SnapshotCodec.writeOffer
import com.tgaetaprojects.settledown.game.engine.GameEvent
import com.tgaetaprojects.settledown.game.engine.ResourceCounts
import com.tgaetaprojects.settledown.utils.MutableEnumMap
import com.tgaetaprojects.settledown.utils.asReadOnly

/**
 * Encodes [GameEvent]s: a tag, then the event's fields. It writes exactly what it's given, so a private event stays
 * private only if the caller passes the result of `ActionResult.Accepted.eventsFor`, never the unredacted list.
 */
object EventCodec {
    /** One tag per event type, written as its ordinal (see [FORMAT_VERSION]). */
    internal enum class Tag {
        TURN_ORDER_DECIDED,
        TURN_STARTED,
        DICE_ROLLED,
        RESOURCES_PRODUCED,
        STARTING_RESOURCES,
        DISCARDED,
        ROBBER_MOVED,
        STOLE,
        ROAD_BUILT,
        SETTLEMENT_BUILT,
        CITY_BUILT,
        DEV_CARD_BOUGHT,
        DEV_CARD_PLAYED,
        YEAR_OF_PLENTY,
        MONOPOLIZED,
        MARITIME_TRADED,
        TRADE_PROPOSED,
        TRADE_ACCEPTED,
        TRADE_CANCELLED,
        LONGEST_ROAD_CHANGED,
        LARGEST_ARMY_CHANGED,
        GAME_WON,
    }

    fun encode(events: List<GameEvent>): ByteArray = BitWriter().apply {
        header(BlobKind.EVENTS)
        writeEvents(events)
    }.toByteArray()

    fun decode(bytes: ByteArray): List<GameEvent> = decoding("events", bytes) {
        header(BlobKind.EVENTS)
        readEvents()
    }

    internal fun BitWriter.writeEvents(events: List<GameEvent>) {
        varint(events.size)
        for (event in events) writeEvent(event)
    }

    internal fun BitReader.readEvents(): List<GameEvent> =
        List(count(minBitsEach = bitsFor(Tag.entries.size))) { readEvent() }

    private fun BitWriter.writeEvent(event: GameEvent) {
        code(tagOf(event))
        when (event) {
            is GameEvent.TurnOrderDecided -> writeTurnOrder(event.turnOrder)
            is GameEvent.TurnStarted -> {
                code(event.player)
                varint(event.turn)
            }

            is GameEvent.DiceRolled -> {
                code(event.player)
                code(event.roll)
            }

            is GameEvent.ResourcesProduced ->
                for (color in PlayerColor.entries) {
                    val gain = event.gains[color]
                    bool(gain != null)
                    if (gain != null) counts(gain)
                }

            is GameEvent.StartingResources -> {
                code(event.player)
                counts(event.cards)
            }

            is GameEvent.Discarded -> {
                code(event.player)
                counts(event.cards)
            }

            is GameEvent.RobberMoved -> {
                code(event.player)
                code(event.hex)
            }

            is GameEvent.Stole -> {
                code(event.thief)
                code(event.victim)
                optional(event.resource)
                bool(event.tookCard)
            }

            is GameEvent.RoadBuilt -> {
                code(event.player)
                code(event.edge)
                bool(event.free)
            }

            is GameEvent.SettlementBuilt -> {
                code(event.player)
                code(event.vertex)
                bool(event.free)
            }

            is GameEvent.CityBuilt -> {
                code(event.player)
                code(event.vertex)
            }

            is GameEvent.DevCardBought -> {
                code(event.player)
                optional(event.card)
            }

            is GameEvent.DevCardPlayed -> {
                code(event.player)
                code(event.card)
            }

            is GameEvent.YearOfPlenty -> {
                code(event.player)
                counts(event.cards)
            }

            is GameEvent.Monopolized -> {
                code(event.player)
                code(event.resource)
                for (color in PlayerColor.entries) {
                    val taken = event.taken[color]
                    bool(taken != null)
                    if (taken != null) varint(taken)
                }
            }

            is GameEvent.MaritimeTraded -> {
                code(event.player)
                counts(event.gave)
                counts(event.got)
            }

            is GameEvent.TradeProposed -> writeOffer(event.offer)
            is GameEvent.TradeAccepted -> writeOffer(event.offer)
            is GameEvent.TradeCancelled -> {
                writeOffer(event.offer)
                code(event.by)
            }

            is GameEvent.LongestRoadChanged -> {
                optional(event.holder)
                varint(event.length)
            }

            is GameEvent.LargestArmyChanged -> {
                code(event.holder)
                varint(event.knights)
            }

            is GameEvent.GameWon -> {
                code(event.player)
                varint(event.victoryPoints)
            }
        }
    }

    private fun BitReader.readEvent(): GameEvent = when (code<Tag>()) {
        Tag.TURN_ORDER_DECIDED -> GameEvent.TurnOrderDecided(readTurnOrder())
        Tag.TURN_STARTED -> GameEvent.TurnStarted(code(), varint())
        Tag.DICE_ROLLED -> GameEvent.DiceRolled(code(), code())
        Tag.RESOURCES_PRODUCED -> {
            val gains = MutableEnumMap<PlayerColor, ResourceCounts>()
            for (color in PlayerColor.entries) if (bool()) gains[color] = resourceCounts()
            GameEvent.ResourcesProduced(gains.asReadOnly())
        }

        Tag.STARTING_RESOURCES -> GameEvent.StartingResources(code(), resourceCounts())
        Tag.DISCARDED -> GameEvent.Discarded(code(), resourceCounts())
        Tag.ROBBER_MOVED -> GameEvent.RobberMoved(code(), code())
        Tag.STOLE -> GameEvent.Stole(code(), code(), optional(), bool()).also {
            require(it.resource == null || it.tookCard) { "A stolen card can't be stolen and not taken" }
        }

        Tag.ROAD_BUILT -> GameEvent.RoadBuilt(code(), code(), bool())
        Tag.SETTLEMENT_BUILT -> GameEvent.SettlementBuilt(code(), code(), bool())
        Tag.CITY_BUILT -> GameEvent.CityBuilt(code(), code())
        Tag.DEV_CARD_BOUGHT -> GameEvent.DevCardBought(code(), optional())
        Tag.DEV_CARD_PLAYED -> GameEvent.DevCardPlayed(code(), code())
        Tag.YEAR_OF_PLENTY -> GameEvent.YearOfPlenty(code(), resourceCounts())
        Tag.MONOPOLIZED -> {
            val player = code<PlayerColor>()
            val resource = code<Resource>()
            val taken = MutableEnumMap<PlayerColor, Int>()
            for (color in PlayerColor.entries) if (bool()) taken[color] = varint()
            GameEvent.Monopolized(player, resource, taken.asReadOnly())
        }

        Tag.MARITIME_TRADED -> GameEvent.MaritimeTraded(code(), resourceCounts(), resourceCounts())
        Tag.TRADE_PROPOSED -> GameEvent.TradeProposed(readOffer())
        Tag.TRADE_ACCEPTED -> GameEvent.TradeAccepted(readOffer())
        Tag.TRADE_CANCELLED -> GameEvent.TradeCancelled(readOffer(), code())
        Tag.LONGEST_ROAD_CHANGED -> GameEvent.LongestRoadChanged(optional(), varint())
        Tag.LARGEST_ARMY_CHANGED -> GameEvent.LargestArmyChanged(code(), varint())
        Tag.GAME_WON -> GameEvent.GameWon(code(), varint())
    }

    private fun BitWriter.writeTurnOrder(order: List<PlayerColor>) {
        bits(order.size - 3, 1)
        for (color in order) code(color)
    }

    private fun BitReader.readTurnOrder(): List<PlayerColor> =
        List(bits(1) + 3) { code<PlayerColor>() }.also {
            require(it.toSet().size == it.size) { "Duplicate colors $it" }
        }

    private fun tagOf(event: GameEvent): Tag = when (event) {
        is GameEvent.TurnOrderDecided -> Tag.TURN_ORDER_DECIDED
        is GameEvent.TurnStarted -> Tag.TURN_STARTED
        is GameEvent.DiceRolled -> Tag.DICE_ROLLED
        is GameEvent.ResourcesProduced -> Tag.RESOURCES_PRODUCED
        is GameEvent.StartingResources -> Tag.STARTING_RESOURCES
        is GameEvent.Discarded -> Tag.DISCARDED
        is GameEvent.RobberMoved -> Tag.ROBBER_MOVED
        is GameEvent.Stole -> Tag.STOLE
        is GameEvent.RoadBuilt -> Tag.ROAD_BUILT
        is GameEvent.SettlementBuilt -> Tag.SETTLEMENT_BUILT
        is GameEvent.CityBuilt -> Tag.CITY_BUILT
        is GameEvent.DevCardBought -> Tag.DEV_CARD_BOUGHT
        is GameEvent.DevCardPlayed -> Tag.DEV_CARD_PLAYED
        is GameEvent.YearOfPlenty -> Tag.YEAR_OF_PLENTY
        is GameEvent.Monopolized -> Tag.MONOPOLIZED
        is GameEvent.MaritimeTraded -> Tag.MARITIME_TRADED
        is GameEvent.TradeProposed -> Tag.TRADE_PROPOSED
        is GameEvent.TradeAccepted -> Tag.TRADE_ACCEPTED
        is GameEvent.TradeCancelled -> Tag.TRADE_CANCELLED
        is GameEvent.LongestRoadChanged -> Tag.LONGEST_ROAD_CHANGED
        is GameEvent.LargestArmyChanged -> Tag.LARGEST_ARMY_CHANGED
        is GameEvent.GameWon -> Tag.GAME_WON
    }
}
