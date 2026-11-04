package com.tgaetaprojects.settledown.game

import kotlin.test.Test
import kotlin.test.assertEquals

class DiceTotalTest {
    @Test
    fun pips_everyTotal_matchesTheNumberDiscs() {
        assertEquals(
            listOf(1, 2, 3, 4, 5, 0, 5, 4, 3, 2, 1),
            DiceTotal.entries.map { it.pips }
        )
    }
}
