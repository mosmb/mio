package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.Serializable

@Serializable
internal data class AniListSyncEntryRequestDto(
    val query: String,
    val variables: Variables,
) {
    @Serializable
    internal data class Variables(
        val id: Int,
    )
}
