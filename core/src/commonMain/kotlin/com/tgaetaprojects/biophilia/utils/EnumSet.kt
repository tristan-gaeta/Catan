package com.tgaetaprojects.biophilia.utils

/**
 * A read-only [Set] of enum constants, backed by [java.util.EnumSet] (a bit field).
 *
 * Read-only is not immutable, in the same way as [setOf]: an instance may be a view of a [MutableEnumSet] that
 * someone else still mutates. Take a snapshot with [toEnumSet] when that matters.
 *
 * Instances follow the [Set] contract and compare equal to any set with the same elements. Iteration is in
 * declaration (ordinal) order. See [EnumSetImpl] for the full set of guarantees.
 */
sealed interface EnumSet<E : Enum<E>> : Set<E> {
    /** Factories mirroring [java.util.EnumSet]'s static methods. */
    companion object {
        inline fun <reified E : Enum<E>> allOf(): EnumSet<E> = enumSetAllOf<E>()
        inline fun <reified E : Enum<E>> noneOf(): EnumSet<E> = enumSetNoneOf<E>()
        inline fun <reified E : Enum<E>> of(): EnumSet<E> = noneOf()
        inline fun <reified E : Enum<E>> of(e: E): EnumSet<E> = mutableEnumSetOf(e)
        inline fun <reified E : Enum<E>> of(e1: E, e2: E): EnumSet<E> = mutableEnumSetOf(e1, e2)
        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E): EnumSet<E> =
            mutableEnumSetOf(e1, e2, e3)

        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E, e4: E): EnumSet<E> =
            mutableEnumSetOf(e1, e2, e3, e4)

        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E, e4: E, e5: E): EnumSet<E> =
            mutableEnumSetOf(e1, e2, e3, e4, e5)

        inline fun <reified E : Enum<E>> of(first: E, vararg elements: E): EnumSet<E> =
            enumSetOf(first, *elements)
    }
}

/** A mutable [EnumSet]. */
sealed interface MutableEnumSet<E : Enum<E>> : EnumSet<E>, MutableSet<E> {
    /** Factories mirroring [java.util.EnumSet]'s static methods; `MutableEnumSet<E>()` is an empty set. */
    companion object {
        inline operator fun <reified E : Enum<E>> invoke() = mutableEnumSetOf<E>()
        inline fun <reified E : Enum<E>> allOf(): MutableEnumSet<E> = mutableEnumSetAllOf<E>()
        inline fun <reified E : Enum<E>> noneOf(): MutableEnumSet<E> = mutableEnumSetNoneOf<E>()
        inline fun <reified E : Enum<E>> of(): MutableEnumSet<E> = noneOf()
        inline fun <reified E : Enum<E>> of(e: E): MutableEnumSet<E> = mutableEnumSetOf(e)
        inline fun <reified E : Enum<E>> of(e1: E, e2: E): MutableEnumSet<E> =
            mutableEnumSetOf(e1, e2)

        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E): MutableEnumSet<E> =
            mutableEnumSetOf(e1, e2, e3)

        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E, e4: E): MutableEnumSet<E> =
            mutableEnumSetOf(e1, e2, e3, e4)

        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E, e4: E, e5: E): MutableEnumSet<E> =
            mutableEnumSetOf(e1, e2, e3, e4, e5)

        inline fun <reified E : Enum<E>> of(first: E, vararg elements: E): MutableEnumSet<E> =
            mutableEnumSetOf(first, *elements)
    }
}

// Read-only builders. Each returns a new instance; sources are copied, never wrapped.

/** A read-only set of the given elements. The fixed-arity overloads avoid allocating a vararg array. */
inline fun <reified E : Enum<E>> enumSetOf(): EnumSet<E> = mutableEnumSetOf()
inline fun <reified E : Enum<E>> enumSetOf(e: E): EnumSet<E> = mutableEnumSetOf(e)
inline fun <reified E : Enum<E>> enumSetOf(e1: E, e2: E): EnumSet<E> = mutableEnumSetOf(e1, e2)
inline fun <reified E : Enum<E>> enumSetOf(e1: E, e2: E, e3: E): EnumSet<E> =
    mutableEnumSetOf(e1, e2, e3)

inline fun <reified E : Enum<E>> enumSetOf(e1: E, e2: E, e3: E, e4: E): EnumSet<E> =
    mutableEnumSetOf(e1, e2, e3, e4)

inline fun <reified E : Enum<E>> enumSetOf(e1: E, e2: E, e3: E, e4: E, e5: E): EnumSet<E> =
    mutableEnumSetOf(e1, e2, e3, e4, e5)

