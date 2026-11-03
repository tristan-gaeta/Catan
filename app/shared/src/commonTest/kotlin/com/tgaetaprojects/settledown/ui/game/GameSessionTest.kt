package com.tgaetaprojects.settledown.ui.game

import com.tgaetaprojects.settledown.game.DevelopmentCardType
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.GameEvent
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.RuleViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class GameSessionTest {

    private fun rolledForFirstPlayer(): GameSession {
        val session = GameSession.new(players = 3, boardSeed = 1L, gameSeed = 2L)
        while (session.phase is Phase.RollForFirstPlayer) assertNull(session.act(Action.RollDice))
        return session
    }

    @Test
    fun setupSettlement_targets_areEveryLegalSpot() {
        val session = rolledForFirstPlayer()
        assertTrue(session.phase is Phase.SetupSettlement)
        assertEquals(54, session.targets().vertices.size)
    }

    @Test
    fun tapVertex_inSetup_placesSettlement() {
        val session = rolledForFirstPlayer()
        val actor = session.actor
        val vertex = session.targets().vertices.first()

        assertNull(session.tapVertex(vertex))

        assertEquals(actor, session.view.buildings.getValue(vertex).color)
    }

    @Test
    fun tapVertex_inSetup_targetsAdjacentRoads() {
        val session = rolledForFirstPlayer()
        val vertex = session.targets().vertices.first()

        session.tapVertex(vertex)

        assertTrue(session.phase is Phase.SetupRoad)
        assertEquals(vertex.edges, session.targets().edges)
    }

    @Test
    fun act_rejected_leavesViewUnchanged() {
        val session = rolledForFirstPlayer()
        val before = session.view

        assertEquals(RuleViolation.WRONG_PHASE, session.act(Action.EndTurn))
        assertEquals(before, session.view)
    }

    @Test
    fun mainPhase_withoutTool_hasNoTargets() {
        val session = GameSession.demo(turns = 3)
        assertEquals(Phase.Main, session.phase)
        assertTrue(session.targets().isEmpty)
    }

    @Test
    fun mainPhase_roadTool_targetsRoadSpots() {
        val session = GameSession.demo(turns = 3)
        session.selectTool(BuildTool.ROAD)
        assertTrue(session.targets().edges.isNotEmpty())
    }

    @Test
    fun selectTool_unaffordable_isIgnored() {
        val session = GameSession.demo()
        val tool = BuildTool.entries.first { !session.canBuild(it) }

        session.selectTool(tool)

        assertNull(session.tool)
        assertTrue(session.targets().isEmpty)
    }

    @Test
    fun demo_isDeterministic() {
        val a = GameSession.demo()
        val b = GameSession.demo()
        assertEquals(a.view, b.view)
        assertEquals(a.log.toList(), b.log.toList())
        assertTrue(a.view.buildings.size >= 8)
    }

    @Test
    fun act_accepted_logsNewestFirst() {
        val session = rolledForFirstPlayer()
        session.tapVertex(session.targets().vertices.first())
        assertTrue(session.log.first().endsWith("built a settlement"))
    }

    @Test
    fun log_steal_hidesStolenResource() {
        val session = sessionWith(players = 4) { log.any { "stole" in it } }
        val steals = session.log.filter { "stole" in it }
        assertTrue(steals.all { it.contains("stole a card from") }, "$steals")
    }

    @Test
    fun log_devCardBought_hidesCardType() {
        assertEquals(
            "White bought a development card",
            describe(GameEvent.DevCardBought(PlayerColor.WHITE, DevelopmentCardType.VICTORY_POINT).publicView()),
        )
    }

    @Test
    fun rollForFirstPlayer_doesNotNeedHandOff() {
        val session = GameSession.new(players = 3, boardSeed = 1L, gameSeed = 2L)
        assertFalse(session.needsHandOff)
    }

    @Test
    fun setup_newActor_needsHandOffUntilRevealed() {
        val session = rolledForFirstPlayer()
        assertTrue(session.needsHandOff)

        session.reveal()

        assertFalse(session.needsHandOff)
    }

    @Test
    fun tieBreak_actor_isFirstTiedPlayer() {
        val session = sessionWith(players = 4) { isTieBreak }
        val phase = session.phase as Phase.RollForFirstPlayer
        assertEquals(phase.contenders.first(), session.actor)
        assertTrue(prompt(session.phase, session.actor, session.isTieBreak).startsWith("Tie!"))
    }

    @Test
    fun discard_severalPending_actorIsFirstInTurnOrder() {
        val session = sessionWith(players = 4) { (phase as? Phase.Discard)?.pending?.size?.let { it >= 2 } == true }
        val pending = (session.phase as Phase.Discard).pending
        assertEquals(session.view.turnOrder.first { it in pending }, session.actor)
    }

    @Test
    fun discard_eachDiscarder_needsHandOff() {
        val session = sessionWith(players = 4) { (phase as? Phase.Discard)?.pending?.size?.let { it >= 2 } == true }
        session.reveal()
        val first = session.actor

        session.act(Action.Discard(firstCards(session.view.hand, (session.phase as Phase.Discard).pending.getValue(first))))

        assertTrue(session.actor != first)
        assertTrue(session.needsHandOff)
    }

    @Test
    fun discard_lastDiscarder_handsBackForRobber() {
        val session = sessionWith(players = 4) { (phase as? Phase.Discard)?.pending?.size?.let { it >= 2 } == true }
        val roller = session.view.currentPlayer
        var lastDiscarder = session.actor
        while (session.phase is Phase.Discard) {
            session.reveal()
            lastDiscarder = session.actor
            val owed = (session.phase as Phase.Discard).pending.getValue(lastDiscarder)
            assertNull(session.act(Action.Discard(firstCards(session.view.hand, owed))))
        }

        assertTrue(session.phase is Phase.MoveRobber)
        assertEquals(roller, session.actor)
        assertEquals(lastDiscarder != roller, session.needsHandOff)
    }

    /** The first seeded game whose scripted play reaches [condition]; fails if none of the first 50 seeds do. */
    private fun sessionWith(players: Int, condition: GameSession.() -> Boolean): GameSession {
        for (seed in 1L..50L) {
            val session = GameSession.new(players, boardSeed = seed, gameSeed = seed * 31)
            session.playScripted(turns = 60, stopWhen = condition)
            if (session.condition()) return session
        }
        fail("No seed reached the condition")
    }
}
