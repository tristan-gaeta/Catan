package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.DevelopmentCardType
import com.tgaetaprojects.biophilia.game.DiceTotal
import com.tgaetaprojects.biophilia.game.Edge
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.Resource
import com.tgaetaprojects.biophilia.game.Vertex

/**
 * What happened as a result of an accepted [Action], in order. Some events carry private details (a stolen card, a
 * bought development card) that a future per-player view must hide from the other players.
 */
sealed interface GameEvent {
    data class TurnStarted(val player: PlayerColor, val turn: Int) : GameEvent
    data class DiceRolled(val player: PlayerColor, val roll: DiceTotal) : GameEvent
    data class ResourcesProduced(val gains: Map<PlayerColor, ResourceCounts>) : GameEvent
    data class StartingResources(val player: PlayerColor, val cards: ResourceCounts) : GameEvent
    data class Discarded(val player: PlayerColor, val cards: ResourceCounts) : GameEvent
    data class RobberMoved(val player: PlayerColor, val hex: Hex) : GameEvent
    /** [resource] is `null` when the victim had nothing to steal. Private to thief and victim. */
    data class Stole(val thief: PlayerColor, val victim: PlayerColor, val resource: Resource?) : GameEvent

    data class RoadBuilt(val player: PlayerColor, val edge: Edge, val free: Boolean) : GameEvent
    data class SettlementBuilt(val player: PlayerColor, val vertex: Vertex, val free: Boolean) : GameEvent
    data class CityBuilt(val player: PlayerColor, val vertex: Vertex) : GameEvent

    /** [card] is private to the buyer. */
    data class DevCardBought(val player: PlayerColor, val card: DevelopmentCardType) : GameEvent
    data class DevCardPlayed(val player: PlayerColor, val card: DevelopmentCardType) : GameEvent
    data class YearOfPlenty(val player: PlayerColor, val cards: ResourceCounts) : GameEvent
    data class Monopolized(val player: PlayerColor, val resource: Resource, val taken: Map<PlayerColor, Int>) : GameEvent

    data class MaritimeTraded(val player: PlayerColor, val gave: ResourceCounts, val got: ResourceCounts) : GameEvent
    data class TradeProposed(val offer: TradeOffer) : GameEvent
    data class TradeAccepted(val offer: TradeOffer) : GameEvent
    data class TradeCancelled(val offer: TradeOffer, val by: PlayerColor) : GameEvent

    /** [holder] is `null` when the card is set aside. */
    data class LongestRoadChanged(val holder: PlayerColor?, val length: Int) : GameEvent
    data class LargestArmyChanged(val holder: PlayerColor, val knights: Int) : GameEvent

    data class GameWon(val player: PlayerColor, val victoryPoints: Int) : GameEvent
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
    data class Accepted(val events: List<GameEvent>) : ActionResult
    data class Rejected(val violation: RuleViolation) : ActionResult
}
