package com.tgaetaprojects.settledown.ui.game

import com.tgaetaprojects.settledown.game.DevelopmentCardType
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.PortType
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.engine.GameEvent
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.ResourceCounts
import com.tgaetaprojects.settledown.game.engine.RuleViolation

/* English labels for the engine's enums and events. */

val PlayerColor.label: String
    get() = when (this) {
        PlayerColor.RED -> "Red"
        PlayerColor.WHITE -> "White"
        PlayerColor.BLUE -> "Blue"
        PlayerColor.ORANGE -> "Orange"
    }

val Resource.label: String
    get() = when (this) {
        Resource.BRICK -> "Brick"
        Resource.LUMBER -> "Lumber"
        Resource.ORE -> "Ore"
        Resource.GRAIN -> "Grain"
        Resource.WOOL -> "Wool"
    }

val DevelopmentCardType.label: String
    get() = when (this) {
        DevelopmentCardType.KNIGHT -> "Knight"
        DevelopmentCardType.VICTORY_POINT -> "Victory point"
        DevelopmentCardType.ROAD_BUILDING -> "Road building"
        DevelopmentCardType.MONOPOLY -> "Monopoly"
        DevelopmentCardType.YEAR_OF_PLENTY -> "Year of plenty"
    }

val BuildTool.label: String
    get() = when (this) {
        BuildTool.ROAD -> "Road"
        BuildTool.SETTLEMENT -> "Settlement"
        BuildTool.CITY -> "City"
    }

val PortType.label: String get() = "${exchangeRate}:1"

/** e.g. "2 Brick, 1 Wool", or "nothing". */
fun ResourceCounts.describe(): String =
    if (isEmpty) "nothing" else types.joinToString { "${get(it)} ${it.label}" }

/** What [actor] is asked to do in [phase], for the status line. */
fun prompt(phase: Phase, actor: PlayerColor): String = when (phase) {
    is Phase.RollForFirstPlayer -> "${actor.label}, roll to decide who goes first"
    is Phase.SetupSettlement -> "${actor.label}, place a free settlement (round ${phase.round})"
    is Phase.SetupRoad -> "${actor.label}, place a free road next to it"
    Phase.PreRoll -> "${actor.label}, roll the dice"
    is Phase.Discard -> "${actor.label}, discard ${phase.pending.getValue(actor)} cards"
    is Phase.MoveRobber -> "${actor.label}, tap a tile to move the robber"
    Phase.Main -> "${actor.label}, trade and build"
    is Phase.RoadBuilding -> "${actor.label}, place ${phase.remaining} free road${if (phase.remaining == 1) "" else "s"}"
    is Phase.GameOver -> "${phase.winner.label} wins!"
}

fun RuleViolation.message(): String = when (this) {
    RuleViolation.UNKNOWN_PLAYER -> "That player isn't in this game."
    RuleViolation.GAME_OVER -> "The game is over."
    RuleViolation.NOT_YOUR_TURN -> "It's not your turn."
    RuleViolation.WRONG_PHASE -> "You can't do that now."
    RuleViolation.ILLEGAL_LOCATION -> "You can't build there."
    RuleViolation.INSUFFICIENT_RESOURCES -> "You don't have the cards for that."
    RuleViolation.NO_PIECES_LEFT -> "You have no pieces of that kind left."
    RuleViolation.BANK_SHORTAGE -> "The bank can't pay that out."
    RuleViolation.DECK_EMPTY -> "No development cards are left."
    RuleViolation.DEV_CARD_ALREADY_PLAYED -> "You've already played a development card this turn."
    RuleViolation.NO_PLAYABLE_CARD -> "You have no card of that kind you can play yet."
    RuleViolation.INVALID_DISCARD -> "Discard exactly the number of cards owed."
    RuleViolation.INVALID_ROBBER_MOVE -> "Move the robber to a different tile."
    RuleViolation.INVALID_VICTIM -> "Choose someone with a building on that tile."
    RuleViolation.INVALID_TRADE -> "That trade isn't allowed."
    RuleViolation.NO_SUCH_OFFER -> "That offer is gone."
}

fun describe(event: GameEvent): String = when (event) {
    is GameEvent.TurnOrderDecided -> "${event.turnOrder.first().label} goes first"
    is GameEvent.TurnStarted -> "Turn ${event.turn}: ${event.player.label}"
    is GameEvent.DiceRolled -> "${event.player.label} rolled ${event.roll.value}"
    is GameEvent.ResourcesProduced ->
        if (event.gains.isEmpty()) "No one produced anything"
        else event.gains.entries.joinToString("; ") { (p, cards) -> "${p.label} got ${cards.describe()}" }

    is GameEvent.StartingResources -> "${event.player.label} got ${event.cards.describe()}"
    is GameEvent.Discarded -> "${event.player.label} discarded ${event.cards.describe()}"
    is GameEvent.RobberMoved -> "${event.player.label} moved the robber"
    is GameEvent.Stole -> {
        val resource = event.resource
        when {
            !event.tookCard -> "${event.victim.label} had nothing to steal"
            resource != null -> "${event.thief.label} stole 1 ${resource.label} from ${event.victim.label}"
            else -> "${event.thief.label} stole a card from ${event.victim.label}"
        }
    }

    is GameEvent.RoadBuilt -> "${event.player.label} built a road"
    is GameEvent.SettlementBuilt -> "${event.player.label} built a settlement"
    is GameEvent.CityBuilt -> "${event.player.label} built a city"
    is GameEvent.DevCardBought ->
        "${event.player.label} bought ${event.card?.let { "a ${it.label} card" } ?: "a development card"}"

    is GameEvent.DevCardPlayed -> "${event.player.label} played ${event.card.label}"
    is GameEvent.YearOfPlenty -> "${event.player.label} took ${event.cards.describe()}"
    is GameEvent.Monopolized ->
        "${event.player.label} took ${event.taken.values.sum()} ${event.resource.label}"

    is GameEvent.MaritimeTraded ->
        "${event.player.label} traded ${event.gave.describe()} for ${event.got.describe()}"

    is GameEvent.TradeProposed -> "${event.offer.from.label} offered ${event.offer.to.label} a trade"
    is GameEvent.TradeAccepted -> "${event.offer.to.label} accepted ${event.offer.from.label}'s trade"
    is GameEvent.TradeCancelled -> "${event.by.label} withdrew a trade"
    is GameEvent.LongestRoadChanged ->
        event.holder?.let { "${it.label} has the longest road (${event.length})" } ?: "No one has the longest road"

    is GameEvent.LargestArmyChanged -> "${event.holder.label} has the largest army (${event.knights})"
    is GameEvent.GameWon -> "${event.player.label} wins with ${event.victoryPoints} points!"
}
