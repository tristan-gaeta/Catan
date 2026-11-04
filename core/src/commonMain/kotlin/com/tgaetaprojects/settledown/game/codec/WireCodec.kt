package com.tgaetaprojects.settledown.game.codec

import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.game.codec.ActionCodec.readActionBody
import com.tgaetaprojects.settledown.game.codec.ActionCodec.writeActionBody
import com.tgaetaprojects.settledown.game.codec.EventCodec.readEvents
import com.tgaetaprojects.settledown.game.codec.EventCodec.writeEvents
import com.tgaetaprojects.settledown.game.codec.SnapshotCodec.readView
import com.tgaetaprojects.settledown.game.codec.SnapshotCodec.writeView
import com.tgaetaprojects.settledown.game.engine.Action
import com.tgaetaprojects.settledown.game.engine.Game
import com.tgaetaprojects.settledown.game.engine.GameEvent
import com.tgaetaprojects.settledown.game.engine.PlayerView
import com.tgaetaprojects.settledown.game.engine.RuleViolation

/**
 * What a client sends during a live game. It has no actor: the server acts for whichever seat the connection
 * belongs to, so a client has no way to claim someone else's seat.
 *
 * @param clientSeq chosen by the client and echoed back; numbered from 1, because 0 means "none" in
 *   [ServerMessage.Update.lastClientSeq].
 */
data class ClientAction(val clientSeq: Int, val action: Action) {
    init {
        require(clientSeq > 0) { "clientSeq starts at 1, got $clientSeq" }
    }
}

/** What the server sends a seat during a live game. */
sealed interface ServerMessage {
    /**
     * The game changed, or this seat just connected. [view] is the whole new state as this seat may see it, so a
     * client never has to merge changes; [events] are what just happened, for the log and animations.
     *
     * @param lastClientSeq the newest [ClientAction.clientSeq] accepted from this connection, or 0 if none.
     */
    data class Update(
        val view: PlayerView,
        val events: List<GameEvent>,
        val lastClientSeq: Int
    ) : ServerMessage

    /** The action numbered [clientSeq] was refused, and the game didn't change. */
    data class Refused(val clientSeq: Int, val violation: RuleViolation) : ServerMessage

    /** Which seats have a live connection. */
    data class Presence(val connected: Set<PlayerColor>) : ServerMessage

    /**
     * The server will move for [waitingOn] in [remainingMillis] unless they act first. It's a duration rather than a
     * time because client and server clocks can differ. An empty [waitingOn] means no timer is running.
     */
    data class Deadline(val waitingOn: Set<PlayerColor>, val remainingMillis: Int) : ServerMessage
}

/**
 * The binary format of live-game messages. Lobby and account requests are ordinary JSON; only these messages use it.
 *
 * Diagram of what may be sent: [Who may see what](https://github.com/tristan-gaeta/Catan/blob/main/docs/images/trust-boundary.svg).
 */
object WireCodec {
    /** One tag per [ServerMessage] type, written as its ordinal (see [FORMAT_VERSION]). */
    internal enum class Tag { UPDATE, REFUSED, PRESENCE, DEADLINE }

    /**
     * Identifies the formats and rules this build speaks. Clients send it on every request in [VERSION_HEADER], so a
     * mismatched client can be told to update before it tries to decode anything.
     */
    val PROTOCOL_VERSION: String = "$FORMAT_VERSION.${Game.RULES_VERSION}"

    const val VERSION_HEADER = "X-Settledown-Protocol"

    /** WebSocket close code: the game can't continue, so don't reconnect. */
    const val CLOSE_GAME_UNAVAILABLE: Short = 4001

    /** WebSocket close code: this seat connected from somewhere else, so don't reconnect. */
    const val CLOSE_REPLACED: Short = 4002

    /** WebSocket close code: the client sent something undecodable or too fast. */
    const val CLOSE_PROTOCOL_ERROR: Short = 4003

    fun encode(message: ClientAction): ByteArray = BitWriter().apply {
        header(BlobKind.CLIENT_MESSAGE)
        varint(message.clientSeq)
        writeActionBody(message.action)
    }.toByteArray()

    fun decodeClientAction(bytes: ByteArray): ClientAction = decoding("client action", bytes) {
        header(BlobKind.CLIENT_MESSAGE)
        ClientAction(varint(), readActionBody())
    }

    fun encode(message: ServerMessage): ByteArray = BitWriter().apply {
        header(BlobKind.SERVER_MESSAGE)
        when (message) {
            is ServerMessage.Update -> {
                code(Tag.UPDATE)
                writeView(message.view)
                writeEvents(message.events)
                varint(message.lastClientSeq)
            }

            is ServerMessage.Refused -> {
                code(Tag.REFUSED)
                varint(message.clientSeq)
                code(message.violation)
            }

            is ServerMessage.Presence -> {
                code(Tag.PRESENCE)
                writeColors(message.connected)
            }

            is ServerMessage.Deadline -> {
                code(Tag.DEADLINE)
                writeColors(message.waitingOn)
                varint(message.remainingMillis)
            }
        }
    }.toByteArray()

    fun decodeServerMessage(bytes: ByteArray): ServerMessage = decoding("server message", bytes) {
        header(BlobKind.SERVER_MESSAGE)
        when (code<Tag>()) {
            Tag.UPDATE -> ServerMessage.Update(readView(), readEvents(), varint())
            Tag.REFUSED -> ServerMessage.Refused(varint(), code())
            Tag.PRESENCE -> ServerMessage.Presence(readColors())
            Tag.DEADLINE -> ServerMessage.Deadline(readColors(), varint())
        }
    }

    private fun BitWriter.writeColors(colors: Set<PlayerColor>) {
        for (color in PlayerColor.entries) bool(color in colors)
    }

    private fun BitReader.readColors(): Set<PlayerColor> =
        PlayerColor.entries.filter { bool() }.toSet()
}
