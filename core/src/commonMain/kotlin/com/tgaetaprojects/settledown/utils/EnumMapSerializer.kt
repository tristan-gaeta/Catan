package com.tgaetaprojects.settledown.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Serializes an [EnumMap] in exactly the same wire format as a regular [Map], so any format kotlinx supports
 * (JSON, CBOR, ProtoBuf...) works, and the result is interchangeable with a `Map<K, V>` on the other side.
 *
 * Decoding needs the key's enum [Class] to build the backing [java.util.EnumMap]. It comes from the first
 * decoded key. An empty map has no key to inspect, so the key serializer is asked to decode the enum's first
 * constant from a stand-in decoder. That needs no class names, so it survives R8 renaming. Only a custom key
 * serializer that doesn't decode through `decodeEnum` falls back to looking the class up by the key descriptor's
 * serial name, which works only when that name is the enum's JVM binary name and the class isn't renamed.
 */
class EnumMapSerializer<K : Enum<K>, V>(
    private val keySerializer: KSerializer<K>,
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
        val keyType = decoded.keys.firstOrNull()?.declaringJavaClass ?: keyTypeOfEmptyMap()
        return EnumMapImpl(keyType, decoded)
    }

    private fun keyTypeOfEmptyMap(): Class<K> = try {
        keySerializer.deserialize(FirstConstantDecoder()).declaringJavaClass
    } catch (_: SerializationException) {
        keyTypeFromSerialName()
    }

    @Suppress("UNCHECKED_CAST")
    private fun keyTypeFromSerialName(): Class<K> = try {
        Class.forName(keySerialName) as Class<K>
    } catch (e: ClassNotFoundException) {
        throw IllegalStateException(
            "Can't decode an empty EnumMap: no enum class named '$keySerialName'. See EnumMapSerializer.",
            e
        )
    }
}

/** Answers any enum with its first constant and refuses everything else; used to find an empty map's key type. */
@OptIn(ExperimentalSerializationApi::class)
private class FirstConstantDecoder : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = CompositeDecoder.DECODE_DONE

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = 0
}
