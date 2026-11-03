package com.tgaetaprojects.settledown.game

import com.tgaetaprojects.settledown.game.random.GameRandom

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

        /** Two six-sided dice; draws twice from [random]. */
        fun roll(random: GameRandom) = entries[random.nextInt(6) + random.nextInt(6)]
    }
}
