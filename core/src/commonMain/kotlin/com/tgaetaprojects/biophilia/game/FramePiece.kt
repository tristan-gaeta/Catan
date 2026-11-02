package com.tgaetaprojects.biophilia.game

import com.tgaetaprojects.biophilia.game.PortType.*

/**
 * The six sea frame pieces of the 6th edition. Each covers one side of the island, 5 coastal edges, and has its
 * harbors printed on it, so shuffling the pieces around the island is what varies the harbors between games.
 *
 * [harbors] lists a piece's 5 edges in counter-clockwise order around the island, `null` where there's no harbor.
 * Three pieces carry one harbor on their middle edge; the other three carry a 2:1 harbor on their second edge and a
 * 3:1 harbor on their last edge. Together that's 4 generic harbors and one 2:1 harbor per resource.
 *
 * Pieces are named after their 2:1 harbor, except [GENERIC], whose only harbor is a 3:1.
 */
enum class FramePiece(vararg harbors: PortType?) {
    GENERIC(null, null, PortType.GENERIC, null, null),
    LUMBER(null, null, PortType.LUMBER, null, null),
    ORE(null, null, PortType.ORE, null, null),
    WOOL(null, PortType.WOOL, null, null, PortType.GENERIC),
    BRICK(null, PortType.BRICK, null, null, PortType.GENERIC),
    GRAIN(null, PortType.GRAIN, null, null, PortType.GENERIC);

    val harbors: List<PortType?> = harbors.toList()

    companion object {
        /** Coastal edges covered by one piece. */
        const val EDGES = 5

        /** Pieces in a frame, one per side of the island. */
        const val SIDES = 6

        /**
         * The 5 coastal edges of side [side], counter-clockwise around the island. Side 0 is the top; sides follow
         * counter-clockwise, in the same order as [Direction.entries].
         *
         * Side `d` starts at the corner hex in direction `d` (two steps out from the center) and runs along the
         * coast to the next corner: the corner's last coastal edge, both coastal edges of the hex between the
         * corners, then the next corner's first two. Pieces meet where a corner hex's coast turns outward.
         */
        fun sideEdges(side: Int): List<Edge> {
            require(side in 0 until SIDES) { "No side $side" }
            val d = Direction.entries[side]
            val corner = Hex.fromCoordinate(2 * d.q, 2 * d.r)!!
            val alongCoast = d.rotatedCCW(2)
            val middle = corner.getNeighbor(alongCoast)!!
            val nextCorner = middle.getNeighbor(alongCoast)!!
            val next = d.rotatedCCW()
            return listOf(
                corner.edges.getValue(next),
                middle.edges.getValue(d),
                middle.edges.getValue(next),
                nextCorner.edges.getValue(d),
                nextCorner.edges.getValue(next),
            )
        }
    }
}
