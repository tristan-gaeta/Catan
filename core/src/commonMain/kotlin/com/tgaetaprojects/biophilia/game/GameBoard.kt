package com.tgaetaprojects.biophilia.game

import com.tgaetaprojects.biophilia.game.Terrain.*
import com.tgaetaprojects.biophilia.utils.*
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.random.asKotlinRandom
import java.util.Random as JRand

/**
 *
 * The game board is 19 hex tiles arranged (pointy side up) in a hexagon. Tiles are ordered
 * using axial coordinates (q,r) where the center is (0,0) and +q is east and +r is
 * south-east.
 *
 * Layout (terrain, number tokens and harbors) is fixed by the seed. Pieces and the robber are mutated
 * by the rules engine through the internal setters.
 */
class GameBoard(seed: Long = System.currentTimeMillis()) {
    companion object {
        private val numberTileSequence = listOf(
            DiceTotal.FIVE, DiceTotal.TWO, DiceTotal.SIX, DiceTotal.THREE, DiceTotal.EIGHT,
            DiceTotal.TEN, DiceTotal.NINE, DiceTotal.TWELVE, DiceTotal.ELEVEN, DiceTotal.FOUR,
            DiceTotal.EIGHT, DiceTotal.TEN, DiceTotal.NINE, DiceTotal.FOUR, DiceTotal.FIVE,
            DiceTotal.SIX, DiceTotal.THREE, DiceTotal.ELEVEN
        )

        /** The 30 coastal edges in counter-clockwise order around the island. */
        internal val coastline: List<Edge> = Edge.entries.filter { it.isCoastal }.sortedBy { coastalAngle(it) }

        /**
         * Gaps, in coastal edges, between consecutive harbors. Repeated three times it covers the 30-edge coast
         * like the printed frame, and since every gap is at least 3 no two harbors share an intersection.
         */
        private val harborGaps = intArrayOf(3, 3, 4)

        /** Angle of a coastal edge's midpoint around the board center, counter-clockwise from east. */
        private fun coastalAngle(edge: Edge): Double {
            val hex = edge.hexes.single()
            val dir = Direction.entries.first { hex.edges[it] == edge }
            val sqrt3 = sqrt(3.0)
            // hex center plus half the step to the (sea) neighbor; y grows downwards so flip it for atan2
            val x = sqrt3 * (hex.q + hex.r / 2.0) + sqrt3 * (dir.q + dir.r / 2.0) / 2
            val y = 1.5 * hex.r + 1.5 * dir.r / 2
            return atan2(-y, x)
        }

        private fun initPorts(rng: Random): Map<Edge, PortType> {
            val portTypes = mutableListOf(
                PortType.BRICK, PortType.LUMBER, PortType.GRAIN, PortType.WOOL, PortType.ORE,
                PortType.GENERIC, PortType.GENERIC, PortType.GENERIC, PortType.GENERIC
            ).shuffled(rng).iterator()

            val portMap = MutableEnumMap<Edge, PortType>()

            var position = rng.nextInt(coastline.size)
            var gap = 0
            while (portTypes.hasNext()) {
                portMap[coastline[position % coastline.size]] = portTypes.next()
                position += harborGaps[gap++ % harborGaps.size]
            }

            return portMap
        }
    }

    val numberTiles: Map<Hex, DiceTotal>
    val biomes: Map<Hex, Terrain>
    val ports: Map<Edge, PortType>

    val roads: Map<Edge, PlayerColor>
        field: MutableMap<Edge, PlayerColor> = MutableEnumMap()
    val buildings: Map<Vertex, Building>
        field: MutableMap<Vertex, Building> = MutableEnumMap()
    lateinit var robber: Hex
        internal set

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
                robber = hex
                numMap[hex] = DiceTotal.SEVEN
            } else {
                numMap[hex] = numbers.next()
            }
        }

        this.numberTiles = numMap
        this.biomes = terrainMap
    }

    /** The harbor touching [vertex], if any. Harbors are spaced so a vertex touches at most one. */
    fun harborAt(vertex: Vertex): PortType? = vertex.edges.firstNotNullOfOrNull { ports[it] }

    internal fun placeRoad(edge: Edge, color: PlayerColor) {
        roads[edge] = color
    }

    /** Places a settlement, or replaces one with a city. */
    internal fun placeBuilding(vertex: Vertex, building: Building) {
        buildings[vertex] = building
    }
}
