package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.Building
import com.tgaetaprojects.biophilia.game.DevelopmentCardType
import com.tgaetaprojects.biophilia.game.Edge
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.PlayerColor.*
import com.tgaetaprojects.biophilia.game.Vertex
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

/*
 * Test helpers that set up exact positions by writing state directly, bypassing the rules. Only tests use them;
 * the engine itself only changes state through Game.apply.
 */

internal fun newGame(seed: Long = 7L, players: List<PlayerColor> = listOf(RED, WHITE, BLUE)) = Game(players, seed)

/** Skips set-up and makes it [color]'s turn, in [phase]. */
internal fun Game.startTurnFor(color: PlayerColor, phase: Phase = Phase.Main) {
    currentPlayer = color
    turn = maxOf(turn, 1)
    this.phase = phase
    devCardPlayedThisTurn = false
}

/** Moves [cards] from the bank to [color]'s hand. */
internal fun Game.give(color: PlayerColor, cards: ResourceCounts) {
    bank -= cards
    player(color).hand += cards
}

internal fun Game.putSettlement(color: PlayerColor, vertex: Vertex) {
    board.placeBuilding(vertex, Building.settlement(color))
    player(color).settlementsLeft--
}

internal fun Game.putCity(color: PlayerColor, vertex: Vertex) {
    board.placeBuilding(vertex, Building.city(color))
    player(color).citiesLeft--
}

internal fun Game.putRoads(color: PlayerColor, edges: List<Edge>) {
    for (edge in edges) {
        board.placeRoad(edge, color)
        player(color).roadsLeft--
    }
}

/** Moves a card of [type] from the deck to [color]'s hand, as if bought on an earlier turn. */
internal fun Game.giveDevCard(color: PlayerColor, type: DevelopmentCardType) {
    check(devDeck.remove(type)) { "No $type left in the deck" }
    player(color).devCards += type
}

/** Plays out set-up, each player taking the first legal spot. */
internal fun Game.playSetup() {
    while (phase is Phase.SetupSettlement || phase is Phase.SetupRoad) {
        val action = when (phase) {
            is Phase.SetupSettlement -> Action.PlaceSettlement(legalSettlementSpots(currentPlayer).first())
            else -> Action.PlaceRoad(legalRoadSpots(currentPlayer).first())
        }
        apply(currentPlayer, action).accepted()
    }
}

/**
 * A simple path of [length] roads starting at [start], walking greedily to unvisited intersections and never
 * entering [avoid]. Returns the edges and the [length] + 1 intersections along it.
 */
internal fun pathFrom(start: Vertex, length: Int, avoid: Collection<Vertex> = emptyList()): Pair<List<Edge>, List<Vertex>> {
    val edges = mutableListOf<Edge>()
    val vertices = mutableListOf(start)
    while (edges.size < length) {
        val here = vertices.last()
        val (next, edge) = here.neighbors.entries.firstOrNull { it.key !in vertices && it.key !in avoid }
            ?: fail("Path from $start got stuck after ${edges.size} roads")
        edges += edge
        vertices += next
    }
    return edges to vertices
}

internal fun ActionResult.accepted(): List<GameEvent> =
    assertIs<ActionResult.Accepted>(this, "Expected the action to be accepted").events

internal fun ActionResult.rejectedWith(violation: RuleViolation) =
    assertEquals(ActionResult.Rejected(violation), this)
