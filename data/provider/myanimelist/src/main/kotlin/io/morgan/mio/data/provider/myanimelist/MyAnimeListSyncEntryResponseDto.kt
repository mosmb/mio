package io.morgan.mio.data.provider.myanimelist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MyAnimeListSyncEntryResponseDto(
    val id: Int? = null,
    @SerialName("num_episodes") val numEpisodes: Int? = null,
    @SerialName("num_chapters") val numChapters: Int? = null,
    @SerialName("num_volumes") val numVolumes: Int? = null,
    @SerialName("my_list_status") val myListStatus: MyListStatus? = null,
) {
    @Serializable
    internal data class MyListStatus(
        val status: String? = null,
        @SerialName("num_episodes_watched") val numEpisodesWatched: Int? = null,
        @SerialName("num_chapters_read") val numChaptersRead: Int? = null,
        @SerialName("num_volumes_read") val numVolumesRead: Int? = null,
        @SerialName("is_rewatching") val isRewatching: Boolean? = null,
        @SerialName("is_rereading") val isRereading: Boolean? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
    )
}
