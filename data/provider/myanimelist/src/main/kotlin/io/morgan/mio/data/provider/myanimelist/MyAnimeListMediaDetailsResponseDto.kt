package io.morgan.mio.data.provider.myanimelist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MyAnimeListMediaDetailsResponseDto(
    val id: Int? = null,
    val title: String? = null,
    @SerialName("alternative_titles") val alternativeTitles: MyAnimeListAlternativeTitlesDto? = null,
    @SerialName("main_picture") val mainPicture: MyAnimeListPictureDto? = null,
    val synopsis: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val status: String? = null,
    @SerialName("num_episodes") val numEpisodes: Int? = null,
    @SerialName("num_chapters") val numChapters: Int? = null,
    @SerialName("num_volumes") val numVolumes: Int? = null,
    val mean: Double? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("start_season") val startSeason: MyAnimeListStartSeasonDto? = null,
    val genres: List<MyAnimeListGenreDto?>? = null,
    @SerialName("related_anime") val relatedAnime: List<MyAnimeListRelatedMediaDto?>? = null,
    @SerialName("related_manga") val relatedManga: List<MyAnimeListRelatedMediaDto?>? = null,
)

@Serializable
internal data class MyAnimeListRelatedMediaDto(
    val node: MyAnimeListMediaDetailsResponseDto? = null,
)
