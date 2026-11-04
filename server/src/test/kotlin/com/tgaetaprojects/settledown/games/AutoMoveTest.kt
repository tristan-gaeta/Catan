package com.tgaetaprojects.settledown.games

import com.tgaetaprojects.settledown.game.DevelopmentCardType
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.ActionResult
import com.tgaetaprojects.settledown.game.engine.Game
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.ResourceCounts
import com.tgaetaprojects.settledown.game.engine.viewFor
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AutoMoveTest {

    /** Plays only automatic moves, apart from buying and playing cards so that Road Building comes up too. */
    private fun play(game: Game, steps: Int, onPhase: (Phase) -> Unit) {
        repeat(steps) {
            onPhase(game.phase)
            if (game.phase is Phase.GameOver) return
            val me = game.currentPlayer
            when {
                game.phase == Phase.Main && game.playableCount(me, DevelopmentCardType.ROAD_BUILDING) > 0 &&
                        !game.viewFor(me).devCardPlayedThisTurn -> assertAccepted(game.apply(me, Action.PlayRoadBuilding))

                game.phase == Phase.Main && game.player(me).hand.covers(ResourceCounts.DEV_CARD) &&
                        game.devCardsRemaining > 0 -> assertAccepted(game.apply(me, Action.BuyDevCard))

                else -> for (seat in AutoMove.waitingOn(game)) {
                    assertAccepted(game.apply(seat, AutoMove.choose(game, seat)))
                }
            }
        }
    }

    private fun assertAccepted(result: ActionResult) {
        assertIs<ActionResult.Accepted>(result)
    }

    @Test
    fun choose_everyPhaseThatWaits_isAccepted() {
        val seen = mutableSetOf<KClass<out Phase>>()
        for (seed in 1L..8L) {
            val seats = PlayerColor.entries.take(if (seed % 2 == 0L) 4 else 3)
            play(Game(seats, seed, seed * 31), steps = 3_000) { seen += it::class }
        }
        val expected = setOf(
            Phase.RollForFirstPlayer::class,
            Phase.SetupSettlement::class,
            Phase.SetupRoad::class,
            Phase.PreRoll::class,
            Phase.Discard::class,
            Phase.MoveRobber::class,
            Phase.Main::class,
            Phase.RoadBuilding::class,
        )
        assertTrue(seen.containsAll(expected), "Never reached ${expected - seen}")
    }

    @Test
    fun waitingOn_discard_isEveryoneOwing() {
        val game = Game(PlayerColor.entries, 3L, 4L)
        var checked = false
        play(game, steps = 3_000) { phase ->
            if (phase is Phase.Discard) {
                assertEquals(phase.pending.keys, AutoMove.waitingOn(game))
                checked = true
            }
        }
        assertTrue(checked, "The game never needed a discard")
    }

    @Test
    fun waitingOn_otherPhases_isTheCurrentPlayer() {
        val game = Game(PlayerColor.entries, 3L, 4L)
        assertEquals(setOf(game.currentPlayer), AutoMove.waitingOn(game))
    }
}
