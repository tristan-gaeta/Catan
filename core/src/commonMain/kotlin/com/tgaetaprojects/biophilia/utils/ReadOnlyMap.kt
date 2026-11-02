package com.tgaetaprojects.biophilia.utils

/**
 * A read-only view of [delegate] that can't be cast back to a mutable map. On the JVM, Kotlin implements the
 * mutating `java.util.Map` methods of a read-only `Map` class by throwing `UnsupportedOperationException`, so even a
 * cast to `MutableMap` can't change the data underneath.
 *
 * Equality, hash code and string form follow the [Map] contract, so a view equals any map with the same entries.
 */
internal class ReadOnlyMap<K, V>(private val delegate: Map<K, V>) : Map<K, V> by delegate {
    override fun equals(other: Any?): Boolean = delegate == if (other is ReadOnlyMap<*, *>) other.delegate else other
    override fun hashCode(): Int = delegate.hashCode()
    override fun toString(): String = delegate.toString()
}

/**
 * A view of this map that callers can't mutate, for maps the engine hands out (phases, events, layouts). It's a
 * view, not a copy: wrap only maps nothing else will change afterwards.
 */
internal fun <K, V> Map<K, V>.asReadOnly(): Map<K, V> = this as? ReadOnlyMap<K, V> ?: ReadOnlyMap(this)