inline fun <reified E : Enum<E>> enumSetOf(first: E, vararg elements: E): EnumSet<E> =
    mutableEnumSetOf(first, *elements)

/** A read-only set of every constant of [E]. */
inline fun <reified E : Enum<E>> enumSetAllOf(): EnumSet<E> = mutableEnumSetAllOf<E>()

/** An empty read-only set. */
inline fun <reified E : Enum<E>> enumSetNoneOf(): EnumSet<E> = mutableEnumSetNoneOf<E>()

/** A read-only snapshot of [elements]; it may be empty. */
inline fun <reified E : Enum<E>> enumSetOf(elements: Iterable<E>): EnumSet<E> =
    mutableEnumSetOf(elements)

/** A read-only snapshot of [elements]; it may be empty. */
inline fun <reified E : Enum<E>> enumSetOf(elements: Array<out E>): EnumSet<E> =
    mutableEnumSetOf(elements)

/** A read-only snapshot of these elements; the source may be empty. */
inline fun <reified E : Enum<E>> Iterable<E>.toEnumSet(): EnumSet<E> = toMutableEnumSet()

/** A read-only snapshot of these elements; the source may be empty. */
inline fun <reified E : Enum<E>> Sequence<E>.toEnumSet(): EnumSet<E> = toMutableEnumSet()

/** A read-only snapshot of these elements; the source may be empty. */
inline fun <reified E : Enum<E>> Array<out E>.toEnumSet(): EnumSet<E> = toMutableEnumSet()

// Mutable builders. Each returns a new instance; sources are copied, never wrapped.

/** A mutable set of the given elements. The fixed-arity overloads avoid allocating a vararg array. */
inline fun <reified E : Enum<E>> mutableEnumSetOf(): MutableEnumSet<E> = EnumSetImpl(E::class.java)
inline fun <reified E : Enum<E>> mutableEnumSetOf(e: E): MutableEnumSet<E> = EnumSetImpl(e)
inline fun <reified E : Enum<E>> mutableEnumSetOf(e1: E, e2: E): MutableEnumSet<E> =
    EnumSetImpl(e1, e2)

inline fun <reified E : Enum<E>> mutableEnumSetOf(e1: E, e2: E, e3: E): MutableEnumSet<E> =
    EnumSetImpl(e1, e2, e3)

inline fun <reified E : Enum<E>> mutableEnumSetOf(e1: E, e2: E, e3: E, e4: E): MutableEnumSet<E> =
    EnumSetImpl(e1, e2, e3, e4)

inline fun <reified E : Enum<E>> mutableEnumSetOf(
    e1: E,
    e2: E,
    e3: E,
    e4: E,
    e5: E
): MutableEnumSet<E> = EnumSetImpl(e1, e2, e3, e4, e5)

inline fun <reified E : Enum<E>> mutableEnumSetOf(first: E, vararg elements: E): MutableEnumSet<E> =
    EnumSetImpl(first, *elements)

/** A mutable set of every constant of [E]. */
inline fun <reified E : Enum<E>> mutableEnumSetAllOf(): MutableEnumSet<E> =
    EnumSetImpl(java.util.EnumSet.allOf(E::class.java))

/** An empty mutable set. */
inline fun <reified E : Enum<E>> mutableEnumSetNoneOf(): MutableEnumSet<E> =
    EnumSetImpl(E::class.java)

/** A mutable copy of [elements]; it may be empty. */
inline fun <reified E : Enum<E>> mutableEnumSetOf(elements: Iterable<E>): MutableEnumSet<E> =
    elements.toMutableEnumSet()

/** A mutable copy of [elements]; it may be empty. */
inline fun <reified E : Enum<E>> mutableEnumSetOf(elements: Array<out E>): MutableEnumSet<E> =
    elements.toMutableEnumSet()

/** A mutable copy of these elements; the source may be empty. */
inline fun <reified E : Enum<E>> Iterable<E>.toMutableEnumSet(): MutableEnumSet<E> =
    EnumSetImpl(E::class.java, this)

/** A mutable copy of these elements; the source may be empty. */
inline fun <reified E : Enum<E>> Sequence<E>.toMutableEnumSet(): MutableEnumSet<E> =
    EnumSetImpl(E::class.java, asIterable())

/** A mutable copy of these elements; the source may be empty. */
inline fun <reified E : Enum<E>> Array<out E>.toMutableEnumSet(): MutableEnumSet<E> =
    EnumSetImpl(E::class.java, asIterable())
