package com.tgaetaprojects.settledown.game.engine

import com.tgaetaprojects.settledown.game.GameBoard
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Resource

/** The maritime rate available to everyone, harbor or not. */
internal const val BANK_RATE = 4

/** The most lots one maritime trade can take: the bank never holds more than 19 of a resource. */
internal const val MAX_MARITIME_LOTS = 19

/**
 * The best bank rate [color] gets when giving [resource]: 2 at that resource's harbor, 3 at a generic harbor,
 * 4 otherwise. A harbor counts if the player has a settlement or city on either end of its edge.
 */
internal fun GameBoard.maritimeRate(color: PlayerColor, resource: Resource): Int =
    buildings.entries
        .filter { it.value.color == color }
        .mapNotNull { harborAt(it.key) }
        .filter { resource in it.accepted }
        .minOfOrNull { it.exchangeRate } ?: BANK_RATE

/**
 * A domestic trade offer: [from] gives [give] to [to] in return for [get]. One of the two players is always the
 * player whose turn it is; the other accepts or declines.
 */
data class TradeOffer(
    val id: Int,
    val from: PlayerColor,
    val to: PlayerColor,
    val give: ResourceCounts,
    val get: ResourceCounts
)

/** No gifts (both sides non-empty) and no like-for-like swaps (no resource on both sides). */
internal fun isValidTradeShape(give: ResourceCounts, get: ResourceCounts): Boolean =
    !give.isEmpty && !get.isEmpty && give.isDisjointFrom(get)
