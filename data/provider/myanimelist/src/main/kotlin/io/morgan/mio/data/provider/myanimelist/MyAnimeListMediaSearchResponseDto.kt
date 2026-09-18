package io.morgan.mio.data.provider.myanimelist

import kotlinx.serialization.Serializable

@Serializable
internal data class MyAnimeListMediaSearchResponseDto(
    val data: List<MyAnimeListMediaSearchEntryDto?>? = null,
    val paging: MyAnimeListPagingDto? = null,
)

@Serializable
internal data class MyAnimeListMediaSearchEntryDto(
    val node: MyAnimeListMediaDetailsResponseDto? = null,
)
