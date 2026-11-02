package com.tgaetaprojects.biophilia.game.codec

import com.tgaetaprojects.biophilia.game.Building
import com.tgaetaprojects.biophilia.game.DevelopmentCardType
import com.tgaetaprojects.biophilia.game.DiceTotal
import com.tgaetaprojects.biophilia.game.Edge
import com.tgaetaprojects.biophilia.game.FramePiece
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.Resource
import com.tgaetaprojects.biophilia.game.Terrain
import com.tgaetaprojects.biophilia.game.Vertex

/**
 * Stable wire codes for an enum, fixed by an explicit list rather than by declaration order. Enum constants can be
 * reordered without changing what stored bytes mean.
 *
 * Construction checks that every constant has exactly one code and that [width] bits can hold them all. Adding a
 * constant to an enum without giving it a code therefore fails the moment the codec is loaded, instead of silently
 * shifting the format. Codes are part of the format: never renumber them, only append.
 */
internal class WireCodes<E : Enum<E>>(val width: Int, entries: List<E>, vararg order: E) {
    private val name = entries.first()::class.simpleName
    private val byCode: List<E> = order.toList()
    private val codeByOrdinal = IntArray(entries.size)

    init {
        require(order.size == entries.size && order.toSet() == entries.toSet()) {
            "Every $name needs exactly one wire code; codes cover ${order.toList()}"
        }
        require(order.size <= 1 shl width) { "${order.size} ${name}s don't fit in $width bits" }
        for ((code, value) in order.withIndex()) codeByOrdinal[value.ordinal] = code
    }

    /** The constants in code order. */
    val inCodeOrder: List<E> get() = byCode

    fun code(value: E): Int = codeByOrdinal[value.ordinal]

    fun decode(code: Int): E = byCode.getOrNull(code) ?: throw CodecException("No $name has wire code $code")
}

/** The code tables for every enum the codecs write. */
internal object Codes {
    val COLOR = WireCodes(
        2, PlayerColor.entries,
        PlayerColor.RED, PlayerColor.WHITE, PlayerColor.BLUE, PlayerColor.ORANGE,
    )
    val RESOURCE = WireCodes(
        3, Resource.entries,
        Resource.BRICK, Resource.LUMBER, Resource.ORE, Resource.GRAIN, Resource.WOOL,
    )
    val DEV_CARD = WireCodes(
        3, DevelopmentCardType.entries,
        DevelopmentCardType.KNIGHT, DevelopmentCardType.VICTORY_POINT, DevelopmentCardType.ROAD_BUILDING,
        DevelopmentCardType.MONOPOLY, DevelopmentCardType.YEAR_OF_PLENTY,
    )
    val DICE = WireCodes(
        4, DiceTotal.entries,
        DiceTotal.TWO, DiceTotal.THREE, DiceTotal.FOUR, DiceTotal.FIVE, DiceTotal.SIX, DiceTotal.SEVEN,
        DiceTotal.EIGHT, DiceTotal.NINE, DiceTotal.TEN, DiceTotal.ELEVEN, DiceTotal.TWELVE,
    )
    val TERRAIN = WireCodes(
        3, Terrain.entries,
        Terrain.HILL, Terrain.FOREST, Terrain.MOUNTAIN, Terrain.FIELDS, Terrain.PASTURE, Terrain.DESERT,
    )
    val FRAME = WireCodes(
        3, FramePiece.entries,
        FramePiece.GENERIC, FramePiece.LUMBER, FramePiece.ORE, FramePiece.WOOL, FramePiece.BRICK, FramePiece.GRAIN,
    )
    val BUILDING = WireCodes(
        3, Building.entries,
        Building.RED_SETTLEMENT, Building.WHITE_SETTLEMENT, Building.BLUE_SETTLEMENT, Building.ORANGE_SETTLEMENT,
        Building.RED_CITY, Building.WHITE_CITY, Building.BLUE_CITY, Building.ORANGE_CITY,
    )
    val HEX = WireCodes(
        5, Hex.entries,
        Hex.A, Hex.B, Hex.C, Hex.D, Hex.E, Hex.F, Hex.G, Hex.H, Hex.I, Hex.J,
        Hex.K, Hex.L, Hex.M, Hex.N, Hex.O, Hex.P, Hex.Q, Hex.R, Hex.S,
    )

    /*
     * Vertices and edges are written as their ordinal. Unlike other enums, their ordinals aren't arbitrary: each
     * constant is assigned to a board position in a fixed geometric order when the enum loads, so an ordinal is a
     * location. The checks pin the counts the widths below rely on.
     */
    const val VERTEX_WIDTH = 6
    const val EDGE_WIDTH = 7

    init {
        check(Vertex.entries.size == 54 && Edge.entries.size == 72) { "The board graph changed size" }
    }
}
