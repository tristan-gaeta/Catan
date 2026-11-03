package com.tgaetaprojects.settledown.game

import com.tgaetaprojects.settledown.game.BoardLayout.Companion.generate
import com.tgaetaprojects.settledown.game.Terrain.DESERT
import com.tgaetaprojects.settledown.game.Terrain.FIELDS
import com.tgaetaprojects.settledown.game.Terrain.FOREST
import com.tgaetaprojects.settledown.game.Terrain.HILL
import com.tgaetaprojects.settledown.game.Terrain.MOUNTAIN
import com.tgaetaprojects.settledown.game.Terrain.PASTURE
import com.tgaetaprojects.settledown.game.random.GameRandom
import com.tgaetaprojects.settledown.game.random.pickWith
import com.tgaetaprojects.settledown.game.random.shuffleWith
import com.tgaetaprojects.settledown.utils.MutableEnumMap
import com.tgaetaprojects.settledown.utils.asReadOnly

/**
 * The fixed part of a board: the sea frame (and so the harbors), plus terrain and number disc per hex. Everything a
 * client needs to draw the island, and it never changes during a game.
 *
 * [generate] follows the rulebook's variable setup from a public board seed, so the network only has to send 8
 * bytes. Records also store the layout itself (see `LayoutCodec`), so old games keep their boards even if the
 * generator changes.
 *
 * The desert carries [DiceTotal.SEVEN] as a "never produces" marker, since no hex produces on a 7.
 *
 * Diagram: [A generated board](https://github.com/tristan-gaeta/Catan/blob/main/docs/images/sample-board.svg).
 *
 * See [Board setup](https://github.com/tristan-gaeta/Catan/blob/main/docs/board-setup.md).
 */
