package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AniListGraphQlErrorDto(
    @SerialName("status") val status: Int? = null,
)
