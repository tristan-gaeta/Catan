package com.tgaetaprojects.biophilia.game.codec

import com.tgaetaprojects.biophilia.game.BoardLayout
import com.tgaetaprojects.biophilia.game.DiceTotal
import com.tgaetaprojects.biophilia.game.FramePiece
import com.tgaetaprojects.biophilia.game.Hex
import com.tgaetaprojects.biophilia.game.Terrain
import com.tgaetaprojects.biophilia.utils.MutableEnumMap

/**
 * Encodes a [BoardLayout] in 21 bytes: the frame piece on each of the 6 sides (3 bits each, which fixes the
 * harbors), then per hex, in hex code order, a 3-bit terrain and a 4-bit number. Decoding re-validates the layout,
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
        for (piece in layout.frame) code(piece, Codes.FRAME)
        for (hex in Codes.HEX.inCodeOrder) {
            code(layout.terrain.getValue(hex), Codes.TERRAIN)
            code(layout.numbers.getValue(hex), Codes.DICE)
        }
    }

    internal fun BitReader.readLayout(): BoardLayout {
        val frame = List(FramePiece.SIDES) { code(Codes.FRAME) }
        val terrain = MutableEnumMap<Hex, Terrain>()
        val numbers = MutableEnumMap<Hex, DiceTotal>()
        for (hex in Codes.HEX.inCodeOrder) {
            terrain[hex] = code(Codes.TERRAIN)
            numbers[hex] = code(Codes.DICE)
        }
        return BoardLayout.of(frame, terrain, numbers)
    }
}
