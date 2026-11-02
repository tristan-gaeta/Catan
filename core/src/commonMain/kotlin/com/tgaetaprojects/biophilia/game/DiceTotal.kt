package com.tgaetaprojects.biophilia.game

import kotlin.random.Random

enum class DiceTotal {
    TWO,
    THREE,
    FOUR,
    FIVE,
    SIX,
    SEVEN,
    EIGHT,
    NINE,
    TEN,
    ELEVEN,
    TWELVE;

    val value = ordinal + 2

    companion object {
        fun fromInt(value: Int) = entries[value - 2]

        fun roll(rng: Random) = entries[rng.nextInt(6) + rng.nextInt(6)]
    }
}
