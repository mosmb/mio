package io.morgan.mio.data.provider.myanimelist

import kotlinx.serialization.Serializable

@Serializable
internal data class MyAnimeListAlternativeTitlesDto(
    val en: String? = null,
    val ja: String? = null,
)
