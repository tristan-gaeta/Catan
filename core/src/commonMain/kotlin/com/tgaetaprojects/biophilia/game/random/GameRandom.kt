package com.tgaetaprojects.biophilia.game.random

/**
 * The game's only source of randomness: xoshiro128++ (Blackman & Vigna), seeded through SplitMix64.
 *
 * Written in plain Kotlin with fixed, published algorithms so every platform and every Kotlin version produces the
 * same sequence for the same seed. That is what makes a game reproducible from its seeds and action log. The
 * standard library's `Random(seed)`, `shuffle` and `random()` make no such promise, so game code must use this
 * class and the helpers below instead.
 *
 * The whole state is four `Int`s (16 bytes), exposed by [state] so a snapshot can resume the exact sequence.
 * Changing anything here, or the order in which the engine draws numbers, changes every recorded game, so it
 * requires bumping `Game.RULES_VERSION`.
 */
class GameRandom private constructor(private var s0: Int, private var s1: Int, private var s2: Int, private var s3: Int) {

    /** Seeds the generator by expanding [seed] with SplitMix64, as the xoshiro authors recommend. */
    constructor(seed: Long) : this(0, 0, 0, 0) {
        val mix = SplitMix64(seed)
        val a = mix.next()
        val b = mix.next()
        s0 = a.toInt(); s1 = (a ushr 32).toInt(); s2 = b.toInt(); s3 = (b ushr 32).toInt()
        // the all-zero state is the one state xoshiro can't leave
        if (s0 or s1 or s2 or s3 == 0) s0 = 1
    }

    /** The next 32 random bits. */
    fun nextInt(): Int {
        val result = (s0 + s3).rotateLeft(7) + s0
        val t = s1 shl 9
        s2 = s2 xor s0
        s3 = s3 xor s1
        s1 = s1 xor s2
        s0 = s0 xor s3
        s2 = s2 xor t
        s3 = s3.rotateLeft(11)
        return result
    }

    /** A uniform integer in `0 until bound`, using Lemire's unbiased multiply-and-reject method. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        val b = bound.toLong()
        var m = (nextInt().toLong() and MASK_32) * b
        if ((m and MASK_32) < b) {
            val threshold = (TWO_POW_32 - b) % b
            while ((m and MASK_32) < threshold) m = (nextInt().toLong() and MASK_32) * b
        }
        return (m ushr 32).toInt()
    }

    /** The generator's complete state, for snapshots. */
    fun state(): IntArray = intArrayOf(s0, s1, s2, s3)

    /** An independent generator that continues exactly where this one is. */
    fun copy(): GameRandom = GameRandom(s0, s1, s2, s3)

    companion object {
        private const val MASK_32 = 0xFFFFFFFFL
        private const val TWO_POW_32 = 0x100000000L

        /** Resumes a generator from [state], as returned by [GameRandom.state]. */
        fun fromState(state: IntArray): GameRandom {
            require(state.size == 4) { "xoshiro128++ state is 4 ints, got ${state.size}" }
            require(state.any { it != 0 }) { "the all-zero state is invalid" }
            return GameRandom(state[0], state[1], state[2], state[3])
        }
    }
}

/**
 * SplitMix64 (Steele, Lea & Flood): turns one 64-bit seed into a stream of well-mixed 64-bit values. Used to seed
 * [GameRandom] and to derive several independent seeds from one.
 */
class SplitMix64(private var state: Long) {
    fun next(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    private companion object {
        val GOLDEN_GAMMA = 0x9E3779B97F4A7C15uL.toLong()
        val MIX_1 = 0xBF58476D1CE4E5B9uL.toLong()
        val MIX_2 = 0x94D049BB133111EBuL.toLong()
    }
}

/** Shuffles in place with Fisher–Yates, drawing from [random]; a fixed algorithm, unlike the standard library's. */
fun <T> MutableList<T>.shuffleWith(random: GameRandom) {
    for (i in lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val tmp = this[i]
        this[i] = this[j]
        this[j] = tmp
    }
}

/** A uniformly chosen element, drawing from [random]. */
fun <T> List<T>.pickWith(random: GameRandom): T {
    require(isNotEmpty()) { "Can't pick from an empty list" }
    return this[random.nextInt(size)]
}
