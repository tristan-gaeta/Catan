package com.tgaetaprojects.settledown.ui.game

import androidx.compose.ui.geometry.Offset
import com.tgaetaprojects.settledown.game.Direction
import com.tgaetaprojects.settledown.game.Edge
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.Vertex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Screen positions of the board graph, in units of one hex's circumradius with the center tile J at the origin and
 * `+y` pointing down. Floating point is fine here: this is only for drawing and hit-testing, never for rules.
 *
 * Hexes are pointy-top. Corner `d` of a hex sits at `90° + 60° × d.ordinal`, counter-clockwise from east, which
 * matches the corner convention on `Vertex.fromCoordinates` (NE is the north corner, SW the south one). See
 * [Board geometry](https://github.com/tristan-gaeta/Catan/blob/main/docs/board-geometry.md).
 */
internal object BoardGeometry {
    private val SQRT3 = sqrt(3f)

    /** Half the width and height of the area to fit on screen, including the sea frame and harbors. */
    const val HALF_WIDTH = 5.6f
    val HALF_HEIGHT = HALF_WIDTH * SQRT3 / 2

    /** The sea frame: a flat-topped hexagon around the island. */
    val seaCorners: List<Offset> = List(6) { i -> polar(HALF_WIDTH, 60.0 * i) }

    private val hexCenters: Array<Offset> = Array(Hex.entries.size) { i ->
        val hex = Hex.entries[i]
        Offset(SQRT3 * (hex.q + hex.r / 2f), 1.5f * hex.r)
    }

    private val vertexPositions: Array<Offset> = Array(Vertex.entries.size) { Offset.Zero }.also { positions ->
        for (hex in Hex.entries) {
            for ((direction, vertex) in hex.vertices) positions[vertex.ordinal] = corner(hex, direction)
        }
    }

    fun center(hex: Hex): Offset = hexCenters[hex.ordinal]

    fun corner(hex: Hex, direction: Direction): Offset =
        center(hex) + polar(1f, 90.0 + 60.0 * direction.ordinal)

    fun corners(hex: Hex): List<Offset> = Direction.entries.map { corner(hex, it) }

    fun position(vertex: Vertex): Offset = vertexPositions[vertex.ordinal]

    fun ends(edge: Edge): Pair<Offset, Offset> {
        val (a, b) = edge.vertices.toList()
        return position(a) to position(b)
    }

    fun midpoint(edge: Edge): Offset = ends(edge).let { (a, b) -> (a + b) / 2f }

    /** The unit vector from the island out to sea across a coastal [edge]. */
    fun outward(edge: Edge): Offset {
        val out = midpoint(edge) - center(edge.hexes.single())
        return out / out.getDistance()
    }

    private fun polar(radius: Float, degrees: Double): Offset {
        val radians = degrees * PI / 180
        return Offset((radius * cos(radians)).toFloat(), (-radius * sin(radians)).toFloat())
    }
}
