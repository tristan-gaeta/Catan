package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.DiceTotal
import com.tgaetaprojects.biophilia.game.Resource
import com.tgaetaprojects.biophilia.game.random.GameRandom

/**
 * The random events of a turn: the dice and the card taken when robbing. A real game uses [SeededChance]; tests can
 * script outcomes, and bots substitute their own when exploring possible futures on a [Game.copy].
 *
 * (The development deck is shuffled once from the game's own generator when the game starts, so its order is part
 * of the game state rather than a chance event.)
 */
interface Chance {
    fun roll(): DiceTotal

    /** One card taken at random from a non-empty [hand]. */
    fun stealFrom(hand: ResourceCounts): Resource
}

/** Chance driven by the game's seeded generator, so a game replays exactly from its seeds and actions. */
class SeededChance(internal val random: GameRandom) : Chance {
    override fun roll(): DiceTotal = DiceTotal.roll(random)

    override fun stealFrom(hand: ResourceCounts): Resource = hand.cardAt(random.nextInt(hand.total))
}
