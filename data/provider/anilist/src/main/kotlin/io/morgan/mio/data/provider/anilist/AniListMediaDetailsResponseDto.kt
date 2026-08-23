package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AniListMediaDetailsResponseDto(
    @SerialName("data") val data: AniListMediaDetailsDataDto? = null,
    @SerialName("errors") val errors: List<AniListGraphQlErrorDto?>? = null,
)

@Serializable
internal data class AniListMediaDetailsDataDto(
    @SerialName("Media") val media: AniListMediaDetailsNodeDto? = null,
)

@Serializable
internal data class AniListMediaDetailsNodeDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("format") val format: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("episodes") val episodes: Int? = null,
    @SerialName("chapters") val chapters: Int? = null,
    @SerialName("volumes") val volumes: Int? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("bannerImage") val bannerImage: String? = null,
    @SerialName("genres") val genres: List<String?>? = null,
    @SerialName("seasonYear") val seasonYear: Int? = null,
    @SerialName("averageScore") val averageScore: Int? = null,
    @SerialName("title") val title: AniListMediaTitleDto? = null,
    @SerialName("coverImage") val coverImage: AniListCoverImageDto? = null,
    @SerialName("airingSchedule") val airingSchedule: AniListAiringScheduleConnectionDto? = null,
    @SerialName("relations") val relations: AniListRelationsConnectionDto? = null,
)

@Serializable
internal data class AniListRelationsConnectionDto(
    @SerialName("nodes") val nodes: List<AniListMediaDetailsNodeDto?>? = null,
)
