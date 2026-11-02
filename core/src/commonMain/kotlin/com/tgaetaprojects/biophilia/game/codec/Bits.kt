package com.tgaetaprojects.biophilia.game.codec

import com.tgaetaprojects.biophilia.game.DevelopmentCardType
import com.tgaetaprojects.biophilia.game.Resource
import com.tgaetaprojects.biophilia.game.engine.DevCardCounts
import com.tgaetaprojects.biophilia.game.engine.ResourceCounts
import kotlin.enums.enumEntries

/** Thrown when bytes can't be decoded: truncated, corrupted, the wrong kind of blob, or an unknown format version. */
class CodecException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * The kinds of top-level blob. Every blob starts with [FORMAT_VERSION] and its kind's ordinal, one byte each, so a
 * decoder can reject the wrong thing up front.
 */
internal enum class BlobKind { LAYOUT, ACTION, SNAPSHOT, VIEW, RECORD }

/**
 * The version of the byte layouts in this package. Bump it on any change to what an encoding writes;
 * `VersionGuardTest` pins this version's bytes and fails if they change without a bump.
 *
 * Enums are written as their ordinal, in just enough bits for their size, so reordering, adding or removing
 * constants in a serialized enum changes the format too. `VersionGuardTest` catches both: its samples use every
 * constant, and it pins each serialized enum's size (an appended constant may not change any existing bytes, but
 * an older reader couldn't decode it). The serialized enums are [BlobKind], `PlayerColor`,
 * `Resource`, `DevelopmentCardType`, `DiceTotal`, `Terrain`, `FramePiece`, `Building`, `Hex`, `Vertex`, `Edge`,
 * and the action and phase tags in `ActionCodec` and `SnapshotCodec`. (`Vertex` and `Edge` ordinals are board
 * positions, assigned in a fixed geometric order, so those two only change if the board does.)
 */
internal const val FORMAT_VERSION = 1

/** Bits needed to store any of [count] distinct values, e.g. 54 vertices need 6. */
internal fun bitsFor(count: Int): Int = if (count <= 1) 0 else 32 - (count - 1).countLeadingZeroBits()

/** How many bits each count in [ResourceCounts] and [DevCardCounts] takes on the wire (at most 19 or 14). */
internal const val COUNT_WIDTH = 5

/**
 * Appends values to a growing byte array, least significant bit first, with no padding between fields. An enum is
 * written as its ordinal in exactly as many bits as its enum needs.
 */
internal class BitWriter {
    private var buffer = ByteArray(64)
    private var position = 0

    fun bits(value: Int, width: Int) {
        require(width in 0..32) { "width $width" }
        require(width == 32 || value ushr width == 0) { "$value doesn't fit in $width bits" }
        ensureCapacity(position + width)
        for (i in 0 until width) {
            if ((value ushr i) and 1 != 0) {
                val index = position + i
                buffer[index ushr 3] = (buffer[index ushr 3].toInt() or (1 shl (index and 7))).toByte()
            }
        }
        position += width
    }

    fun bool(value: Boolean) = bits(if (value) 1 else 0, 1)

    fun long(value: Long) {
        bits(value.toInt(), 32)
        bits((value ushr 32).toInt(), 32)
    }

    /** A non-negative number in 8-bit groups: 7 bits of value plus a "more follows" bit. */
    fun varint(value: Int) {
        require(value >= 0) { "varints are non-negative, got $value" }
        var rest = value
        while (rest >= 0x80) {
            bits((rest and 0x7F) or 0x80, 8)
            rest = rest ushr 7
        }
        bits(rest, 8)
    }

    inline fun <reified E : Enum<E>> code(value: E) = bits(value.ordinal, bitsFor(enumEntries<E>().size))

    /** An optional enum: a presence bit, then the ordinal if present. */
    inline fun <reified E : Enum<E>> optional(value: E?) {
        bool(value != null)
        if (value != null) code(value)
    }

    /** Each count in [Resource] order. */
    fun counts(value: ResourceCounts) {
        for (resource in Resource.entries) bits(value[resource], COUNT_WIDTH)
    }

    /** Each count in [DevelopmentCardType] order. */
    fun counts(value: DevCardCounts) {
        for (type in DevelopmentCardType.entries) bits(value[type], COUNT_WIDTH)
    }

