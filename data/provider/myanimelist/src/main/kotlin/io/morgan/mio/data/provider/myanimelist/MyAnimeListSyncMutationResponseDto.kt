package io.morgan.mio.data.provider.myanimelist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MyAnimeListSyncMutationResponseDto(
    val status: String? = null,
    @SerialName("num_episodes_watched") val numEpisodesWatched: Int? = null,
    @SerialName("num_chapters_read") val numChaptersRead: Int? = null,
    @SerialName("num_volumes_read") val numVolumesRead: Int? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
