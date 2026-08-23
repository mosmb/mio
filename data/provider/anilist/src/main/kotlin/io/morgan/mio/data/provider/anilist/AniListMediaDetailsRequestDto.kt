package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.Serializable

@Serializable
internal data class AniListMediaDetailsRequestDto(
    val query: String,
    val variables: AniListMediaDetailsVariablesDto,
)

@Serializable
internal data class AniListMediaDetailsVariablesDto(
    val id: Int,
)
