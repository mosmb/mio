package io.morgan.mio.feature.media.api

import io.morgan.mio.core.navigation.FocusedDestination
import io.morgan.mio.domain.model.MediaId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
public data class MediaKey(
    @Serializable(with = MediaIdSerializer::class)
    public val mediaId: MediaId,
) : FocusedDestination

private object MediaIdSerializer : KSerializer<MediaId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MediaId", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: MediaId,
    ) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): MediaId = MediaId(decoder.decodeString())
}
