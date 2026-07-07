package com.tgaetaprojects.biophilia.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

class EnumMapSerializer<K : Enum<K>, V>(
    private val keySerializer: KSerializer<K>,
    private val valueSerializer: KSerializer<V>
) : KSerializer<EnumMap<K, V>> {

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = mapSerialDescriptor(
        keySerializer.descriptor, valueSerializer.descriptor
    )

    override fun serialize(encoder: Encoder, value: EnumMap<K, V>) {
        encoder.encodeStructure(descriptor) {
            var index = 0
            for ((k, v) in value) {
                encodeSerializableElement(descriptor, index++, keySerializer, k)
                encodeSerializableElement(descriptor, index++, valueSerializer, v)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): EnumMap<K, V> {
        // Resolve the class token cleanly using the serialization framework's internal type-name strings
        val enumClassName = keySerializer.descriptor.serialName
        val enumClass = Class.forName(enumClassName) as Class<K>

        val map: MutableEnumMap<K, V> = EnumMapImpl(enumClass)

        decoder.decodeStructure(descriptor) {
            while (true) {
                val index = decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break

                val key = decodeSerializableElement(descriptor, index, keySerializer)
                val nextIndex = decodeElementIndex(descriptor)
                val value = decodeSerializableElement(descriptor, nextIndex, valueSerializer)

                map[key] = value
            }
        }
        return map
    }
}