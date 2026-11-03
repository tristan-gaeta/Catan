package com.tgaetaprojects.settledown.game

import com.tgaetaprojects.settledown.game.Direction.E
import com.tgaetaprojects.settledown.game.Direction.NE
import com.tgaetaprojects.settledown.game.Direction.NW
import com.tgaetaprojects.settledown.game.Direction.SE
import com.tgaetaprojects.settledown.game.Direction.SW
import com.tgaetaprojects.settledown.game.Direction.W
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The variable setup from the 6th-edition rulebook (page 11): frame, hexes and number discs. */
class BoardLayoutTest {

    // ---- Coastline and frame ----

    @Test
    fun theCoastlineIsOneUnbrokenCounterClockwiseLoop() {
        val coast = BoardLayout.coastline
        assertEquals(Edge.entries.filter { it.isCoastal }.toSet(), coast.toSet())
        assertEquals(30, coast.size)
        for (i in coast.indices) {
            val shared = coast[i].vertices intersect coast[(i + 1) % coast.size].vertices
            assertEquals(1, shared.size, "coastal edges $i and ${i + 1} must meet")
        }
    }

    @Test
    fun aFramePieceRunsFromOneCornersLastEdgeToTheNextCornersSecond() {
        // the top side: corner C's north-west edge, both of B's, then A's first two (all counter-clockwise)
        assertEquals(
            listOf(
                Hex.C.edges[NW],
                Hex.B.edges[NE],
                Hex.B.edges[NW],
                Hex.A.edges[NE],
                Hex.A.edges[NW]
            ),
            FramePiece.sideEdges(0)
        )
        // pieces meet where a corner hex's coast turns outward, i.e. at an intersection on only that corner
        for (side in 0 until FramePiece.SIDES) {
            val next = FramePiece.sideEdges((side + 1) % FramePiece.SIDES).first()
            val join = (FramePiece.sideEdges(side).last().vertices intersect next.vertices).single()
            assertEquals(1, join.hexes.size, "side $side should end at a corner's outer point")
        }
    }

    @Test
    fun theCoastRunsCounterClockwiseAndEachPieceTurnsLikeItsArtwork() {
        // in axial coordinates, `b` lies counter-clockwise of `a` around the center exactly when this is positive
        fun cross(q1: Int, r1: Int, q2: Int, r2: Int) = r1 * q2 - q1 * r2

        val coast = BoardLayout.coastline
        for (i in coast.indices) {
            val a = coast[i]
            val b = coast[(i + 1) % coast.size]
            val hexA = a.hexes.single()
            val hexB = b.hexes.single()
            if (hexA == hexB) {
                val dirA = Direction.entries.first { hexA.edges[it] == a }
                val dirB = Direction.entries.first { hexB.edges[it] == b }
                assertTrue(
                    cross(dirA.q, dirA.r, dirB.q, dirB.r) > 0,
                    "edges $i and ${i + 1} turn clockwise around $hexA"
                )
            } else {
                assertTrue(
                    cross(hexA.q, hexA.r, hexB.q, hexB.r) > 0,
                    "the coast steps clockwise from $hexA to $hexB"
                )
            }
        }

        // Read counter-clockwise, a piece's coast in the artwork turns right, left, right, left: its four inner joins
        // alternate between an intersection shared by two hexes and one on a single hex, starting with two. A piece
        // shifted by one edge, or mirrored, gives a different pattern.
        for (side in 0 until FramePiece.SIDES) {
            val joins = FramePiece.sideEdges(side)
                .zipWithNext { a, b -> (a.vertices intersect b.vertices).single().hexes.size }
            assertEquals(listOf(2, 1, 2, 1), joins, "side $side")
        }
    }

    @Test
    fun harborsSitWhereTheirFramePieceShowsThem() {
        for (seed in 0L until 200L) {
            val layout = BoardLayout.generate(seed)
            assertEquals(FramePiece.entries.toSet(), layout.frame.toSet(), "seed $seed")
            for ((side, piece) in layout.frame.withIndex()) {
                for ((edge, harbor) in FramePiece.sideEdges(side).zip(piece.harbors)) {
                    assertEquals(harbor, layout.ports[edge], "seed $seed side $side")
                }
            }
            val ports = layout.ports
            assertEquals(9, ports.size)
            assertEquals(4, ports.values.count { it == PortType.GENERIC })
            assertEquals(PortType.entries.toSet(), ports.values.toSet())
            assertEquals(
                18,
                ports.keys.flatMap { it.vertices }.toSet().size,
                "seed $seed: harbors share an intersection"
            )
        }
    }

