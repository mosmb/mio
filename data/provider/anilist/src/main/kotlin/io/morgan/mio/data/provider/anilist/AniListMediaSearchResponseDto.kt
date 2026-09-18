package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AniListMediaSearchResponseDto(
    @SerialName("data") val data: AniListMediaSearchDataDto? = null,
    @SerialName("errors") val errors: List<AniListGraphQlErrorDto?>? = null,
)

@Serializable
internal data class AniListMediaSearchDataDto(
    @SerialName("Page") val page: AniListMediaSearchPageDto? = null,
)

@Serializable
internal data class AniListMediaSearchPageDto(
    @SerialName("media") val media: List<AniListMediaDetailsNodeDto?>? = null,
    @SerialName("pageInfo") val pageInfo: AniListMediaSearchPageInfoDto? = null,
)

@Serializable
internal data class AniListMediaSearchPageInfoDto(
    @SerialName("hasNextPage") val hasNextPage: Boolean? = null,
)
