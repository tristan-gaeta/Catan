package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.DevelopmentCardType
import com.tgaetaprojects.biophilia.game.DevelopmentCardType.*

/** The 25-card base deck, unshuffled: 14 knights, 5 victory points and 2 of each progress card. */
internal fun newDevelopmentDeck(): List<DevelopmentCardType> = buildList {
    repeat(14) { add(KNIGHT) }
    repeat(5) { add(VICTORY_POINT) }
    repeat(2) { add(ROAD_BUILDING) }
    repeat(2) { add(YEAR_OF_PLENTY) }
    repeat(2) { add(MONOPOLY) }
}
