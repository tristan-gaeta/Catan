package com.tgaetaprojects.biophilia.game

/**
 * The 72 paths between intersections, where roads go. The 30 [isCoastal] ones also carry the harbors.
 *
 * Ordinals are geometric slot ranks: each cell of a radius-3 grid owns its NE, NW and W sides, numbered cell by cell
 * in that order. The coastal edges are outlined in blue:
 *
 * Diagram: [Edge ordinals](https://github.com/tristan-gaeta/Catan/blob/main/docs/images/edge-ordinals.svg).
 *
 * See [Board geometry](https://github.com/tristan-gaeta/Catan/blob/main/docs/board-geometry.md#vertex-and-edge-numbering).
 */
enum class Edge {
    E0, E1, E2, E3, E4, E5, E6, E7, E8, E9,
    E10, E11, E12, E13, E14, E15, E16, E17, E18, E19,
    E20, E21, E22, E23, E24, E25, E26, E27, E28, E29,
    E30, E31, E32, E33, E34, E35, E36, E37, E38, E39,
    E40, E41, E42, E43, E44, E45, E46, E47, E48, E49,
    E50, E51, E52, E53, E54, E55, E56, E57, E58, E59,
    E60, E61, E62, E63, E64, E65, E66, E67, E68, E69,
    E70, E71;

    /** The two endpoints of this edge. */
    val vertices: Set<Vertex> get() = BoardGraph.edgeVertices[ordinal]

    /** The one (coastal) or two (inland) tiles bordering this edge. */
    val hexes: Set<Hex> get() = BoardGraph.edgeHexes[ordinal]

    val isCoastal: Boolean get() = hexes.size == 1

    companion object {
        /* Same scheme as Hex.board, but over a radius 3 grid (7x7 rhombus, 3 cells of padding truncated
           from each end) so the sea-side cells around the board can own the coastal edges. Each cell owns
           its NE, NW and W edges; the other three belong to a neighbor.
         */
        private const val WIDTH = 7
        private const val PADDING = 3

        private val board: Array<Edge?>

        init {
            val edges = entries.iterator()
            board = Array(3 * (WIDTH * WIDTH - 2 * PADDING)) { i ->
                val cell = i / 3 + PADDING
                val q: Int = cell % WIDTH - PADDING
                val r: Int = cell / WIDTH - PADDING
                val dir = Direction.entries[i % 3]
                if (Hex.isValidTile(q, r) || Hex.isValidTile(q + dir.q, r + dir.r))
                    edges.next()
                else null
            }
            assert(!edges.hasNext()) { "Not all edges were assigned" }
        }

        fun fromCoordinate(q: Int, r: Int, direction: Direction): Edge? {
            if (direction.ordinal >= 3)
                return fromCoordinate(q + direction.q, r + direction.r, direction.opposite)
            if (!Hex.isWithinRadius(q, r, PADDING)) return null
            val index = ((r + PADDING) * WIDTH + q) * 3 + direction.ordinal
            return board.getOrNull(index)
        }
    }
}
