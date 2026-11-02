package com.tgaetaprojects.biophilia.game

import com.tgaetaprojects.biophilia.utils.MutableEnumMap

/**
 *
 * The game board is 19 hex tiles arranged (pointy side up) in a hexagon. Tiles are ordered
 * using axial coordinates (q,r) where the center is (0,0) and +q is east and +r is
 * south-east.
 *
 * The fixed [layout] (terrain, number tokens and harbors) is shared; this class adds the pieces and the robber,
 * which the rules engine mutates through the internal setters.
 */
class GameBoard(val layout: BoardLayout) {
    /** A board for the random layout generated from [boardSeed]. */
    constructor(boardSeed: Long) : this(BoardLayout.generate(boardSeed))

    val numberTiles: Map<Hex, DiceTotal> get() = layout.numbers
    val biomes: Map<Hex, Terrain> get() = layout.terrain
    val ports: Map<Edge, PortType> get() = layout.ports

    val roads: Map<Edge, PlayerColor>
        field: MutableMap<Edge, PlayerColor> = MutableEnumMap()
    val buildings: Map<Vertex, Building>
        field: MutableMap<Vertex, Building> = MutableEnumMap()
    var robber: Hex = layout.desert
        internal set

    /** The harbor touching [vertex], if any. Harbors are spaced so a vertex touches at most one. */
    fun harborAt(vertex: Vertex): PortType? = vertex.edges.firstNotNullOfOrNull { ports[it] }

    internal fun placeRoad(edge: Edge, color: PlayerColor) {
        roads[edge] = color
    }

    /** Places a settlement, or replaces one with a city. */
    internal fun placeBuilding(vertex: Vertex, building: Building) {
        buildings[vertex] = building
    }

    /** An independent board with the same layout, pieces and robber. */
    internal fun copy(): GameBoard = GameBoard(layout).also { copy ->
        for ((edge, color) in roads) copy.placeRoad(edge, color)
        for ((vertex, building) in buildings) copy.placeBuilding(vertex, building)
        copy.robber = robber
    }
}
