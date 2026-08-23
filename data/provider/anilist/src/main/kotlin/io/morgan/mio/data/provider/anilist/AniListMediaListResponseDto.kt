package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AniListMediaListResponseDto(
    @SerialName("data") val data: AniListMediaListDataDto?,
    @SerialName("errors") val errors: List<AniListGraphQlErrorDto?>?,
)

@Serializable
internal data class AniListMediaListDataDto(
    @SerialName("Page") val page: AniListPageDto?,
)

@Serializable
internal data class AniListPageDto(
    @SerialName("pageInfo") val pageInfo: AniListPageInfoDto?,
    @SerialName("mediaList") val mediaList: List<AniListMediaListEntryDto?>?,
)

@Serializable
internal data class AniListPageInfoDto(
    @SerialName("currentPage") val currentPage: Int?,
    @SerialName("hasNextPage") val hasNextPage: Boolean?,
)

@Serializable
internal data class AniListMediaListEntryDto(
    @SerialName("id") val id: Int?,
    @SerialName("status") val status: String?,
    @SerialName("progress") val progress: Int?,
    @SerialName("progressVolumes") val progressVolumes: Int?,
    @SerialName("updatedAt") val updatedAt: Long?,
    @SerialName("createdAt") val createdAt: Long?,
    @SerialName("media") val media: AniListMediaDto?,
)

@Serializable
internal data class AniListMediaDto(
    @SerialName("id") val id: Int?,
    @SerialName("type") val type: String?,
    @SerialName("format") val format: String?,
    @SerialName("status") val status: String?,
    @SerialName("episodes") val episodes: Int?,
    @SerialName("chapters") val chapters: Int?,
    @SerialName("volumes") val volumes: Int?,
    @SerialName("description") val description: String?,
    @SerialName("bannerImage") val bannerImage: String?,
    @SerialName("genres") val genres: List<String?>?,
    @SerialName("seasonYear") val seasonYear: Int? = null,
    @SerialName("averageScore") val averageScore: Int? = null,
    @SerialName("title") val title: AniListMediaTitleDto?,
    @SerialName("coverImage") val coverImage: AniListCoverImageDto?,
    @SerialName("airingSchedule") val airingSchedule: AniListAiringScheduleConnectionDto?,
)

@Serializable
internal data class AniListMediaTitleDto(
    @SerialName("userPreferred") val userPreferred: String? = null,
    @SerialName("romaji") val romaji: String? = null,
    @SerialName("english") val english: String? = null,
    @SerialName("native") val native: String? = null,
)

@Serializable
internal data class AniListCoverImageDto(
    @SerialName("extraLarge") val extraLarge: String? = null,
    @SerialName("large") val large: String? = null,
    @SerialName("medium") val medium: String? = null,
)

@Serializable
internal data class AniListAiringScheduleConnectionDto(
    @SerialName("nodes") val nodes: List<AniListAiringScheduleNodeDto?>? = null,
)

@Serializable
internal data class AniListAiringScheduleNodeDto(
    @SerialName("episode") val episode: Int? = null,
    @SerialName("airingAt") val airingAt: Long? = null,
)
