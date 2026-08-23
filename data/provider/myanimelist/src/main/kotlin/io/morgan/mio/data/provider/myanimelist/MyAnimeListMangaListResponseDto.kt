package io.morgan.mio.data.provider.myanimelist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MyAnimeListMangaListResponseDto(
    val data: List<MyAnimeListMangaListEntryDto?>? = null,
    val paging: MyAnimeListPagingDto? = null,
)

@Serializable
internal data class MyAnimeListMangaListEntryDto(
    val node: MyAnimeListMangaDto? = null,
    @SerialName("list_status") val listStatus: MyAnimeListMangaListStatusDto? = null,
)

@Serializable
internal data class MyAnimeListMangaDto(
    val id: Int? = null,
    val title: String? = null,
    @SerialName("main_picture") val mainPicture: MyAnimeListPictureDto? = null,
    val synopsis: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val status: String? = null,
    @SerialName("num_chapters") val numChapters: Int? = null,
    @SerialName("num_volumes") val numVolumes: Int? = null,
    val mean: Double? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("start_season") val startSeason: MyAnimeListStartSeasonDto? = null,
    val genres: List<MyAnimeListGenreDto?>? = null,
)

@Serializable
internal data class MyAnimeListMangaListStatusDto(
    val status: String? = null,
    @SerialName("num_chapters_read") val numChaptersRead: Int? = null,
    @SerialName("num_volumes_read") val numVolumesRead: Int? = null,
    @SerialName("is_rereading") val isRereading: Boolean? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
