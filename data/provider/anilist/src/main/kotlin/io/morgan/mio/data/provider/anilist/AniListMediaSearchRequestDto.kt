package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.Serializable

@Serializable
internal data class AniListMediaSearchRequestDto(
    val query: String,
    val variables: AniListMediaSearchVariablesDto,
)

@Serializable
internal data class AniListMediaSearchVariablesDto(
    val search: String,
    val type: String,
    val page: Int,
    val perPage: Int,
)
