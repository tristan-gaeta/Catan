package com.tgaetaprojects.biophilia.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardGraphTest {

    /** Pixel position of a hex corner (unit size, pointy top, y down), rounded so it can be compared. */
    private fun cornerPoint(hex: Hex, dir: Direction): Pair<Long, Long> {
        val angle = (60.0 * dir.ordinal + 90.0) * PI / 180.0
        val x = sqrt(3.0) * (hex.q + hex.r / 2.0) + cos(angle)
        val y = 1.5 * hex.r - sin(angle)
        return (x * 1000).roundToLong() to (y * 1000).roundToLong()
    }

    @Test
    fun hexLookupRoundTrips() {
        for (hex in Hex.entries) assertEquals(hex, Hex.fromCoordinate(hex.q, hex.r))
        assertNull(Hex.fromCoordinate(3, 0))
        for (dir in Direction.entries) assertEquals(Hex.entries.toSet(), Hex.spiralInwardCC(dir).toSet())
    }

    @Test
    fun eachVertexIsExactlyOnePointOnTheBoard() {
        val points = mutableMapOf<Vertex, Pair<Long, Long>>()
        for (hex in Hex.entries) {
            assertEquals(6, hex.vertices.values.toSet().size)
            for ((dir, vertex) in hex.vertices) {
                val point = cornerPoint(hex, dir)
                assertEquals(point, points.getOrPut(vertex) { point }, "$vertex seen at two positions")
            }
        }
        assertEquals(Vertex.entries.size, points.size)
        assertEquals(points.size, points.values.toSet().size, "two vertices share a position")
    }

    @Test
    fun edgesJoinTheCornersEitherSideOfThem() {
        for (hex in Hex.entries) {
            assertEquals(6, hex.edges.values.toSet().size)
            for ((dir, edge) in hex.edges) {
                val prev = Direction.entries[(dir.ordinal + 5) % 6]
                assertEquals(setOf(hex.vertices[dir], hex.vertices[prev]), edge.vertices)
                assertTrue(hex in edge.hexes)
                hex.getNeighbor(dir)?.let { assertEquals(edge, it.edges[dir.opposite]) }
            }
        }
    }

    @Test
    fun incidenceCountsMatchTheStandardBoard() {
        // 30 coastal edges, 42 inland
        assertEquals(30, Edge.entries.count { it.isCoastal })
        assertTrue(Edge.entries.all { it.hexes.size in 1..2 && it.vertices.size == 2 })

        // 18 coastal corners on one tile, 12 on two, 24 inland on three
        val byHexCount = Vertex.entries.groupingBy { it.hexes.size }.eachCount()
        assertEquals(mapOf(1 to 18, 2 to 12, 3 to 24), byHexCount)
    }

    @Test
    fun vertexAdjacencyIsSymmetricAndConsistent() {
        for (vertex in Vertex.entries) {
            assertTrue(vertex.neighbors.size in 2..3)
            assertEquals(vertex.edges, vertex.neighbors.values.toSet())
            for ((other, edge) in vertex.neighbors) {
                assertEquals(edge, other.neighbors[vertex])
                assertEquals(setOf(vertex, other), edge.vertices)
            }
            for (hex in vertex.hexes) assertTrue(vertex in hex.vertices.values)
        }
    }

    @Test
    fun lookupsOffTheBoardReturnNull() {
        for (dir in Direction.entries) {
            assertNull(Vertex.fromCoordinates(5, 0, dir))
            assertNull(Edge.fromCoordinate(0, -5, dir))
        }
        // sea cells only own the corners and sides touching land
        assertNull(Edge.fromCoordinate(3, -3, Direction.NE))
        assertNull(Vertex.fromCoordinates(3, -3, Direction.NE))
    }
}
