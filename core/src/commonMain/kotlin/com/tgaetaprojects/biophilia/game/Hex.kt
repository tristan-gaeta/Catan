package com.tgaetaprojects.biophilia.game

import com.tgaetaprojects.biophilia.utils.MutableEnumMap
import com.tgaetaprojects.biophilia.utils.MutableEnumSet
import kotlin.math.abs

/**
 *
 */
enum class Hex(val q: Int, val r: Int) {
    A(0, -2), B(1, -2), C(2, -2), //
    D(-1, -1), E(0, -1), F(1, -1), G(2, -1), //
    H(-2, 0), I(-1, 0), J(0, 0), K(1, 0), L(2, 0), //
    M(-2, 1), N(-1, 1), O(0, 1), P(1, 1), //
    Q(-2, 2), R(-1, 2), S(0, 2);

    inline val s: Int get() = -q - r

    operator fun get(index: Int) = when(index) {
        0 -> q; 1 -> r; 2 -> s; else -> throw IllegalArgumentException()
    }

    val vertices: Set<Vertex> = MutableEnumSet<Vertex>()
    val edges: Map<Direction, Edge> = MutableEnumMap<Direction, Edge>()

    fun getNeighbor(dir: Direction): Hex? {
        val nQ = q + dir.q
        val nR = r + dir.r
        val index = getIndex(nQ, nR)
        return if (index != -1) {
            Hex.board[index]
        } else null
    }

    fun rotateCC(times: Int = 1): Hex {
        val q = this[times.mod(3)]
        val r = this[(times + 1).mod(3)]
        return if (times % 2 == 0) {
            fromCoordinate(q, r) as Hex
        } else {
            fromCoordinate(-q, -r) as Hex
        }
    }

    companion object {
        /* by adding a little padding our hash from coordinates to enum is very easy.
           It's basically stored as a 5x5 rhombus with the first and last bits of padding truncated
                .   .   A   B   C
              null  D   E   F   G
                H   I   J   K   L
                M   N   O   P  null
                Q   R   S   .   .
         */
        internal val board = Array(21) { i ->
            val q = i % 5
            val r = (i / 5) - 2
            if (isValidTile(q, r)) {
                Hex.entries.firstOrNull { it.q == q && it.r == r }
                    ?: throw IllegalStateException("Hex with coordinate q=$q, r=$r is not declared in enum class.")
            } else null
        }

        internal fun isValidTile(q: Int, r: Int): Boolean = abs(q) + abs(r) + abs(q + r) <= 4

        internal fun getIndex(q: Int, r: Int): Int {
            return if (isValidTile(q, r)) {
                5 * (r + 2) + q
            } else -1
        }

        fun fromCoordinate(q: Int, r: Int): Hex? {
            val index = getIndex(q, r)
            if (index == -1) return null
            return board[index]
        }

        private val spiralSequences: Array<List<Hex>> = Array(Direction.entries.size) { i ->
            visitSpiralInwardCC(Direction.entries[i])
        }

        private fun visitSpiralInwardCC(direction: Direction): List<Hex> {
            val list = ArrayList<Hex>(19)

            // Start from the outermost radius and work inwards
            for (radius in 2 downTo 1) {
                val q = direction.q * radius
                val r = direction.r * radius
                var hex = fromCoordinate(q, r)!!

                for (i in 0 until 6) {
                    // To move counter-clockwise around the ring, rotate the starting direction by 120 degrees (2 steps)
                    val moveDir = Direction.entries[(direction.ordinal + i + 2) % 6]
                    for (step in 0 until radius) {
                        list += hex
                        hex = hex.getNeighbor(moveDir)!!
                    }
                }
            }
            // add origin last
            list += fromCoordinate(0, 0)!!
            return list
        }

        fun spiralInwardCC(direction: Direction) = spiralSequences[direction.ordinal]
    }
}