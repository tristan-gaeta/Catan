package com.tgaetaprojects.biophilia.game.codec

import com.tgaetaprojects.biophilia.game.BoardLayout
import com.tgaetaprojects.biophilia.game.PlayerColor
import com.tgaetaprojects.biophilia.game.codec.ActionCodec.readAction
import com.tgaetaprojects.biophilia.game.codec.ActionCodec.writeAction
import com.tgaetaprojects.biophilia.game.codec.LayoutCodec.readLayout
import com.tgaetaprojects.biophilia.game.codec.LayoutCodec.writeLayout
import com.tgaetaprojects.biophilia.game.engine.Action
import com.tgaetaprojects.biophilia.game.engine.ActionResult
import com.tgaetaprojects.biophilia.game.engine.Game
import com.tgaetaprojects.biophilia.game.engine.GameEvent

/** One accepted action in a [GameRecord]. */
data class RecordedAction(val actor: PlayerColor, val action: Action)

/**
 * A complete, replayable game: who sat where, both seeds, and every accepted action in order (including the
 * opening rolls for the first player). Because the engine is
 * deterministic, that's enough to rebuild every position, so no states or random outcomes are stored. A whole game
 * is around 1 KB.
 *
 * The layout is stored as well as the board seed. If the board generator ever changes, [replay] notices the
 * mismatch instead of silently replaying the moves on a different island.
 *
 * Records contain the private game seed, which reveals every roll of the game. Keep them on the server and in the
 * database; never send one to a client while the game is running.
 */
class GameRecord(
    val rulesVersion: Int,
    /** Colors in seating order, as passed to [Game]. */
    val seats: List<PlayerColor>,
    val boardSeed: Long,
    val gameSeed: Long,
    val layout: BoardLayout,
    actions: List<RecordedAction>
) {
    val actions: List<RecordedAction> = actions.toList()

    /**
     * Rebuilds the game by replaying every action, calling [onAction] after each one with the game as it now stands
     * and the events produced. The same [Game] instance is passed every time, so copy it if you need to keep a
     * position. Returns the final game.
     *
     * Throws [IllegalStateException] if the record was made with other rules, on another layout, or contains an
     * action the engine refuses.
     */
    fun replay(onAction: (game: Game, action: RecordedAction, events: List<GameEvent>) -> Unit = { _, _, _ -> }): Game {
        check(rulesVersion == Game.RULES_VERSION) {
            "Recorded with rules version $rulesVersion; this engine is version ${Game.RULES_VERSION}"
        }
        val game = Game(seats, boardSeed, gameSeed)
        check(game.board.layout == layout) { "The board seed no longer generates the recorded layout" }
        for ((index, recorded) in actions.withIndex()) {
            val result = game.apply(recorded.actor, recorded.action)
            check(result is ActionResult.Accepted) { "Action $index ($recorded) was refused on replay: $result" }
            onAction(game, recorded, result.events)
        }
        return game
    }

    fun encode(): ByteArray = BitWriter().apply {
        header(BlobKind.RECORD)
        varint(rulesVersion)
        bits(seats.size - 3, 1)
        for (color in seats) code(color)
        long(boardSeed)
        long(gameSeed)
        writeLayout(layout)
        varint(actions.size)
        for ((actor, action) in actions) writeAction(actor, action)
    }.toByteArray()

    companion object {
        /** Decodes a record. The rules version is only checked when the record is replayed. */
        fun decode(bytes: ByteArray): GameRecord = decoding("game record", bytes) {
            header(BlobKind.RECORD)
            val rulesVersion = varint()
            val seats = List(bits(1) + 3) { code<PlayerColor>() }
            require(seats.toSet().size == seats.size) { "Duplicate colors $seats" }
            val boardSeed = long()
            val gameSeed = long()
            val layout = readLayout()
            val actions = List(count(minBitsEach = ActionCodec.MIN_ACTION_BITS)) {
                readAction().let { (actor, action) ->
                    RecordedAction(
                        actor,
                        action
                    )
                }
            }
            GameRecord(rulesVersion, seats, boardSeed, gameSeed, layout, actions)
        }
    }
}

/**
 * Plays a game while recording it: use [apply] in place of [Game.apply]. Only accepted actions are recorded,
 * since refused ones change nothing.
 */
class GameRecorder(
    seats: List<PlayerColor>,
    private val boardSeed: Long,
    private val gameSeed: Long
) {
    val game = Game(seats, boardSeed, gameSeed)
    private val actions = mutableListOf<RecordedAction>()

    fun apply(actor: PlayerColor, action: Action): ActionResult =
        game.apply(actor, action)
            .also { if (it is ActionResult.Accepted) actions += RecordedAction(actor, action) }

    /** The record so far; it can be taken at any point, not only once the game is over. */
    fun record(): GameRecord =
        GameRecord(
            Game.RULES_VERSION,
            game.seatOrder,
            boardSeed,
            gameSeed,
            game.board.layout,
            actions
        )
}
