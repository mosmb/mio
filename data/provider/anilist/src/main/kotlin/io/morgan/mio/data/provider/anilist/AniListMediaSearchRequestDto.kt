package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.Serializable

@Serializable
internal data class AniListMediaSearchRequestDto(
    val query: String,
    val variables: AniListMediaSearchVariablesDto,
)

@Serializable
internal data class AniListMediaSearchVariablesDto(
    val search: String? = null,
    val type: String,
    val page: Int,
    val perPage: Int,
    val sort: String? = null,
    val status: String? = null,
    val format: String? = null,
    val genre: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
)
