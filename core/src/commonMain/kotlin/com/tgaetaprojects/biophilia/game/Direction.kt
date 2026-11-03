package com.tgaetaprojects.biophilia.game

/**
 * The six neighbors of a hex, as axial offsets `(q, r)`. Ordinals run counter-clockwise from [NE], so
 * [opposite] is three steps round and rotating is ordinal arithmetic.
 *
 * A direction also names a hex's side facing that neighbor, and the corner 30° counter-clockwise of that side:
 *
 * Diagram: [Corner and side names](https://github.com/tristan-gaeta/Catan/blob/main/docs/images/hex-corners.svg).
 *
 * See [Board geometry](https://github.com/tristan-gaeta/Catan/blob/main/docs/board-geometry.md#corners-and-sides-of-one-hex)
 * and Red Blob Games' [neighbors](https://www.redblobgames.com/grids/hexagons/#neighbors-axial).
 */
enum class Direction(val q: Int, val r: Int) {
    NE(1, -1),
    NW(0, -1),
    W(-1, 0),
    SW(-1, 1),
    SE(0, 1),
    E(1, 0);

    val opposite: Direction
        get() = entries[(ordinal + 3) % 6]

    fun rotatedCW(steps: Int = 1): Direction =
        entries[(ordinal - steps).mod(6)]

    fun rotatedCCW(steps: Int = 1): Direction =
        entries[(ordinal + steps).mod(6)]
}