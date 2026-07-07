package com.tgaetaprojects.biophilia.utils

sealed interface EnumSet<E : Enum<E>> : Set<E> {
    companion object {
        inline fun <reified E : Enum<E>> allOf(): EnumSet<E> = enumSetAllOf<E>()
        inline fun <reified E : Enum<E>> noneOf(): EnumSet<E> = enumSetNoneOf<E>()
        inline fun <reified E : Enum<E>> of(): EnumSet<E> = noneOf()
        inline fun <reified E : Enum<E>> of(e: E): EnumSet<E> = mutableEnumSetOf(e)
        inline fun <reified E : Enum<E>> of(e1: E, e2: E): EnumSet<E> = mutableEnumSetOf(e1, e2)
        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E): EnumSet<E> = mutableEnumSetOf(e1, e2, e3)
        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E, e4: E): EnumSet<E> = mutableEnumSetOf(e1, e2, e3, e4)
        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E, e4: E, e5: E): EnumSet<E> = mutableEnumSetOf(e1, e2, e3, e4, e5)
        inline fun <reified E : Enum<E>> of(first: E, vararg elements: E): EnumSet<E> = enumSetOf(first, *elements)
    }
}

interface MutableEnumSet<E : Enum<E>> : EnumSet<E>, MutableSet<E> {
    companion object {
        inline operator fun <reified E : Enum<E>> invoke() = mutableEnumSetOf<E>()
        inline fun <reified E : Enum<E>> allOf(): MutableEnumSet<E> = mutableEnumSetAllOf<E>()
        inline fun <reified E : Enum<E>> noneOf(): MutableEnumSet<E> = mutableEnumSetNoneOf<E>()
        inline fun <reified E : Enum<E>> of(): MutableEnumSet<E> = noneOf()
        inline fun <reified E : Enum<E>> of(e: E): MutableEnumSet<E> = mutableEnumSetOf(e)
        inline fun <reified E : Enum<E>> of(e1: E, e2: E): MutableEnumSet<E> = mutableEnumSetOf(e1, e2)
        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E): MutableEnumSet<E> = mutableEnumSetOf(e1, e2, e3)
        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E, e4: E): MutableEnumSet<E> = mutableEnumSetOf(e1, e2, e3, e4)
        inline fun <reified E : Enum<E>> of(e1: E, e2: E, e3: E, e4: E, e5: E): MutableEnumSet<E> = mutableEnumSetOf(e1, e2, e3, e4, e5)
        inline fun <reified E : Enum<E>> of(first: E, vararg elements: E): MutableEnumSet<E> = mutableEnumSetOf(first, *elements)
    }
}

// Immutable declarations
inline fun <reified E : Enum<E>> enumSetOf(): EnumSet<E> = mutableEnumSetOf()
inline fun <reified E : Enum<E>> enumSetOf(e: E): EnumSet<E> = mutableEnumSetOf(e)
inline fun <reified E : Enum<E>> enumSetOf(e1: E, e2: E): EnumSet<E> = mutableEnumSetOf(e1, e2)
inline fun <reified E : Enum<E>> enumSetOf(e1: E, e2: E, e3: E): EnumSet<E> = mutableEnumSetOf(e1, e2, e3)
inline fun <reified E : Enum<E>> enumSetOf(e1: E, e2: E, e3: E, e4: E): EnumSet<E> = mutableEnumSetOf(e1, e2, e3, e4)
inline fun <reified E : Enum<E>> enumSetOf(e1: E, e2: E, e3: E, e4: E, e5: E): EnumSet<E> = mutableEnumSetOf(e1, e2, e3, e4, e5)
inline fun <reified E : Enum<E>> enumSetOf(first: E, vararg elements: E): EnumSet<E> = mutableEnumSetOf(first, *elements)

inline fun <reified E : Enum<E>> enumSetAllOf(): EnumSet<E> = mutableEnumSetAllOf<E>()
inline fun <reified E : Enum<E>> enumSetNoneOf(): EnumSet<E> = mutableEnumSetNoneOf<E>()

