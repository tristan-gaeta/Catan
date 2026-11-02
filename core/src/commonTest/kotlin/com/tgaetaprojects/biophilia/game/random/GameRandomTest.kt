package com.tgaetaprojects.biophilia.game.random

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Golden values computed with an independent Python port of the published algorithms. If any of these change,
 * every recorded game replays differently: that needs a `Game.RULES_VERSION` bump, not a new golden value.
 */
class GameRandomTest {

    @Test
    fun splitMix64MatchesItsPublishedTestVector() {
        val mix = SplitMix64(1234567)
        val expected = listOf(6457827717110365317uL, 3203168211198807973uL, 9817491932198370423uL)
        assertEquals(expected, List(3) { mix.next().toULong() })
    }

    @Test
    fun xoshiroSequenceIsPinned() {
        val random = GameRandom(42)
        assertEquals(
            listOf(
                -1651223871,
                1762251840,
                1632151183,
                1417845339,
                -968303052,
                -305620314,
                -15323469,
                -1419553110
            ),
            List(8) { random.nextInt() }
        )
    }

    @Test
    fun boundedDrawsArePinned() {
        val random = GameRandom(42)
        assertEquals(
            listOf(3, 2, 2, 1, 4, 5, 5, 4, 3, 5, 3, 0, 1, 2, 2, 3, 3, 0, 2, 2),
            List(20) { random.nextInt(6) }
        )
    }

    @Test
    fun shuffleAndPickArePinned() {
        val items = (0 until 10).toMutableList()
        items.shuffleWith(GameRandom(7))
        assertEquals(listOf(4, 5, 6, 1, 3, 8, 7, 2, 9, 0), items)
        assertEquals(0, (0 until 10).toList().pickWith(GameRandom(7)))
    }

    @Test
    fun aResumedOrCopiedGeneratorContinuesTheSameSequence() {
        val random = GameRandom(99)
        repeat(5) { random.nextInt() }
        val resumed = GameRandom.fromState(random.state())
        val copy = random.copy()

        val expected = List(10) { random.nextInt() }
        assertEquals(expected, List(10) { resumed.nextInt() })
        assertEquals(expected, List(10) { copy.nextInt() })
    }

    @Test
    fun invalidInputsAreRejected() {
        assertFailsWith<IllegalArgumentException> { GameRandom.fromState(IntArray(4)) }
        assertFailsWith<IllegalArgumentException> { GameRandom.fromState(IntArray(3) { 1 }) }
        assertFailsWith<IllegalArgumentException> { GameRandom(1).nextInt(0) }
        assertFailsWith<IllegalArgumentException> { emptyList<Int>().pickWith(GameRandom(1)) }
    }

    @Test
    fun boundedDrawsStayInRangeAndCoverIt() {
        val random = GameRandom(1)
        for (bound in listOf(1, 2, 3, 6, 7, 25, 1_000_003)) {
            val seen = HashSet<Int>()
            repeat(5_000) {
                val n = random.nextInt(bound)
                check(n in 0 until bound) { "$n out of 0 until $bound" }
                seen += n
            }
            if (bound <= 25) assertEquals(
                bound,
                seen.size,
                "every value below $bound should appear"
            )
        }
    }
}
