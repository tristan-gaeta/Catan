package com.tgaetaprojects.settledown.game.codec

import com.tgaetaprojects.settledown.game.BoardLayout
import com.tgaetaprojects.settledown.game.DiceTotal
import com.tgaetaprojects.settledown.game.FramePiece
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.Terrain
import com.tgaetaprojects.settledown.utils.MutableEnumMap

/**
 * Encodes a [BoardLayout] in 21 bytes: the frame piece on each of the 6 sides (3 bits each, which fixes the
 * harbors), then per hex, in [Hex] order, a 3-bit terrain and a 4-bit number. Decoding re-validates the layout,
 * so corrupt data can't produce an impossible board.
 */
object LayoutCodec {
    fun encode(layout: BoardLayout): ByteArray = BitWriter().apply {
        header(BlobKind.LAYOUT)
        writeLayout(layout)
    }.toByteArray()

    fun decode(bytes: ByteArray): BoardLayout = decoding("layout", bytes) {
        header(BlobKind.LAYOUT)
        readLayout()
    }

    internal fun BitWriter.writeLayout(layout: BoardLayout) {
        for (piece in layout.frame) code(piece)
        for (hex in Hex.entries) {
            code(layout.terrain.getValue(hex))
            code(layout.numbers.getValue(hex))
        }
    }

    internal fun BitReader.readLayout(): BoardLayout {
        val frame = List(FramePiece.SIDES) { code<FramePiece>() }
        val terrain = MutableEnumMap<Hex, Terrain>()
        val numbers = MutableEnumMap<Hex, DiceTotal>()
        for (hex in Hex.entries) {
            terrain[hex] = code<Terrain>()
            numbers[hex] = code<DiceTotal>()
        }
        return BoardLayout.of(frame, terrain, numbers)
    }
}
