package com.tgaetaprojects.biophilia.utils

import java.util.EnumSet as JEnumSet

/**
 * The single implementation of [MutableEnumSet], a thin wrapper over [java.util.EnumSet] (one bit per constant,
 * stored in a `long`, or a `long[]` for enums with more than 64 constants).
 *
 * Behavior inherited from the JDK class:
 * - iteration follows declaration (ordinal) order, whatever the insertion order;
 * - iterators are weakly consistent rather than fail-fast, so they never throw [ConcurrentModificationException];
 * - [contains] and [remove] with an element of another type return `false` instead of throwing.
 *
 * [equals], [hashCode] and [toString] are not covered by interface delegation, so they are forwarded explicitly
 * to honor the [Set] contract: an instance equals any [Set] with the same elements. Note that enum hash codes are
 * identity based, so [hashCode] differs between JVM runs and must never be persisted.
 *
 * Every constructor takes the element type's [Class] or a non-empty set of elements. The JDK's own copy
 * constructors can't infer the type from an empty collection and throw.
 */
@PublishedApi
internal class EnumSetImpl<E : Enum<E>>(
    private val delegate: JEnumSet<E>
) : MutableEnumSet<E>, MutableSet<E> by delegate {
    constructor(elementType: Class<E>) : this(JEnumSet.noneOf(elementType))
    constructor(elementType: Class<E>, elements: Iterable<E>) : this(copyOf(elementType, elements))

    constructor(e1: E) : this(JEnumSet.of(e1))
    constructor(e1: E, e2: E) : this(JEnumSet.of(e1, e2))
    constructor(e1: E, e2: E, e3: E) : this(JEnumSet.of(e1, e2, e3))
    constructor(e1: E, e2: E, e3: E, e4: E) : this(JEnumSet.of(e1, e2, e3, e4))
    constructor(e1: E, e2: E, e3: E, e4: E, e5: E) : this(JEnumSet.of(e1, e2, e3, e4, e5))
    constructor(first: E, vararg elements: E) : this(JEnumSet.of(first, *elements))

    override fun equals(other: Any?): Boolean =
        delegate == if (other is EnumSetImpl<*>) other.delegate else other
    override fun hashCode(): Int = delegate.hashCode()
    override fun toString(): String = delegate.toString()

    private companion object {
        @Suppress("UNCHECKED_CAST")
        fun <E : Enum<E>> copyOf(elementType: Class<E>, elements: Iterable<E>): JEnumSet<E> =
            if (elements is EnumSetImpl<*>) (elements as EnumSetImpl<E>).delegate.clone()
            else JEnumSet.noneOf(elementType).apply { addAll(elements) }
    }
}
