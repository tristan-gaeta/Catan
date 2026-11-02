package com.tgaetaprojects.biophilia.game

import com.tgaetaprojects.biophilia.game.Direction.E
import com.tgaetaprojects.biophilia.game.Direction.NE
import com.tgaetaprojects.biophilia.game.Direction.NW
import com.tgaetaprojects.biophilia.game.Direction.SE
import com.tgaetaprojects.biophilia.game.Direction.SW
import com.tgaetaprojects.biophilia.game.Direction.W

/**
 * Enum class for all 54 vertices on the map.
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
