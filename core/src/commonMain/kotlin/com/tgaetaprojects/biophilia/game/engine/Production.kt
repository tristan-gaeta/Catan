package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.DiceTotal
import com.tgaetaprojects.biophilia.game.GameBoard
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.Resource
import com.tgaetaprojects.biophilia.utils.MutableEnumMap

/**
 * What each player is owed for [roll] before the bank is consulted: 1 card per settlement and 2 per city on every
 * hex showing [roll], except the hex holding the robber.
 */
internal fun GameBoard.productionClaims(roll: DiceTotal): Map<PlayerColor, ResourceCounts> {
    val claims = MutableEnumMap<PlayerColor, ResourceCounts>()
    for (hex in Hex.entries) {
        if (numberTiles[hex] != roll || hex == robber) continue
        val resource = biomes.getValue(hex).resource ?: continue
        for (vertex in hex.vertices.values) {
            val building = buildings[vertex] ?: continue
            val yield = if (building.isCity) 2 else 1
            claims[building.color] =
                (claims[building.color] ?: ResourceCounts.EMPTY) + ResourceCounts.of(
                    resource,
                    yield
                )
        }
    }
    return claims
}

/**
 * Applies the bank-shortage rule to [claims], resource by resource. If the bank can cover every claim for a
 * resource, everyone is paid. If not, nobody gets that resource, unless only one player claimed it, in which case
 * they get whatever the bank has left. Other resources are unaffected.
 */
internal fun settleProduction(
    claims: Map<PlayerColor, ResourceCounts>,
    bank: ResourceCounts
): Map<PlayerColor, ResourceCounts> {
    val paid = MutableEnumMap<PlayerColor, ResourceCounts>()
    for (resource in Resource.entries) {
        val claimants = claims.filterValues { it[resource] > 0 }
        val demand = claimants.values.sumOf { it[resource] }
        val payouts: Map<PlayerColor, Int> = when {
            demand == 0 -> continue
            demand <= bank[resource] -> claimants.mapValues { it.value[resource] }
            claimants.size == 1 -> mapOf(claimants.keys.single() to bank[resource])
            else -> continue
        }
        for ((color, count) in payouts) {
            if (count > 0) paid[color] =
                (paid[color] ?: ResourceCounts.EMPTY) + ResourceCounts.of(resource, count)
        }
    }
    return paid
}
