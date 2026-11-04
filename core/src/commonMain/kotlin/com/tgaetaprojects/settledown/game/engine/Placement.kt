package com.tgaetaprojects.settledown.game.engine

import com.tgaetaprojects.settledown.game.Building
import com.tgaetaprojects.settledown.game.Edge
import com.tgaetaprojects.settledown.game.GameBoard
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.Vertex

/** The distance rule: [vertex] and every intersection next to it are empty. */
internal fun GameBoard.satisfiesDistanceRule(vertex: Vertex): Boolean =
    vertex !in buildings && vertex.neighbors.keys.none { it in buildings }

/**
 * Whether [color] may put a settlement on [vertex]. Outside the set-up phase ([requireRoad]) the vertex must
 * also touch one of the player's own roads.
 */
internal fun GameBoard.canPlaceSettlement(
    color: PlayerColor,
    vertex: Vertex,
    requireRoad: Boolean
): Boolean =
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

/** Where [color] may place a settlement in [phase]: free in set-up, connected by road afterward. */
internal fun GameBoard.legalSettlementSpots(color: PlayerColor, settlementsLeft: Int, phase: Phase): List<Vertex> {
    if (settlementsLeft == 0) return emptyList()
    val requireRoad = phase !is Phase.SetupSettlement
    return Vertex.entries.filter { canPlaceSettlement(color, it, requireRoad) }
}

/** Where [color] may place a road in [phase]; during set-up only [currentPlayer], next to their new settlement. */
internal fun GameBoard.legalRoadSpots(
    color: PlayerColor,
    roadsLeft: Int,
    phase: Phase,
    currentPlayer: PlayerColor
): List<Edge> {
    if (roadsLeft == 0) return emptyList()
    return when (phase) {
        is Phase.SetupRoad ->
            if (color == currentPlayer) phase.settlement.edges.filter { it !in roads } else emptyList()

        else -> Edge.entries.filter { canPlaceRoad(color, it) }
    }
}

/** Which of [color]'s settlements could become cities; none once all their cities are on the board. */
internal fun GameBoard.legalCitySpots(color: PlayerColor, citiesLeft: Int): List<Vertex> {
    if (citiesLeft == 0) return emptyList()
    return Vertex.entries.filter { canUpgradeToCity(color, it) }
}
