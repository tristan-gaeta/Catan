package com.tgaetaprojects.biophilia.game

import kotlin.math.abs

/**
 * A hex position in axial coordinates, with `q` in the low 32 bits of [packed] and `r` in the high 32 bits. The cube
 * coordinate `s = -q - r` is derived on demand.
 *
 * The arithmetic follows Red Blob Games' [Hexagonal Grids](https://www.redblobgames.com/grids/hexagons/):
 * [distances](https://www.redblobgames.com/grids/hexagons/#distances-axial) for [length], and
 * [rotation](https://www.redblobgames.com/grids/hexagons/#rotation) for [rotateCC].
 */
@JvmInline
value class AxialCoord(val packed: Long) {
    inline val q: Int get() = packed.toInt()
    inline val r: Int get() = (packed ushr 32).toInt()
    inline val s: Int get() = -q - r

    operator fun get(i: Int) = when (i) {
        0 -> q
        1 -> r
        2 -> s
        else -> throw IndexOutOfBoundsException("Index $i out of bounds for AxialCoord")
    }

    operator fun plus(other: AxialCoord) = AxialCoord(q + other.q, r + other.r)
    operator fun minus(other: AxialCoord) = AxialCoord(q - other.q, r - other.r)
    operator fun times(scalar: Int) = AxialCoord(q * scalar, r * scalar)
    operator fun unaryMinus() = AxialCoord(-q, -r)

    fun length(): Int = (abs(q) + abs(r) + abs(s)) / 2
    fun distanceTo(other: AxialCoord): Int = (this - other).length()

    fun rotateCC(times: Int = 1): AxialCoord {
        val q = this[times.mod(3)]
        val r = this[(times + 1).mod(3)]
        return if (times % 2 == 0) AxialCoord(q, r) else AxialCoord(-q, -r)
    }

    inline fun rotateCW(times: Int = 1): AxialCoord = rotateCC(-times)

    fun neighbor(direction: Direction): AxialCoord = AxialCoord(q + direction.q, r + direction.r)

    companion object {
        val ZERO = AxialCoord(0, 0)
        val Q = AxialCoord(1, 0)
        val R = AxialCoord(0, 1)
        val S = AxialCoord(-1, -1)

        operator fun invoke(q: Int, r: Int): AxialCoord {
            val packed = (r.toLong() shl 32) or (q.toLong() and 0xFFFFFFFFL)
            return AxialCoord(packed)
        }
    }
}