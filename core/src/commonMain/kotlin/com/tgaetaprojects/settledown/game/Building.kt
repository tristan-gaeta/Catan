package com.tgaetaprojects.settledown.game

enum class Building {
    RED_SETTLEMENT,
    WHITE_SETTLEMENT,
    BLUE_SETTLEMENT,
    ORANGE_SETTLEMENT,

    RED_CITY,
    WHITE_CITY,
    BLUE_CITY,
    ORANGE_CITY;

    val color: PlayerColor = PlayerColor.entries[ordinal % PlayerColor.entries.size]
    val isCity: Boolean = ordinal >= PlayerColor.entries.size
    inline val isSettlement: Boolean get() = !isCity
    val victoryPoints: Int = if (isCity) 2 else 1

    companion object {
        fun settlement(color: PlayerColor): Building = entries[color.ordinal]
        fun city(color: PlayerColor): Building = entries[color.ordinal + PlayerColor.entries.size]
    }
}