class BoardLayout private constructor(
    /** The frame piece on each side of the island, by side (see [FramePiece.sideEdges]). */
    val frame: List<FramePiece>,
    terrain: Map<Hex, Terrain>,
    numbers: Map<Hex, DiceTotal>,
) {
    // shared by every copy of a game, so it must not be mutable through a cast
    val terrain: Map<Hex, Terrain> = terrain.asReadOnly()
    val numbers: Map<Hex, DiceTotal> = numbers.asReadOnly()

    /** The 9 harbors, as printed on the frame pieces. */
    val ports: Map<Edge, PortType> = MutableEnumMap<Edge, PortType>().also { ports ->
        for ((side, piece) in frame.withIndex()) {
            for ((edge, harbor) in FramePiece.sideEdges(side).zip(piece.harbors)) {
                if (harbor != null) ports[edge] = harbor
            }
        }
    }.asReadOnly()

    /** Where the robber starts. */
    val desert: Hex = terrain.entries.single { it.value == DESERT }.key

    override fun equals(other: Any?): Boolean =
        other is BoardLayout && frame == other.frame && terrain == other.terrain && numbers == other.numbers

    override fun hashCode(): Int =
        (frame.hashCode() * 31 + terrain.hashCode()) * 31 + numbers.hashCode()

    override fun toString(): String =
        "BoardLayout(frame=$frame, terrain=$terrain, numbers=$numbers)"

    companion object {
        private val terrainTiles = listOf(
            HILL, HILL, HILL,
            FOREST, FOREST, FOREST, FOREST,
            FIELDS, FIELDS, DESERT, FIELDS, FIELDS,
            PASTURE, PASTURE, PASTURE, PASTURE,
            MOUNTAIN, MOUNTAIN, MOUNTAIN
        )

        /** Number discs in A–R order, laid along the spiral (rulebook, Variable Setup step 3). */
        private val numberTileSequence = listOf(
            DiceTotal.FIVE, DiceTotal.TWO, DiceTotal.SIX, DiceTotal.THREE, DiceTotal.EIGHT,
            DiceTotal.TEN, DiceTotal.NINE, DiceTotal.TWELVE, DiceTotal.ELEVEN, DiceTotal.FOUR,
            DiceTotal.EIGHT, DiceTotal.TEN, DiceTotal.NINE, DiceTotal.FOUR, DiceTotal.FIVE,
            DiceTotal.SIX, DiceTotal.THREE, DiceTotal.ELEVEN
        )

        /** All 30 coastal edges, counter-clockwise around the island, side by side. */
        val coastline: List<Edge> = (0 until FramePiece.SIDES).flatMap { FramePiece.sideEdges(it) }

        /**
         * The rulebook's fixed setup for first games (page 4): the printed frame with [FramePiece.WOOL] on the top
         * side, and a set arrangement of terrain and number discs (not the spiral).
         *
         * Diagram: [The fixed setup](https://github.com/tristan-gaeta/Catan/blob/main/docs/images/fixed-setup.svg).
         */
        val fixedSetup: BoardLayout = run {
            // row by row, as printed
            val hexes = listOf(
                Hex.A to (DESERT to 7), Hex.B to (FIELDS to 8), Hex.C to (PASTURE to 11),
                Hex.D to (HILL to 6), Hex.E to (PASTURE to 3),
                Hex.F to (FOREST to 4), Hex.G to (MOUNTAIN to 9),
                Hex.H to (FOREST to 10), Hex.I to (FIELDS to 5), Hex.J to (HILL to 12),
                Hex.K to (FOREST to 11), Hex.L to (PASTURE to 5),
                Hex.M to (FIELDS to 2), Hex.N to (HILL to 9),
                Hex.O to (MOUNTAIN to 4), Hex.P to (FOREST to 8),
                Hex.Q to (MOUNTAIN to 6), Hex.R to (FIELDS to 3), Hex.S to (PASTURE to 10),
            )
            of(
                frame = List(FramePiece.SIDES) { FramePiece.entries[(it + 1) % FramePiece.SIDES] },
                terrain = hexes.associate { (hex, tile) -> hex to tile.first },
                numbers = hexes.associate { (hex, tile) -> hex to DiceTotal.fromInt(tile.second) },
            )
        }

        /**
         * The rulebook's variable setup for [boardSeed], with random draws in this order:
         * 1. shuffle the six sea frame pieces around the island (this places the harbors);
         * 2. shuffle the terrain hexes into place;
         * 3. pick the corner where the number discs start, then lay them counter-clockwise inward, skipping the
         *    desert.
         */
        fun generate(boardSeed: Long): BoardLayout {
            val random = GameRandom(boardSeed)
            val frame = FramePiece.entries.toMutableList().apply { shuffleWith(random) }
            val tiles = terrainTiles.toMutableList().apply { shuffleWith(random) }
            val spiral = Hex.spiralInwardCC(Direction.entries.pickWith(random))

            val terrain = MutableEnumMap<Hex, Terrain>()
            val numbers = MutableEnumMap<Hex, DiceTotal>()
            val discs = numberTileSequence.iterator()
            for ((hex, tile) in spiral.zip(tiles)) {
                terrain[hex] = tile
                numbers[hex] = if (tile == DESERT) DiceTotal.SEVEN else discs.next()
            }
            return BoardLayout(frame.toList(), terrain, numbers)
        }

        /**
         * A layout from explicit parts, e.g. decoded from storage. Throws [IllegalArgumentException] unless the frame
         * uses each piece once and the hexes use exactly the base game's tiles and number discs.
         */
        fun of(
            frame: List<FramePiece>,
            terrain: Map<Hex, Terrain>,
            numbers: Map<Hex, DiceTotal>
        ): BoardLayout {
            require(frame.size == FramePiece.SIDES && frame.toSet() == FramePiece.entries.toSet()) {
                "The frame must use each piece once: $frame"
            }
            require(terrain.keys == Hex.entries.toSet() && numbers.keys == Hex.entries.toSet()) {
                "Every hex needs a terrain and a number"
            }
            require(terrain.values.sortedBy { it.ordinal } == terrainTiles.sortedBy { it.ordinal }) {
                "Wrong terrain tiles: ${terrain.values}"
            }
            val desert = terrain.entries.single { it.value == DESERT }.key
            require(numbers[desert] == DiceTotal.SEVEN) { "The desert must carry the 7 marker" }
            require(numbers.filterKeys { it != desert }.values.sortedBy { it.value } == numberTileSequence.sortedBy { it.value }) {
                "Wrong number discs: ${numbers.values}"
            }
            return BoardLayout(
                frame.toList(),
                MutableEnumMap<Hex, Terrain>().also { it.putAll(terrain) },
                MutableEnumMap<Hex, DiceTotal>().also { it.putAll(numbers) },
            )
        }
    }
}
