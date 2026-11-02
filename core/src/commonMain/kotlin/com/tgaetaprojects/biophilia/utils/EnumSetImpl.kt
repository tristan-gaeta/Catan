package com.tgaetaprojects.biophilia.utils

import java.util.EnumSet as JEnumSet

class EnumSetImpl<E : Enum<E>>(
    private val delegate: JEnumSet<E>
) : MutableEnumSet<E>, MutableSet<E> by delegate {
    // Delegation doesn't cover these; forward them so equality follows the Set contract
    override fun equals(other: Any?): Boolean =
        delegate == if (other is EnumSetImpl<*>) other.delegate else other
    override fun hashCode(): Int = delegate.hashCode()
    override fun toString(): String = delegate.toString()

    constructor(clazz: Class<E>) : this(JEnumSet.noneOf<E>(clazz))
    constructor(elements: Collection<E>) : this(JEnumSet.copyOf(elements))
    constructor(elements: EnumSet<E>) : this(JEnumSet.copyOf(elements))

    constructor(e1: E) : this(JEnumSet.of(e1))
    constructor(e1: E, e2: E) : this(JEnumSet.of(e1, e2))
    constructor(e1: E, e2: E, e3: E) : this(JEnumSet.of(e1, e2, e3))
    constructor(e1: E, e2: E, e3: E, e4: E) : this(JEnumSet.of(e1, e2, e3, e4))
    constructor(e1: E, e2: E, e3: E, e4: E, e5: E) : this(JEnumSet.of(e1, e2, e3, e4, e5))
    constructor(first: E, vararg elements: E) : this(JEnumSet.of<E>(first, *elements))

    companion object {
        inline fun <reified E : Enum<E>> allOf(): EnumSetImpl<E> {
            return EnumSetImpl(JEnumSet.allOf(E::class.java))
        }
    }
}