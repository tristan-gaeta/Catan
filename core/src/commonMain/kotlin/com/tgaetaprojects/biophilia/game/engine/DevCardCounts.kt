package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.DevelopmentCardType

/**
 * A count of each [DevelopmentCardType] packed into one `Int`, 5 bits per type (at most 14 knights exist).
 * The same idea as [ResourceCounts]; immutable, and arithmetic throws if a count would leave `0..31`.
 */
@JvmInline
value class DevCardCounts private constructor(internal val packedBits: Int) {
    operator fun get(type: DevelopmentCardType): Int = (packedBits ushr (type.ordinal * BITS)) and MASK

    val total: Int get() = DevelopmentCardType.entries.sumOf { get(it) }

    val isEmpty: Boolean get() = packedBits == 0

    fun with(type: DevelopmentCardType, count: Int): DevCardCounts {
        require(count in 0..MASK) { "Count $count for $type is out of range" }
        val shift = type.ordinal * BITS
        return DevCardCounts((packedBits and (MASK shl shift).inv()) or (count shl shift))
    }

    operator fun plus(type: DevelopmentCardType): DevCardCounts = with(type, get(type) + 1)

    operator fun minus(type: DevelopmentCardType): DevCardCounts = with(type, get(type) - 1)

    override fun toString(): String =
        DevelopmentCardType.entries.filter { get(it) > 0 }.joinToString(prefix = "{", postfix = "}") { "$it=${get(it)}" }

    companion object {
        private const val BITS = 5
        private const val MASK = (1 shl BITS) - 1

        val EMPTY = DevCardCounts(0)
    }
}
