package com.tgaetaprojects.biophilia.game

import com.tgaetaprojects.biophilia.game.Terrain.*
import com.tgaetaprojects.biophilia.utils.*
import kotlin.random.Random
import kotlin.random.asKotlinRandom
import java.util.Random as JRand

/**
 *
 * The game board is 19 hex tiles arranged (pointy side up) in a hexagon. Tiles are ordered
 * using axial coordinates (q,r) where the center is (0,0) and +q is east and +r is
 * south-east.
 *
 */
class GameBoard(seed: Long = System.currentTimeMillis()) {
    companion object {
        private val numberTileSequence = listOf(
            DiceTotal.FIVE, DiceTotal.TWO, DiceTotal.SIX, DiceTotal.THREE, DiceTotal.EIGHT,
            DiceTotal.TEN, DiceTotal.NINE, DiceTotal.TWELVE, DiceTotal.ELEVEN, DiceTotal.FOUR,
            DiceTotal.EIGHT, DiceTotal.TEN, DiceTotal.NINE, DiceTotal.FOUR, DiceTotal.FIVE,
            DiceTotal.SIX, DiceTotal.THREE, DiceTotal.ELEVEN
        )


        private fun initPorts(rng: Random): Map<Edge, PortType> {
            val portTypes = mutableListOf(
                PortType.BRICK, PortType.LUMBER, PortType.GRAIN, PortType.WOOL, PortType.ORE,
                PortType.GENERIC, PortType.GENERIC, PortType.GENERIC, PortType.GENERIC
            ).shuffled(rng).iterator()

            val portMap = MutableEnumMap<Edge, PortType>()

            for (i in (0 until 6) step 2) {
                val dir = Direction.entries[i]
                val hex1 = Hex.fromCoordinate(dir.q * 2, dir.r * 2)
                portMap[hex1.edges[dir]] = portTypes.next()
            }

            return portMap
        }
    }

    val numberTiles: Map<Hex, DiceTotal>
    val biomes: Map<Hex, Terrain>
    val ports: Map<Edge, PortType>

    val roads: Map<Edge, PlayerColor> get() = _roads
    private val _roads: MutableMap<Edge, PlayerColor> = MutableEnumMap()

    val buildings: Map<Vertex, Building> get() = _buildings
    private val _buildings: MutableMap<Vertex, Building> = MutableEnumMap()

    lateinit var robberTileIndex: Hex
        private set

    init {
        // java random algorithm is guarantee to not change
        val rng = JRand(seed).asKotlinRandom()

        // Assign ports
        this.ports = initPorts(rng)


        val biomeArr = arrayOf(
            HILL, HILL, HILL,
            FOREST, FOREST, FOREST, FOREST,
            FIELDS, FIELDS, DESERT, FIELDS, FIELDS,
            PASTURE, PASTURE, PASTURE, PASTURE,
            MOUNTAIN, MOUNTAIN, MOUNTAIN
        )
        biomeArr.shuffle(rng)

        // Assign numbers following the spiral pattern
        val startDir = Direction.entries.random(rng)
        val spiral = Hex.spiralInwardCC(startDir)

        val numMap = MutableEnumMap<Hex, DiceTotal>()
        val terrainMap = MutableEnumMap<Hex, Terrain>()

        val biomes = biomeArr.iterator()
        val numbers = numberTileSequence.iterator()
        for (hex in spiral) {
            val biome = biomes.next()
            terrainMap[hex] = biome

            if (biome == DESERT) {
                robberTileIndex = hex
                numMap[hex] = DiceTotal.SEVEN
            } else {
                numMap[hex] = numbers.next()
            }
        }

        this.numberTiles = numMap
        this.biomes = terrainMap
    }
}
