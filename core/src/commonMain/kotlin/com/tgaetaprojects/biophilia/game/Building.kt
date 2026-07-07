package com.tgaetaprojects.biophilia.game

enum class Building {
    RED_SETTLEMENT,
    RED_CITY,

    WHITE_SETTLEMENT,
    WHITE_CITY,

    BLUE_SETTLEMENT,
    BLUE_CITY,

    ORANGE_SETTLEMENT,
    ORANGE_CITY;

    val color: PlayerColor = PlayerColor.entries[ordinal / 2]
    val isCity: Boolean = (ordinal % 2) != 0
    val isSettlement: Boolean = (ordinal % 2) == 0
    val victoryPoints: Int = (ordinal % 2) + 1

    companion object {
        fun settlement(color: PlayerColor): Building = entries[color.ordinal * 2]
        fun city(color: PlayerColor): Building = entries[color.ordinal * 2 + 1]
    }
}
