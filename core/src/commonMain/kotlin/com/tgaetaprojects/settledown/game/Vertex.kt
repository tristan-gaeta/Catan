package com.tgaetaprojects.settledown.game

import com.tgaetaprojects.settledown.game.Direction.E
import com.tgaetaprojects.settledown.game.Direction.NE
import com.tgaetaprojects.settledown.game.Direction.NW
import com.tgaetaprojects.settledown.game.Direction.SE
import com.tgaetaprojects.settledown.game.Direction.SW
import com.tgaetaprojects.settledown.game.Direction.W

/**
 * The 54 intersections where settlements and cities stand.
 *
 * Ordinals are geometric slot ranks: each cell of a radius-3 grid owns its north and south corners, and cells are
 * numbered row by row, north before south. They only change if the board does, which is why the codec can write a
 * vertex as its ordinal in 6 bits.
 *
 * Diagram: [Vertex ordinals](https://github.com/tristan-gaeta/Catan/blob/main/docs/images/vertex-ordinals.svg).
 *
 * See [Board geometry](https://github.com/tristan-gaeta/Catan/blob/main/docs/board-geometry.md#vertex-and-edge-numbering).
 */
enum class Vertex {
    V0, V1, V2, V3, V4, V5, V6, V7, V8, V9,
    V10, V11, V12, V13, V14, V15, V16, V17, V18, V19,
    V20, V21, V22, V23, V24, V25, V26, V27, V28, V29,
    V30, V31, V32, V33, V34, V35, V36, V37, V38, V39,
    V40, V41, V42, V43, V44, V45, V46, V47, V48, V49,
    V50, V51, V52, V53;

    /** Adjacent vertices, each mapped to the edge that connects it to this one. */
    val neighbors: Map<Vertex, Edge> get() = BoardGraph.vertexNeighbors[ordinal]

    /** The two (coastal) or three (inland) edges meeting at this vertex. */
    val edges: Set<Edge> get() = BoardGraph.vertexEdges[ordinal]

    /** The one to three tiles touching this vertex; these are what a building here collects from. */
    val hexes: Set<Hex> get() = BoardGraph.vertexHexes[ordinal]

    companion object {
        /* Same 7x7 truncated grid as Edge.board. Each cell owns two corners, its north (Y shape) and its
           south (inverted Y); the other four are a neighbor's north or south.
         */
        private const val WIDTH = 7
        private const val PADDING = 3
        private const val NORTH = 0
        private const val SOUTH = 1

        private val board: Array<Vertex?>

        init {
            val verts = entries.iterator()
            board = Array(2 * (WIDTH * WIDTH - 2 * PADDING)) { i ->
                val cell = i / 2 + PADDING
                val q: Int = cell % WIDTH - PADDING
                val r: Int = cell / WIDTH - PADDING
                // Include the vertex if at least one of the three hexes sharing it is on the board
                val touchesBoard = if (i % 2 == NORTH) {
                    Hex.isValidTile(q, r) || Hex.isValidTile(q, r - 1) || Hex.isValidTile(
                        q + 1,
                        r - 1
                    )
                } else {
                    Hex.isValidTile(q, r) || Hex.isValidTile(q - 1, r + 1) || Hex.isValidTile(
                        q,
                        r + 1
                    )
                }
                if (touchesBoard) verts.next() else null
            }
            if (verts.hasNext()) throw IllegalStateException("Not all vertices were assigned")
        }

        private fun ownedBy(q: Int, r: Int, corner: Int): Vertex? {
            if (!Hex.isWithinRadius(q, r, PADDING)) return null
            return board.getOrNull(((r + PADDING) * WIDTH + q) * 2 + corner)
        }

        /**
         * Corners are named by the edge direction 30 degrees clockwise of them, so corner `d` sits between
         * edge `d` and the next edge counter-clockwise (NE -> north, SW -> south, E -> the NE corner...).
         * It follows that edge `d` runs from corner `d` to the corner of the previous direction.
         */
        fun fromCoordinates(q: Int, r: Int, direction: Direction): Vertex? = when (direction) {
            NE -> ownedBy(q, r, NORTH)
            NW -> ownedBy(q, r - 1, SOUTH)
            W -> ownedBy(q - 1, r + 1, NORTH)
            SW -> ownedBy(q, r, SOUTH)
            SE -> ownedBy(q, r + 1, NORTH)
            E -> ownedBy(q + 1, r - 1, SOUTH)
        }
    }
}
