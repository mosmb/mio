package io.morgan.mio.data.provider.myanimelist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MyAnimeListAnimeListResponseDto(
    val data: List<MyAnimeListAnimeListEntryDto?>? = null,
    val paging: MyAnimeListPagingDto? = null,
)

@Serializable
internal data class MyAnimeListPagingDto(
    val next: String? = null,
    val previous: String? = null,
)

@Serializable
internal data class MyAnimeListAnimeListEntryDto(
    val node: MyAnimeListAnimeDto? = null,
    @SerialName("list_status") val listStatus: MyAnimeListAnimeListStatusDto? = null,
)

@Serializable
internal data class MyAnimeListAnimeDto(
    val id: Int? = null,
    val title: String? = null,
    @SerialName("main_picture") val mainPicture: MyAnimeListPictureDto? = null,
    val synopsis: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val status: String? = null,
    @SerialName("num_episodes") val numEpisodes: Int? = null,
    val mean: Double? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("start_season") val startSeason: MyAnimeListStartSeasonDto? = null,
    val genres: List<MyAnimeListGenreDto?>? = null,
)

@Serializable
internal data class MyAnimeListStartSeasonDto(
    val year: Int? = null,
)

@Serializable
internal data class MyAnimeListPictureDto(
    val medium: String? = null,
    val large: String? = null,
)

@Serializable
internal data class MyAnimeListGenreDto(
    val id: Int? = null,
    val name: String? = null,
)

@Serializable
internal data class MyAnimeListAnimeListStatusDto(
    val status: String? = null,
    @SerialName("num_episodes_watched") val numEpisodesWatched: Int? = null,
    @SerialName("is_rewatching") val isRewatching: Boolean? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