    fun header(kind: BlobKind) {
        bits(FORMAT_VERSION, 8)
        bits(kind.ordinal, 8)
    }

    fun toByteArray(): ByteArray = buffer.copyOf((position + 7) ushr 3)

    private fun ensureCapacity(bits: Int) {
        val bytes = (bits + 7) ushr 3
        if (bytes > buffer.size) buffer = buffer.copyOf(maxOf(bytes, buffer.size * 2))
    }
}

/** Reads what a [BitWriter] wrote, throwing [CodecException] on anything malformed. */
internal class BitReader(private val bytes: ByteArray) {
    private var position = 0

    fun bits(width: Int): Int {
        if (position + width > bytes.size * 8) throw CodecException("Unexpected end of data")
        var value = 0
        for (i in 0 until width) {
            val index = position + i
            if ((bytes[index ushr 3].toInt() ushr (index and 7)) and 1 != 0) value = value or (1 shl i)
        }
        position += width
        return value
    }

    fun bool(): Boolean = bits(1) == 1

    fun long(): Long = (bits(32).toLong() and 0xFFFFFFFFL) or (bits(32).toLong() shl 32)

    fun varint(): Int {
        var value = 0
        var shift = 0
        while (true) {
            val group = bits(8)
            if (shift == 28 && group > 0x07) throw CodecException("Varint too long")
            value = value or ((group and 0x7F) shl shift)
            if (group and 0x80 == 0) return value
            shift += 7
        }
    }

    /**
     * A count of items that each take at least [minBitsEach] bits. It's checked against the data actually left
     * before anyone allocates for it, so a few corrupt bytes can't claim billions of items.
     */
    fun count(minBitsEach: Int): Int {
        require(minBitsEach > 0)
        val count = varint()
        val remaining = bytes.size * 8 - position
        if (count.toLong() * minBitsEach > remaining) {
            throw CodecException("Claims $count items but only $remaining bits remain")
        }
        return count
    }

    inline fun <reified E : Enum<E>> code(): E {
        val entries = enumEntries<E>()
        val ordinal = bits(bitsFor(entries.size))
        return entries.getOrNull(ordinal) ?: throw CodecException("No ${E::class.simpleName} with ordinal $ordinal")
    }

    inline fun <reified E : Enum<E>> optional(): E? = if (bool()) code<E>() else null

    fun resourceCounts(): ResourceCounts =
        Resource.entries.fold(ResourceCounts.EMPTY) { acc, r -> acc.with(r, bits(COUNT_WIDTH)) }

    fun devCardCounts(): DevCardCounts =
        DevelopmentCardType.entries.fold(DevCardCounts.EMPTY) { acc, t -> acc.with(t, bits(COUNT_WIDTH)) }

    fun header(expected: BlobKind) {
        val version = bits(8)
        if (version != FORMAT_VERSION) throw CodecException("Unsupported format version $version (expected $FORMAT_VERSION)")
        val ordinal = bits(8)
        val kind = BlobKind.entries.getOrNull(ordinal)
        if (kind != expected) throw CodecException("Expected a $expected blob but found ${kind ?: "unknown kind $ordinal"}")
    }

    /** Checks that only the zero padding of the last byte is left. */
    fun finish() {
        if (bytes.size != (position + 7) ushr 3) throw CodecException("Trailing data after the encoded value")
        if (position < bytes.size * 8 && bits(bytes.size * 8 - position) != 0) throw CodecException("Non-zero padding")
    }
}

/**
 * Runs a decoder, turning any validation failure inside it (from `require`, a lookup, or the engine's own checks)
 * into a [CodecException], so callers only have one failure type to handle.
 */
internal inline fun <T> decoding(what: String, bytes: ByteArray, block: BitReader.() -> T): T = try {
    BitReader(bytes).run { block().also { finish() } }
} catch (e: CodecException) {
    throw e
} catch (e: IllegalArgumentException) {
    throw CodecException("Invalid $what: ${e.message}", e)
} catch (e: IllegalStateException) {
    throw CodecException("Invalid $what: ${e.message}", e)
} catch (e: NoSuchElementException) {
    throw CodecException("Invalid $what: ${e.message}", e)
}