inline fun <reified E : Enum<E>> enumSetOf(elements: Collection<E>): EnumSet<E> = mutableEnumSetOf(elements)
inline fun <reified E : Enum<E>> enumSetOf(elements: Array<out E>): EnumSet<E> = mutableEnumSetOf(elements)
inline fun <reified E : Enum<E>> enumSetOf(elements: EnumSet<E>): EnumSet<E> = mutableEnumSetOf(elements)

inline fun <reified E : Enum<E>> Iterable<E>.toEnumSet(): EnumSet<E> = toMutableEnumSet()
inline fun <reified E : Enum<E>> Sequence<E>.toEnumSet(): EnumSet<E> = toMutableEnumSet()
inline fun <reified E : Enum<E>> Collection<E>.toEnumSet(): EnumSet<E> = toMutableEnumSet()
inline fun <reified E : Enum<E>> Array<out E>.toEnumSet(): EnumSet<E> = toMutableEnumSet()
inline fun <reified E : Enum<E>> EnumSet<E>.toEnumSet(): EnumSet<E> = toMutableEnumSet()

// Mutable declarations
inline fun <reified E : Enum<E>> mutableEnumSetOf(): MutableEnumSet<E> = EnumSetImpl(E::class.java)
inline fun <reified E : Enum<E>> mutableEnumSetOf(e: E): MutableEnumSet<E> = EnumSetImpl(e)
inline fun <reified E : Enum<E>> mutableEnumSetOf(e1: E, e2: E): MutableEnumSet<E> = EnumSetImpl(e1, e2)
inline fun <reified E : Enum<E>> mutableEnumSetOf(e1: E, e2: E, e3: E): MutableEnumSet<E> = EnumSetImpl(e1, e2, e3)
inline fun <reified E : Enum<E>> mutableEnumSetOf(e1: E, e2: E, e3: E, e4: E): MutableEnumSet<E> = EnumSetImpl(e1, e2, e3, e4)
inline fun <reified E : Enum<E>> mutableEnumSetOf(e1: E, e2: E, e3: E, e4: E, e5: E): MutableEnumSet<E> = EnumSetImpl(e1, e2, e3, e4, e5)
inline fun <reified E : Enum<E>> mutableEnumSetOf(first: E, vararg elements: E): MutableEnumSet<E> = EnumSetImpl(first, *elements)

inline fun <reified E : Enum<E>> mutableEnumSetAllOf(): MutableEnumSet<E> = EnumSetImpl(java.util.EnumSet.allOf(E::class.java))
inline fun <reified E : Enum<E>> mutableEnumSetNoneOf(): MutableEnumSet<E> = EnumSetImpl(E::class.java)

inline fun <reified E : Enum<E>> mutableEnumSetOf(elements: Collection<E>): MutableEnumSet<E> = EnumSetImpl(elements)
inline fun <reified E : Enum<E>> mutableEnumSetOf(elements: Array<out E>): MutableEnumSet<E> = elements.toMutableEnumSet()
inline fun <reified E : Enum<E>> mutableEnumSetOf(elements: EnumSet<E>): MutableEnumSet<E> = EnumSetImpl(elements)

inline fun <reified E : Enum<E>> Iterable<E>.toMutableEnumSet(): MutableEnumSet<E> {
    return mutableEnumSetOf<E>().apply { addAll(this@toMutableEnumSet) }
}
inline fun <reified E : Enum<E>> Sequence<E>.toMutableEnumSet(): MutableEnumSet<E> {
    return mutableEnumSetOf<E>().apply { addAll(this@toMutableEnumSet) }
}
inline fun <reified E : Enum<E>> Collection<E>.toMutableEnumSet(): MutableEnumSet<E> = EnumSetImpl(this)
inline fun <reified E : Enum<E>> Array<out E>.toMutableEnumSet(): MutableEnumSet<E> {
    return mutableEnumSetOf<E>().apply { addAll(this@toMutableEnumSet) }
}
inline fun <reified E : Enum<E>> EnumSet<E>.toMutableEnumSet(): MutableEnumSet<E> = EnumSetImpl(this)
