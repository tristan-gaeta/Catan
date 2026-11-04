package com.tgaetaprojects.settledown.game

import com.tgaetaprojects.settledown.game.random.GameRandom
import kotlin.math.abs

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

    /** The dots under a number disc: how many of the 36 dice outcomes roll this, or 0 for the desert's seven. */
    val pips: Int get() = if (this == SEVEN) 0 else 6 - abs(7 - value)

    companion object {
        fun fromInt(value: Int) = entries[value - 2]

        /** Two six-sided dice; draws twice from [random]. */
        fun roll(random: GameRandom) = entries[random.nextInt(6) + random.nextInt(6)]
    }
}
