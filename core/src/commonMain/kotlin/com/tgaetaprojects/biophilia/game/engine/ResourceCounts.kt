package com.tgaetaprojects.biophilia.game.engine

import com.tgaetaprojects.biophilia.game.Resource
import com.tgaetaprojects.biophilia.game.Resource.BRICK
import com.tgaetaprojects.biophilia.game.Resource.GRAIN
import com.tgaetaprojects.biophilia.game.Resource.LUMBER
import com.tgaetaprojects.biophilia.game.Resource.ORE
import com.tgaetaprojects.biophilia.game.Resource.WOOL

/**
 * A count of each [Resource] packed into one `Int`, 5 bits per resource. No count can exceed 19 (the whole bank),
 * so 31 per slot is plenty. Used for hands, the bank, costs and trades.
 *
 * Kept as a non-null field or local, a value class never boxes, so a hand costs four bytes. It does box inside
 * generic collections and nullable types, so the engine avoids those on hot paths.
 *
 * Instances are immutable; arithmetic returns new values and throws if a count would leave `0..31`.
 */
@JvmInline
value class ResourceCounts private constructor(private val packedBits: Int) {
    operator fun get(resource: Resource): Int = (packedBits ushr (resource.ordinal * BITS)) and MASK

    /** The total number of cards. */
    val total: Int get() = Resource.entries.sumOf { get(it) }

    val isEmpty: Boolean get() = packedBits == 0

    /** The resource types with a non-zero count, in ordinal order. */
    val types: List<Resource> get() = Resource.entries.filter { get(it) > 0 }

    /** A copy with [resource] set to [count]. */
    fun with(resource: Resource, count: Int): ResourceCounts {
        require(count in 0..MASK) { "Count $count for $resource is out of range" }
        val shift = resource.ordinal * BITS
        return ResourceCounts((packedBits and (MASK shl shift).inv()) or (count shl shift))
    }

    operator fun plus(other: ResourceCounts): ResourceCounts =
        Resource.entries.fold(this) { acc, r -> acc.with(r, get(r) + other[r]) }

    operator fun plus(resource: Resource): ResourceCounts = with(resource, get(resource) + 1)

    operator fun minus(other: ResourceCounts): ResourceCounts {
        require(covers(other)) { "Can't take $other from $this" }
        return Resource.entries.fold(this) { acc, r -> acc.with(r, get(r) - other[r]) }
    }

    operator fun minus(resource: Resource): ResourceCounts = with(resource, get(resource) - 1)

    operator fun times(factor: Int): ResourceCounts =
        Resource.entries.fold(EMPTY) { acc, r -> acc.with(r, get(r) * factor) }

    /** Whether this has at least as many of every resource as [other], e.g. whether a hand can pay a cost. */
    fun covers(other: ResourceCounts): Boolean = Resource.entries.all { get(it) >= other[it] }

    /** Whether no resource type appears in both this and [other]. */
    fun isDisjointFrom(other: ResourceCounts): Boolean =
        Resource.entries.none { get(it) > 0 && other[it] > 0 }

    /** The [index]th card, counting through the cards in ordinal order; used to draw one at random. */
    fun cardAt(index: Int): Resource {
        require(index in 0 until total) { "Card $index out of range for $this" }
        var remaining = index
        for (r in Resource.entries) {
            if (remaining < get(r)) return r
            remaining -= get(r)
        }
        error("unreachable")
    }

    override fun toString(): String =
        Resource.entries.filter { get(it) > 0 }
            .joinToString(prefix = "{", postfix = "}") { "$it=${get(it)}" }

    companion object {
        private const val BITS = 5
        private const val MASK = (1 shl BITS) - 1

        val EMPTY = ResourceCounts(0)

        fun of(resource: Resource, count: Int): ResourceCounts = EMPTY.with(resource, count)

        fun of(vararg counts: Pair<Resource, Int>): ResourceCounts =
            counts.fold(EMPTY) { acc, (r, n) -> acc.with(r, acc[r] + n) }

        /** One card per listed resource; repeats add up. */
        fun of(vararg cards: Resource): ResourceCounts = cards.fold(EMPTY) { acc, r -> acc + r }

        /** The starting bank: 19 of each resource. */
        val FULL_BANK = Resource.entries.fold(EMPTY) { acc, r -> acc.with(r, 19) }

        val ROAD = of(BRICK, LUMBER)
        val SETTLEMENT = of(BRICK, LUMBER, WOOL, GRAIN)
        val CITY = of(ORE to 3, GRAIN to 2)
        val DEV_CARD = of(ORE, WOOL, GRAIN)
    }
}
