package com.tgaetaprojects.biophilia.game

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
    }
}
