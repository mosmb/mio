package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AniListMediaListRequestDto(
    @SerialName("query") val query: String,
    @SerialName("variables") val variables: AniListMediaListVariablesDto,
)

@Serializable
internal data class AniListMediaListVariablesDto(
    @SerialName("userId") val userId: Int,
    @SerialName("type") val type: String,
    @SerialName("page") val page: Int,
    @SerialName("perPage") val perPage: Int,
)
