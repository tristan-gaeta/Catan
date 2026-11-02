package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.Building
import com.tgaetaprojects.biophilia.game.Edge
import com.tgaetaprojects.biophilia.game.GameBoard
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.Vertex

/** The distance rule: [vertex] and every intersection next to it are empty. */
internal fun GameBoard.satisfiesDistanceRule(vertex: Vertex): Boolean =
    vertex !in buildings && vertex.neighbors.keys.none { it in buildings }

/**
 * Whether [color] may put a settlement on [vertex]. Outside the set-up phase ([requireRoad]) the vertex must
 * also touch one of the player's own roads.
 */
internal fun GameBoard.canPlaceSettlement(color: PlayerColor, vertex: Vertex, requireRoad: Boolean): Boolean =
    satisfiesDistanceRule(vertex) && (!requireRoad || vertex.edges.any { roads[it] == color })

/**
 * Whether [color] may build a road on [edge]: it must be empty and connect, at either end, to one of the player's
 * buildings or roads. A road can't connect through an intersection holding an opponent's building.
 */
internal fun GameBoard.canPlaceRoad(color: PlayerColor, edge: Edge): Boolean =
    edge !in roads && edge.vertices.any { connectsAt(it, color, edge) }

private fun GameBoard.connectsAt(vertex: Vertex, color: PlayerColor, newEdge: Edge): Boolean {
    val building = buildings[vertex]
    if (building != null) return building.color == color
    return vertex.edges.any { it != newEdge && roads[it] == color }
}

/** Whether [vertex] holds one of [color]'s settlements, the only thing a city can replace. */
internal fun GameBoard.canUpgradeToCity(color: PlayerColor, vertex: Vertex): Boolean =
    buildings[vertex] == Building.settlement(color)
