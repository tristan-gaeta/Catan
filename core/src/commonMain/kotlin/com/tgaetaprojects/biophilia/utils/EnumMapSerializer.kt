package com.tgaetaprojects.biophilia.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes an [EnumMap] in exactly the same wire format as a regular [Map], so any format kotlinx supports
 * (JSON, CBOR, ProtoBuf...) works, and the result is interchangeable with a `Map<K, V>` on the other side.
 *
 * Decoding needs the key's enum [Class] to build the backing [java.util.EnumMap]. It comes from the first
 * decoded key. An empty map has no key to inspect, so the class is looked up by the key descriptor's serial name.
 * That fallback only works when the name is the enum's JVM binary name: the default for top-level enums, but not
 * when the enum carries a custom `@SerialName` or is nested inside another class (`Outer$Inner`).
 */
class EnumMapSerializer<K : Enum<K>, V>(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>
) : KSerializer<EnumMap<K, V>> {
    private val mapSerializer = MapSerializer(keySerializer, valueSerializer)
    private val keySerialName = keySerializer.descriptor.serialName

    override val descriptor: SerialDescriptor get() = mapSerializer.descriptor

    override fun serialize(encoder: Encoder, value: EnumMap<K, V>) {
        mapSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): EnumMap<K, V> {
        val decoded = mapSerializer.deserialize(decoder)
        val keyType = decoded.keys.firstOrNull()?.declaringJavaClass ?: keyTypeFromSerialName()
        return EnumMapImpl(keyType, decoded)
    }

    @Suppress("UNCHECKED_CAST")
    private fun keyTypeFromSerialName(): Class<K> = try {
        Class.forName(keySerialName) as Class<K>
    } catch (e: ClassNotFoundException) {
        throw IllegalStateException(
            "Can't decode an empty EnumMap: no enum class named '$keySerialName'. See EnumMapSerializer.", e
        )
    }
}
