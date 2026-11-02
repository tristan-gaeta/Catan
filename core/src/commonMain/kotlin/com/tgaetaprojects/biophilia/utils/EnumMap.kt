package com.tgaetaprojects.biophilia.utils

import kotlinx.serialization.Serializable

/**
 * A read-only [Map] keyed by an enum, backed by [java.util.EnumMap].
 *
 * Read-only is not immutable, in the same way as [mapOf]: an instance may be a view of a [MutableEnumMap] that
 * someone else still mutates. Take a snapshot with [toEnumMap] when that matters.
 *
 * Instances follow the [Map] contract and compare equal to any map with the same entries. Iteration is in
 * declaration (ordinal) order. See [EnumMapImpl] for the full set of guarantees.
 */
@Serializable(with = EnumMapSerializer::class)
sealed interface EnumMap<K : Enum<K>, V> : Map<K, V> {
    companion object {
        /** An empty map. */
        inline operator fun <reified K : Enum<K>, V> invoke(): EnumMap<K, V> =
            EnumMapImpl(K::class.java)

        /** A copy of [map]; it may be empty. */
        inline operator fun <reified K : Enum<K>, V> invoke(map: Map<out K, V>): EnumMap<K, V> =
            EnumMapImpl(K::class.java, map)
    }
}

/**
 * A mutable [EnumMap]. Its [keys], [values] and [entries] are live views, as the [MutableMap] contract requires.
 */
sealed interface MutableEnumMap<K : Enum<K>, V> : EnumMap<K, V>, MutableMap<K, V> {
    companion object {
        /** An empty map. */
        inline operator fun <reified K : Enum<K>, V> invoke(): MutableEnumMap<K, V> =
            EnumMapImpl(K::class.java)

        /** A copy of [map]; it may be empty. */
        inline operator fun <reified K : Enum<K>, V> invoke(map: Map<out K, V>): MutableEnumMap<K, V> =
            EnumMapImpl(K::class.java, map)
    }
}

// Read-only builders. Each returns a new instance; sources are copied, never wrapped.

/** An empty read-only map. */
inline fun <reified K : Enum<K>, V> enumMapOf(): EnumMap<K, V> = mutableEnumMapOf()

/** A read-only map of [pairs]; later pairs win on duplicate keys, as with [mapOf]. */
inline fun <reified K : Enum<K>, V> enumMapOf(vararg pairs: Pair<K, V>): EnumMap<K, V> = pairs.toMutableEnumMap()

/** A read-only snapshot of [map]. */
inline fun <reified K : Enum<K>, V> enumMapOf(map: Map<out K, V>): EnumMap<K, V> = map.toMutableEnumMap()

/** A read-only snapshot of this map. */
inline fun <reified K : Enum<K>, V> Map<out K, V>.toEnumMap(): EnumMap<K, V> = toMutableEnumMap()
/** A read-only map of these pairs; later pairs win on duplicate keys. */
inline fun <reified K : Enum<K>, V> Iterable<Pair<K, V>>.toEnumMap(): EnumMap<K, V> = toMutableEnumMap()
/** A read-only map of these pairs; later pairs win on duplicate keys. */
inline fun <reified K : Enum<K>, V> Sequence<Pair<K, V>>.toEnumMap(): EnumMap<K, V> = toMutableEnumMap()
/** A read-only map of these pairs; later pairs win on duplicate keys. */
inline fun <reified K : Enum<K>, V> Array<out Pair<K, V>>.toEnumMap(): EnumMap<K, V> = toMutableEnumMap()

/** Builds a read-only map by running [builderAction] on a new mutable one, like [buildMap]. */
inline fun <reified K : Enum<K>, V> buildEnumMap(builderAction: MutableEnumMap<K, V>.() -> Unit): EnumMap<K, V> =
    buildMutableEnumMap(builderAction)

// Mutable builders. Each returns a new instance; sources are copied, never wrapped.

/** An empty mutable map. */
inline fun <reified K : Enum<K>, V> mutableEnumMapOf(): MutableEnumMap<K, V> = EnumMapImpl(K::class.java)

/** A mutable map of [pairs]; later pairs win on duplicate keys, as with [mutableMapOf]. */
inline fun <reified K : Enum<K>, V> mutableEnumMapOf(vararg pairs: Pair<K, V>): MutableEnumMap<K, V> =
    pairs.toMutableEnumMap()

/** A mutable copy of [map]. */
inline fun <reified K : Enum<K>, V> mutableEnumMapOf(map: Map<out K, V>): MutableEnumMap<K, V> =
    map.toMutableEnumMap()

/** A mutable copy of this map; it may be empty. */
inline fun <reified K : Enum<K>, V> Map<out K, V>.toMutableEnumMap(): MutableEnumMap<K, V> =
    EnumMapImpl(K::class.java, this)

/** A mutable map of these pairs; later pairs win on duplicate keys. */
inline fun <reified K : Enum<K>, V> Iterable<Pair<K, V>>.toMutableEnumMap(): MutableEnumMap<K, V> =
    EnumMapImpl<K, V>(K::class.java).also { it.putAll(this) }
/** A mutable map of these pairs; later pairs win on duplicate keys. */
inline fun <reified K : Enum<K>, V> Sequence<Pair<K, V>>.toMutableEnumMap(): MutableEnumMap<K, V> =
    EnumMapImpl<K, V>(K::class.java).also { it.putAll(this) }
/** A mutable map of these pairs; later pairs win on duplicate keys. */
inline fun <reified K : Enum<K>, V> Array<out Pair<K, V>>.toMutableEnumMap(): MutableEnumMap<K, V> =
    EnumMapImpl<K, V>(K::class.java).also { it.putAll(this) }

/** Builds a mutable map by running [builderAction] on a new one. */
inline fun <reified K : Enum<K>, V> buildMutableEnumMap(builderAction: MutableEnumMap<K, V>.() -> Unit): MutableEnumMap<K, V> =
    EnumMapImpl<K, V>(K::class.java).apply(builderAction)
