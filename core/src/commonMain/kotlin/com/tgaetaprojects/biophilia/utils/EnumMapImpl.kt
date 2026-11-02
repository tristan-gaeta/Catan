package com.tgaetaprojects.biophilia.utils

import java.util.EnumMap as JEnumMap

/**
 * The single implementation of [MutableEnumMap], a thin wrapper over [java.util.EnumMap] (one array slot per
 * constant, indexed by ordinal).
 *
 * Behavior inherited from the JDK class:
 * - iteration over [keys], [values] and [entries] follows declaration (ordinal) order;
 * - those views are live: removing through them, or calling `setValue` on an entry, writes through to the map;
 * - iterators are weakly consistent rather than fail-fast, so they never throw [ConcurrentModificationException];
 * - `null` values are allowed (keys are non-null by type), and [get] or [containsKey] with a key of another type
 *   return `null` or `false` instead of throwing.
 *
 * [equals], [hashCode] and [toString] are not covered by interface delegation, so they are forwarded explicitly
 * to honor the [Map] contract: an instance equals any [Map] with the same entries. Note that enum hash codes are
 * identity based, so [hashCode] differs between JVM runs and must never be persisted.
 *
 * Every constructor takes the key type's [Class]. The JDK's own copy constructor can't infer the type from an
 * empty map and throws.
 */
@PublishedApi
internal class EnumMapImpl<K : Enum<K>, V>(
    private val delegate: JEnumMap<K, V>
) : MutableEnumMap<K, V>, MutableMap<K, V> by delegate {
    constructor(keyType: Class<K>) : this(JEnumMap<K, V>(keyType))
    constructor(keyType: Class<K>, map: Map<out K, V>) : this(copyOf(keyType, map))

    override fun equals(other: Any?): Boolean =
        delegate == if (other is EnumMapImpl<*, *>) other.delegate else other

    override fun hashCode(): Int = delegate.hashCode()
    override fun toString(): String = delegate.toString()

    private companion object {
        @Suppress("UNCHECKED_CAST")
        fun <K : Enum<K>, V> copyOf(keyType: Class<K>, map: Map<out K, V>): JEnumMap<K, V> =
            if (map is EnumMapImpl<*, *>) JEnumMap((map as EnumMapImpl<K, V>).delegate)
            else JEnumMap<K, V>(keyType).apply { putAll(map) }
    }
}
