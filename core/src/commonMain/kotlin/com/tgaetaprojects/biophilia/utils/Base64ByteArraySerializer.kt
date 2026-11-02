package com.tgaetaprojects.biophilia.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64

/**
 * Encodes a [ByteArray] as one standard Base64 string instead of the default list of numbers. This keeps compact
 * binary snapshots (e.g. one byte per board slot) small in text formats such as JSON.
 *
 * Apply it per property with `@Serializable(with = Base64ByteArraySerializer::class)`.
 */
object Base64ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.tgaetaprojects.biophilia.utils.Base64ByteArray", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.encode(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        return Base64.decode(decoder.decodeString())
    }
}
