package com.tgaetaprojects.settledown.ui.game

import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.Phase
import com.tgaetaprojects.settledown.game.engine.RuleViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        session.tool = BuildTool.ROAD
        assertTrue(session.targets().edges.isNotEmpty())
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
}
