package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.PlayerColor

/** Roads needed before anyone can hold the Longest Road card. */
internal const val MIN_LONGEST_ROAD = 5

/** Knights needed before anyone can hold the Largest Army card. */
internal const val MIN_LARGEST_ARMY = 3

/**
 * Who holds Longest Road given everyone's current [lengths], following the almanac:
 * - the [current] holder keeps it while nobody is strictly longer, even after their road is broken into a tie;
 * - otherwise it goes to the single longest road of at least [MIN_LONGEST_ROAD];
 * - if that longest length is shared, or nobody reaches the minimum, the card is set aside (`null`).
 */
internal fun longestRoadHolder(
    current: PlayerColor?,
    lengths: Map<PlayerColor, Int>
): PlayerColor? {
    val best = lengths.values.maxOrNull() ?: 0
    if (best < MIN_LONGEST_ROAD) return null
    if (current != null && lengths[current] == best) return current
    return lengths.filterValues { it == best }.keys.singleOrNull()
}

/**
 * Who holds Largest Army given everyone's played [knights]: the first player to [MIN_LARGEST_ARMY], until someone
 * has strictly more than the [current] holder. Knights are never lost, so the card is never set aside.
 */
internal fun largestArmyHolder(
    current: PlayerColor?,
    knights: Map<PlayerColor, Int>
): PlayerColor? {
    val toBeat = current?.let { knights.getValue(it) } ?: (MIN_LARGEST_ARMY - 1)
    return knights.filter { it.key != current && it.value > toBeat }.maxByOrNull { it.value }?.key
        ?: current
}
