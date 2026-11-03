package com.tgaetaprojects.settledown.game.engine

import com.tgaetaprojects.settledown.game.GameBoard
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.utils.EnumSet
import com.tgaetaprojects.settledown.utils.MutableEnumSet

/** Cards a player must discard when a 7 is rolled: half, rounded down, but only with more than 7 cards. */
internal fun discardAmount(handSize: Int): Int = if (handSize > 7) handSize / 2 else 0

/** The players [thief] may rob after moving the robber to [hex]: everyone else with a building on it. */
internal fun GameBoard.robberVictims(hex: Hex, thief: PlayerColor): EnumSet<PlayerColor> {
    val victims = MutableEnumSet<PlayerColor>()
    for (vertex in hex.vertices.values) {
        val owner = buildings[vertex]?.color ?: continue
        if (owner != thief) victims += owner
    }
    return victims
}
