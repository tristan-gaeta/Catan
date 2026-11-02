package com.tgaetaprojects.biophilia.utils


import kotlinx.serialization.Serializable
import java.util.EnumMap as JEnumMap

@Serializable(with = EnumMapSerializer::class)
class EnumMapImpl<K : Enum<K>, V>(
    private val delegate: JEnumMap<K, V>
) : MutableEnumMap<K, V>, MutableMap<K, V> by delegate {
    constructor(keyType: Class<K>) : this(JEnumMap<K, V>(keyType))
    constructor(map: Map<K, V>) : this(JEnumMap<K, V>(map))
    constructor(map: EnumMap<K, V>) : this(JEnumMap<K, V>((map as EnumMapImpl<K, V>).delegate))

    // Delegation doesn't cover these; forward them so equality follows the Map contract
    override fun equals(other: Any?): Boolean =
        delegate == if (other is EnumMapImpl<*, *>) other.delegate else other
    override fun hashCode(): Int = delegate.hashCode()
    override fun toString(): String = delegate.toString()
}