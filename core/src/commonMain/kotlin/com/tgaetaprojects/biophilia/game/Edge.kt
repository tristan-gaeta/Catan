package com.tgaetaprojects.biophilia.game

/**
 * Enum class for the 72 edges of the board
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

    companion object {
        private val board: Array<Edge?>

        init {
            val edges = entries.iterator()
            board = Array(3*(7*7 - 2*3)){ i ->
                val q: Int = (i / 3) % 7
                val r: Int = (i / 3) / 7 - 3
                val dir = Direction.entries[i % 3]
                if (Hex.isValidTile(q, r) || Hex.isValidTile(q + dir.q, r + dir.r))
                    edges.next()
                else null
            }
            if (edges.hasNext()) throw IllegalStateException("Not all edges were assigned")
        }

        fun fromCoordinate(q: Int, r: Int, direction: Direction): Edge? {
            if (direction.ordinal >= 3)
                return fromCoordinate(q + direction.q, r + direction.r, direction.opposite)
            val index = ((r + 3) * 7 + q) * 3 + direction.ordinal
            return board.getOrNull(index)
        }
    }
}