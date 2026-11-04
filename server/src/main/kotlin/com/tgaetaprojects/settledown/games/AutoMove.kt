package com.tgaetaprojects.settledown.games

import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.Game
import com.tgaetaprojects.settledown.game.engine.Phase

/**
 * The simple legal move the server plays for someone whose time ran out. These are ordinary actions, so a game's
 * record replays as usual. The choices are deterministic, and deliberately plain: they keep the game moving, and
 * aren't meant to play well.
 */
internal object AutoMove {
    /** Who the game is waiting on. Only a discard waits on several players at once. */
    fun waitingOn(game: Game): Set<PlayerColor> = when (val phase = game.phase) {
        is Phase.Discard -> phase.pending.keys
        is Phase.GameOver -> emptySet()
        else -> setOf(game.currentPlayer)
    }

    fun choose(game: Game, seat: PlayerColor): Action = when (val phase = game.phase) {
        is Phase.RollForFirstPlayer, Phase.PreRoll -> Action.RollDice
        is Phase.SetupSettlement -> Action.PlaceSettlement(
            game.legalSettlementSpots(seat).maxBy { vertex ->
                vertex.hexes.sumOf { game.board.numberTiles.getValue(it).pips }
            }
        )

        is Phase.SetupRoad, is Phase.RoadBuilding -> Action.PlaceRoad(game.legalRoadSpots(seat).first())
        is Phase.Discard -> Action.Discard(game.player(seat).hand.firstCards(phase.pending.getValue(seat)))
        is Phase.MoveRobber -> {
            val hex = Hex.entries.first { it != game.board.robber }
            Action.MoveRobber(hex, game.robberVictims(hex).firstOrNull())
        }

        Phase.Main -> Action.EndTurn
        is Phase.GameOver -> error("The game is over")
    }
}
