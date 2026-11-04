package com.tgaetaprojects.settledown.game.engine

import com.tgaetaprojects.settledown.game.DevelopmentCardType
import com.tgaetaprojects.settledown.game.DiceTotal
import com.tgaetaprojects.settledown.game.Edge
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.Vertex

/**
 * What happened as a result of an accepted [Action], in order. Some events carry private details (a stolen card, a
 * bought development card); [visibleTo] hides them from the other players.
 */
sealed interface GameEvent {
    /** The first player has been rolled; [turnOrder] runs clockwise from them. */
    data class TurnOrderDecided(val turnOrder: List<PlayerColor>) : GameEvent

    data class TurnStarted(val player: PlayerColor, val turn: Int) : GameEvent
    data class DiceRolled(val player: PlayerColor, val roll: DiceTotal) : GameEvent
    data class ResourcesProduced(val gains: Map<PlayerColor, ResourceCounts>) : GameEvent
    data class StartingResources(val player: PlayerColor, val cards: ResourceCounts) : GameEvent
    data class Discarded(val player: PlayerColor, val cards: ResourceCounts) : GameEvent
    data class RobberMoved(val player: PlayerColor, val hex: Hex) : GameEvent

    /**
     * [resource] is private to thief and victim; others see it as `null` with [tookCard] still telling them a card
     * changed hands. With [tookCard] false the victim had nothing to steal.
     */
    data class Stole(
        val thief: PlayerColor,
        val victim: PlayerColor,
        val resource: Resource?,
        val tookCard: Boolean = resource != null
    ) : GameEvent

    data class RoadBuilt(val player: PlayerColor, val edge: Edge, val free: Boolean) : GameEvent
    data class SettlementBuilt(val player: PlayerColor, val vertex: Vertex, val free: Boolean) :
        GameEvent

    data class CityBuilt(val player: PlayerColor, val vertex: Vertex) : GameEvent

    /** [card] is private to the buyer; others see `null`. */
    data class DevCardBought(val player: PlayerColor, val card: DevelopmentCardType?) : GameEvent
    data class DevCardPlayed(val player: PlayerColor, val card: DevelopmentCardType) : GameEvent
    data class YearOfPlenty(val player: PlayerColor, val cards: ResourceCounts) : GameEvent
    data class Monopolized(
        val player: PlayerColor,
        val resource: Resource,
        val taken: Map<PlayerColor, Int>
    ) : GameEvent

    data class MaritimeTraded(
        val player: PlayerColor,
        val gave: ResourceCounts,
        val got: ResourceCounts
    ) : GameEvent

    data class TradeProposed(val offer: TradeOffer) : GameEvent
    data class TradeAccepted(val offer: TradeOffer) : GameEvent
    data class TradeCancelled(val offer: TradeOffer, val by: PlayerColor) : GameEvent

    /** [holder] is `null` when the card is set aside. */
    data class LongestRoadChanged(val holder: PlayerColor?, val length: Int) : GameEvent
    data class LargestArmyChanged(val holder: PlayerColor, val knights: Int) : GameEvent

    data class GameWon(val player: PlayerColor, val victoryPoints: Int) : GameEvent

    /**
     * This event as [viewer] may see it: a stolen card's type is hidden from everyone but thief and victim, and a
     * bought development card from everyone but the buyer. Everything else in the base game is public.
     */
    fun visibleTo(viewer: PlayerColor): GameEvent = when (this) {
        is Stole -> if (viewer == thief || viewer == victim) this else publicView()
        is DevCardBought -> if (viewer == player) this else publicView()
        else -> this
    }

    /** This event as a spectator sees it, with every private detail hidden; e.g. for a log on a shared screen. */
    fun publicView(): GameEvent = when (this) {
        is Stole -> copy(resource = null)
        is DevCardBought -> copy(card = null)
        else -> this
    }
}

/** Why an [Action] was refused. A refused action leaves the game untouched. */
enum class RuleViolation {
    UNKNOWN_PLAYER,
    GAME_OVER,
    NOT_YOUR_TURN,
    WRONG_PHASE,
    ILLEGAL_LOCATION,
    INSUFFICIENT_RESOURCES,
    NO_PIECES_LEFT,
    BANK_SHORTAGE,
    DECK_EMPTY,
    DEV_CARD_ALREADY_PLAYED,
    NO_PLAYABLE_CARD,
    INVALID_DISCARD,
    INVALID_ROBBER_MOVE,
    INVALID_VICTIM,
    INVALID_TRADE,
    NO_SUCH_OFFER,
}

/** The outcome of [Game.apply]. */
sealed interface ActionResult {
    /**
     * [events] are the server's unredacted record, including stolen card types and bought cards. Send each player
     * [eventsFor] that player, never [events] itself.
     */
    data class Accepted(val events: List<GameEvent>) : ActionResult {
        /** The events as [viewer] may see them; see [GameEvent.visibleTo]. */
        fun eventsFor(viewer: PlayerColor): List<GameEvent> = events.map { it.visibleTo(viewer) }
    }

    data class Rejected(val violation: RuleViolation) : ActionResult
}
