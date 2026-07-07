package com.tgaetaprojects.biophilia.utils

/**
 * A read only version of java's EnumMap
 */
sealed interface EnumMap<K : Enum<K>, V> : Map<K, V> {
    companion object {
        inline operator fun <reified K : Enum<K>, V> invoke(): EnumMap<K, V> =
            EnumMapImpl(K::class.java)

        inline operator fun <reified K : Enum<K>, V> invoke(map: Map<K, V>): EnumMap<K, V> =
            EnumMapImpl(map)

        inline operator fun <reified K : Enum<K>, V> invoke(enumMap: EnumMap<K, V>): EnumMap<K, V> =
            EnumMapImpl(enumMap)
    }
}


/**
 * A read/write version of java's EnumMap
 */
sealed interface MutableEnumMap<K : Enum<K>, V> : EnumMap<K, V>, MutableMap<K, V> {
    companion object {
        inline operator fun <reified K : Enum<K>, V> invoke(): MutableEnumMap<K, V> =
            EnumMapImpl(K::class.java)

        inline operator fun <reified K : Enum<K>, V> invoke(map: Map<K, V>): MutableEnumMap<K, V> =
            EnumMapImpl(map)

        inline operator fun <reified K : Enum<K>, V> invoke(enumMap: EnumMap<K, V>): MutableEnumMap<K, V> =
            EnumMapImpl(enumMap)
    }
}

// Immutable impl
inline fun <reified K : Enum<K>, V> enumMapOf(): EnumMap<K, V> = mutableEnumMapOf()
inline fun <reified K : Enum<K>, V> enumMapOf(vararg pairs: Pair<K, V>): EnumMap<K, V> {
    return pairs.toMutableEnumMap()
}
inline fun <reified K : Enum<K>, V> enumMapOf(enumMap: EnumMap<K, V>): EnumMap<K, V> {
    return enumMap.toMutableEnumMap()
}
inline fun <reified K : Enum<K>, V> Map<K, V>.toEnumMap(): EnumMap<K, V> {
    return toMutableEnumMap()
}
inline fun <reified K : Enum<K>, V> Iterable<Pair<K, V>>.toEnumMap(): EnumMap<K, V> {
    return toMutableEnumMap()
}
inline fun <reified K : Enum<K>, V> Sequence<Pair<K, V>>.toEnumMap(): EnumMap<K, V> {
    return toMutableEnumMap()
}
inline fun <reified K : Enum<K>, V> Array<out Pair<K, V>>.toEnumMap(): EnumMap<K, V> {
    return toMutableEnumMap()
}
inline fun <reified K : Enum<K>, V> buildEnumMap(builderAction: MutableEnumMap<K, V>.() -> Unit): EnumMap<K, V> {
    return buildMutableEnumMap(builderAction)
}

// Mutable impl
inline fun <reified K : Enum<K>, V> mutableEnumMapOf(): MutableEnumMap<K, V> = EnumMapImpl(K::class.java)
inline fun <reified K : Enum<K>, V> mutableEnumMapOf(vararg pairs: Pair<K, V>): MutableEnumMap<K, V> {
    return EnumMapImpl<K, V>(K::class.java).also { it.putAll(pairs) }
}
inline fun <reified K : Enum<K>, V> mutableEnumMapOf(map: Map<K, V>): MutableEnumMap<K, V> {
    return map.toMutableEnumMap()
}
inline fun <reified K : Enum<K>, V> mutableEnumMapOf(enumMap: EnumMap<K, V>): MutableEnumMap<K, V> {
    return enumMap.toMutableEnumMap()
}
inline fun <reified K : Enum<K>, V> mutableEnumMapOf(mutableEnumMap: MutableEnumMap<K, V>): MutableEnumMap<K, V> {
    return mutableEnumMap.toMutableEnumMap()
}
inline fun <reified K : Enum<K>, V> Map<K, V>.toMutableEnumMap(): MutableEnumMap<K, V> = EnumMapImpl(this)
inline fun <reified K : Enum<K>, V> EnumMap<K, V>.toMutableEnumMap(): MutableEnumMap<K, V> = EnumMapImpl(this)
inline fun <reified K : Enum<K>, V> MutableEnumMap<K, V>.toMutableEnumMap(): MutableEnumMap<K, V> = EnumMapImpl(this)

inline fun <reified K : Enum<K>, V> Iterable<Pair<K, V>>.toMutableEnumMap(): MutableEnumMap<K, V> {
    return EnumMapImpl<K, V>(K::class.java).also { it.putAll(this) }
}
inline fun <reified K : Enum<K>, V> Sequence<Pair<K, V>>.toMutableEnumMap(): MutableEnumMap<K, V> {
    return EnumMapImpl<K, V>(K::class.java).also { it.putAll(this) }
}
inline fun <reified K : Enum<K>, V> Array<out Pair<K, V>>.toMutableEnumMap(): MutableEnumMap<K, V> {
    return EnumMapImpl<K, V>(K::class.java).also { it.putAll(this) }
}
inline fun <reified K : Enum<K>, V> buildMutableEnumMap(builderAction: MutableEnumMap<K, V>.() -> Unit): MutableEnumMap<K, V> {
    return EnumMapImpl<K, V>(K::class.java).apply(builderAction)
}

