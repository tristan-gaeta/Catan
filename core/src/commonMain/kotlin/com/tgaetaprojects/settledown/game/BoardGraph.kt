package com.tgaetaprojects.settledown.game

import com.tgaetaprojects.settledown.utils.EnumMap
import com.tgaetaprojects.settledown.utils.EnumSet
import com.tgaetaprojects.settledown.utils.MutableEnumMap
import com.tgaetaprojects.settledown.utils.MutableEnumSet

/**
 * Static adjacency tables for the board, indexed by ordinal and built once on first use.
 *
 * Every vertex and edge touches at least one tile, so walking the six corners and sides of each of the
 * 19 hexes reaches the whole graph. This lives outside the enums so that Hex, Edge and Vertex only ever
 * depend on each other's coordinate lookups during class initialization, never on each other's graph.
 *
 * Side `d` of a hex joins its corners `d` and `d - 1`; see
 * [Board geometry](https://github.com/tristan-gaeta/Catan/blob/main/docs/board-geometry.md#corners-and-sides-of-one-hex).
 */
internal object BoardGraph {
    val hexVertices: List<EnumMap<Direction, Vertex>>
    val hexEdges: List<EnumMap<Direction, Edge>>

    val vertexHexes: List<EnumSet<Hex>>
    val vertexEdges: List<EnumSet<Edge>>
    val vertexNeighbors: List<EnumMap<Vertex, Edge>>

    val edgeHexes: List<EnumSet<Hex>>
    val edgeVertices: List<EnumSet<Vertex>>

    init {
        val hexVerts = List(Hex.entries.size) { MutableEnumMap<Direction, Vertex>() }
        val hexSides = List(Hex.entries.size) { MutableEnumMap<Direction, Edge>() }
        val vertHexes = List(Vertex.entries.size) { MutableEnumSet<Hex>() }
        val vertEdges = List(Vertex.entries.size) { MutableEnumSet<Edge>() }
        val vertNeighbors = List(Vertex.entries.size) { MutableEnumMap<Vertex, Edge>() }
        val sideHexes = List(Edge.entries.size) { MutableEnumSet<Hex>() }
        val sideVerts = List(Edge.entries.size) { MutableEnumSet<Vertex>() }

        for (hex in Hex.entries) {
            for (dir in Direction.entries) {
                val corner = Vertex.fromCoordinates(hex.q, hex.r, dir)!!
                val prevDir = Direction.entries[(dir.ordinal + 5) % 6]
                val prevCorner = Vertex.fromCoordinates(hex.q, hex.r, prevDir)!!
                val side = Edge.fromCoordinate(hex.q, hex.r, dir)!!

                hexVerts[hex.ordinal][dir] = corner
                hexSides[hex.ordinal][dir] = side
                vertHexes[corner.ordinal].add(hex)
                sideHexes[side.ordinal].add(hex)

                // edge d runs between corner d and corner d-1
                sideVerts[side.ordinal].add(corner)
                sideVerts[side.ordinal].add(prevCorner)
            }
        }

        for (side in Edge.entries) {
            val (a, b) = sideVerts[side.ordinal].toList()
            vertEdges[a.ordinal].add(side)
            vertEdges[b.ordinal].add(side)
            vertNeighbors[a.ordinal][b] = side
            vertNeighbors[b.ordinal][a] = side
        }

        hexVertices = hexVerts
        hexEdges = hexSides
        vertexHexes = vertHexes
        vertexEdges = vertEdges
        vertexNeighbors = vertNeighbors
        edgeHexes = sideHexes
        edgeVertices = sideVerts
    }
}
