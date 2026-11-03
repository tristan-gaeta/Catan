package com.tgaetaprojects.settledown.game.engine

import com.tgaetaprojects.settledown.game.Edge
import com.tgaetaprojects.settledown.game.GameBoard
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Vertex
import com.tgaetaprojects.settledown.utils.MutableEnumSet

/**
 * The length of [color]'s longest road: the longest trail through its roads that uses no road twice. Branches
 * don't add up, but a loop counts in full. A trail may end at an opponent's building but can't pass through it,
 * which is how a settlement breaks a road.
 *
 * A player has at most 15 roads, so an exhaustive depth-first search from every road end is cheap.
 */
internal fun GameBoard.longestRoad(color: PlayerColor): Int {
    val used = MutableEnumSet<Edge>()
    var best = 0

    fun walk(vertex: Vertex, length: Int) {
        if (length > best) best = length
        if (length > 0) {
            val building = buildings[vertex]
            if (building != null && building.color != color) return
        }
        for (edge in vertex.edges) {
            if (roads[edge] != color || !used.add(edge)) continue
            walk(edge.vertices.first { it != vertex }, length + 1)
            used.remove(edge)
        }
    }

    for ((edge, owner) in roads) {
        if (owner == color) for (vertex in edge.vertices) walk(vertex, 0)
    }
    return best
}