    @Test
    fun thePiecesAreDeclaredInThePrintedFramesOrder() {
        // the fixed setup on rulebook page 4, sides 0 (top) to 5, counter-clockwise
        val fixedSetup = listOf(
            FramePiece.WOOL, FramePiece.ORE, FramePiece.GRAIN,
            FramePiece.LUMBER, FramePiece.BRICK, FramePiece.GENERIC
        )
        val declared = FramePiece.entries
        assertEquals(fixedSetup, List(FramePiece.SIDES) { declared[(it + 1) % FramePiece.SIDES] })
    }

    @Test
    fun theFixedSetupHasThePrintedHarbors() {
        // read off the artwork on rulebook pages 4 and 5
        val printed = mapOf(
            Hex.A.edges.getValue(NW) to PortType.GENERIC,
            Hex.B.edges.getValue(NE) to PortType.WOOL,
            Hex.D.edges.getValue(W) to PortType.ORE,
            Hex.M.edges.getValue(W) to PortType.GRAIN,
            Hex.Q.edges.getValue(SW) to PortType.GENERIC,
            Hex.R.edges.getValue(SE) to PortType.LUMBER,
            Hex.P.edges.getValue(SE) to PortType.BRICK,
            Hex.L.edges.getValue(E) to PortType.GENERIC,
            Hex.G.edges.getValue(NE) to PortType.GENERIC,
        )
        assertEquals(printed, BoardLayout.fixedSetup.ports)
        assertEquals(Hex.A, BoardLayout.fixedSetup.desert)
    }

    @Test
    fun theFramePiecesCarryTheRulebooksHarbors() {
        // three pieces with one harbor on the middle edge, three with a 2:1 second and a 3:1 last
        val singles = FramePiece.entries.filter { it.harbors.count { h -> h != null } == 1 }
        assertEquals(
            setOf(PortType.GENERIC, PortType.LUMBER, PortType.ORE),
            singles.map { it.harbors[2] }.toSet()
        )
        val doubles = FramePiece.entries - singles.toSet()
        assertTrue(doubles.all { it.harbors[4] == PortType.GENERIC && it.harbors[1]!!.exchangeRate == 2 })
        assertEquals(
            setOf(PortType.WOOL, PortType.BRICK, PortType.GRAIN),
            doubles.map { it.harbors[1] }.toSet()
        )
    }

    @Test
    fun shufflingPutsEveryPieceOnEverySide() {
        val seen = List(FramePiece.SIDES) { mutableSetOf<FramePiece>() }
        for (seed in 0L until 300L) {
            BoardLayout.generate(seed).frame.forEachIndexed { side, piece -> seen[side] += piece }
        }
        assertTrue(seen.all { it == FramePiece.entries.toSet() }, "pieces by side: $seen")
    }

    // ---- Hexes and number discs ----

    @Test
    fun numberDiscsSpiralInwardFromACornerSkippingTheDesert() {
        // the A–R disc values from the rulebook's variable setup example
        val discs = listOf(5, 2, 6, 3, 8, 10, 9, 12, 11, 4, 8, 10, 9, 4, 5, 6, 3, 11)
        for (seed in 0L until 100L) {
            val layout = BoardLayout.generate(seed)
            val fromSomeCorner = Direction.entries.any { corner ->
                Hex.spiralInwardCC(corner).filter { it != layout.desert }
                    .map { layout.numbers.getValue(it).value } == discs
            }
            assertTrue(fromSomeCorner, "seed $seed")
            assertEquals(DiceTotal.SEVEN, layout.numbers[layout.desert])
            assertEquals(layout.desert, GameBoard(layout).robber)
        }
    }

    @Test
    fun layoutsAreValidatedWhenBuiltFromParts() {
        val layout = BoardLayout.generate(1)
        assertEquals(layout, BoardLayout.of(layout.frame, layout.terrain, layout.numbers))

        val repeatedPiece = layout.frame.toMutableList().also { it[0] = it[1] }
        assertFailsWith<IllegalArgumentException> {
            BoardLayout.of(
                repeatedPiece,
                layout.terrain,
                layout.numbers
            )
        }
        val other = Hex.entries.first { it != layout.desert }
        val twoDeserts = layout.terrain.toMutableMap().also { it[other] = Terrain.DESERT }
        assertFailsWith<IllegalArgumentException> {
            BoardLayout.of(
                layout.frame,
                twoDeserts,
                layout.numbers
            )
        }
    }
}
