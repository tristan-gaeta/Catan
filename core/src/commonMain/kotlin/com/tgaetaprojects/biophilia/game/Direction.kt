package com.tgaetaprojects.biophilia.game

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
        entries[(ordinal + steps).mod(6)]

    fun rotatedCCW(steps: Int = 1): Direction =
        entries[(ordinal - steps).mod(6)]
}