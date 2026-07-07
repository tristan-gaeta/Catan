package com.tgaetaprojects.biophilia.game

import com.tgaetaprojects.biophilia.utils.MutableEnumMap
import com.tgaetaprojects.biophilia.utils.MutableEnumSet
import com.tgaetaprojects.biophilia.utils.buildEnumMap

import com.tgaetaprojects.biophilia.game.Direction.*

/**
 * Enum class for all 54 vertices on the map.
 */
enum class Vertex {
    V0, V1, V2, V3, V4, V5, V6, V7, V8, V9,
    V10, V11, V12, V13, V14, V15, V16, V17, V18, V19,
    V20, V21, V22, V23, V24, V25, V26, V27, V28, V29,
    V30, V31, V32, V33, V34, V35, V36, V37, V38, V39,
    V40, V41, V42, V43, V44, V45, V46, V47, V48, V49,
    V50, V51, V52, V53;

    val neighbors: Map<Vertex, Edge> by lazy {
        val q = (ordinal / 2) % 7
        val r = (ordinal / 2) / 7 - 3

        buildEnumMap {
            if (ordinal % 2 == 0) { // Y shape
                fromCoordinates(q + NE.q, r + NE.r, SW)?.let {
                    put(it, Edge.fromCoordinate(q, r, NW)!!)
                }
                fromCoordinates(q + NW.q, r + NW.r, SW)?.let {
                    put(it, Edge.fromCoordinate(q, r, NE)!!)
                }
                fromCoordinates(q + 1, r - 2, SW)?.let {
                    put(it, Edge.fromCoordinate(q, r, S)!!) }
            } else {
                fromCoordinates(q, r + 1, NE)?.let {
                    put(it, Edge.fromCoordinate(q, r, SE)!!)
                }
                fromCoordinates(q - 1, r + 1, NE)?.let { put(it, Edge.E1) }
                fromCoordinates(q - 1, r + 2, NE)?.let { put(it, Edge.E2) }
            }
        }
    }

    companion object {
        private val board: Array<Vertex?>

        init {
            val verts = entries.iterator()
            board = Array(86){ i ->
                val q: Int = (i / 2) % 7
                val r: Int = (i / 2) / 7 - 3
                // Include the vertex if at least one of the adjacent hexes are on the board
                if (Hex.isValidTile(q, r)) {
                    verts.next()
                } else if (i % 2 == 0) {
                    if (Hex.isValidTile(q - 1, r) || Hex.isValidTile(q - 1, r + 1)) {
                        verts.next()
                    } else null
                } else {
                    if (Hex.isValidTile(q + 1, r) || Hex.isValidTile(q + 1, r - 1)) {
                        verts.next()
                    } else null
                }
            }
            if(verts.hasNext()) throw IllegalStateException("Not all vertices were assigned")
        }

        fun fromCoordinates(q: Int, r: Int, direction: Direction): Vertex? {
            val index = when(direction) {
                NE -> ((r + 3) * 7 + q) * 2  // North
                NW -> ((r + 2) * 7 + q) * 2 + 1
                W -> ((r + 4) * 7 + q - 1) * 2
                SW -> ((r + 3) * 7 + q) * 2 + 1 // South
                SE -> ((r + 4) * 7 + q) * 2
                E -> ((r + 1) * 7 + q + 1) * 2 + 1
            }
            return board.getOrNull(index)
        }
    }
}
