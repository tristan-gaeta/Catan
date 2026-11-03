package com.tgaetaprojects.biophilia.game

import kotlin.test.Test

/**
 * Writes docs/diagrams/geometry.json from the real enums, for docs/diagrams/generate.py. Not compiled here: copy it
 * into core/src/jvmTest to run it (see docs/diagrams/README.md).
 */
class GeometryDump {
    @Test
    fun dump() {
        val hexes = Hex.entries.joinToString(",", "[", "]") { h ->
            "{\"name\":\"${h.name}\",\"q\":${h.q},\"r\":${h.r}," +
                "\"v\":" + h.vertices.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value.ordinal}" } +
                ",\"e\":" + h.edges.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value.ordinal}" } + "}"
        }
        val sides = (0 until FramePiece.SIDES).joinToString(",", "[", "]") { side ->
            FramePiece.sideEdges(side).joinToString(",", "[", "]") { it.ordinal.toString() }
        }
        val spirals = Direction.entries.joinToString(",", "{", "}") { d ->
            "\"$d\":" + Hex.spiralInwardCC(d).joinToString(",", "[", "]") { "\"${it.name}\"" }
        }
        val edgeVerts = Edge.entries.joinToString(",", "[", "]") { e ->
            e.vertices.joinToString(",", "[", "]") { it.ordinal.toString() }
        }
        val json = "{\"hexes\":$hexes,\"sides\":$sides,\"spirals\":$spirals,\"edgeVerts\":$edgeVerts," +
            "\"sample\":${layoutJson(BoardLayout.generate(42))},\"fixed\":${layoutJson(BoardLayout.fixedSetup)}}"
        java.io.File("../docs/diagrams/geometry.json").writeText(json)
    }

    private fun layoutJson(layout: BoardLayout): String =
        "{\"frame\":" + layout.frame.joinToString(",", "[", "]") { "\"$it\"" } +
            ",\"terrain\":" + layout.terrain.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" } +
            ",\"numbers\":" + layout.numbers.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value.value}" } +
            ",\"ports\":" +
            layout.ports.entries.joinToString(",", "{", "}") { "\"${it.key.ordinal}\":\"${it.value}\"" } + "}"
}
