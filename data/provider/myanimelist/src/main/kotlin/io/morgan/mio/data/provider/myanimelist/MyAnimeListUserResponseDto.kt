package io.morgan.mio.data.provider.myanimelist

import kotlinx.serialization.Serializable

@Serializable
internal data class MyAnimeListUserResponseDto(
    val id: Int? = null,
    val name: String? = null,
    val picture: String? = null,
)
