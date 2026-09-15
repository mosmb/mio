package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AniListSyncEntryResponseDto(
    @SerialName("data") val data: Data? = null,
    @SerialName("errors") val errors: List<AniListGraphQlErrorDto?>? = null,
) {
    @Serializable
    internal data class Data(
        @SerialName("MediaList") val mediaList: Entry? = null,
    )

    @Serializable
    internal data class Entry(
        val id: Int? = null,
        val userId: Int? = null,
        val mediaId: Int? = null,
        val status: String? = null,
        val progress: Int? = null,
        val progressVolumes: Int? = null,
        val updatedAt: Long? = null,
        val media: Media? = null,
    )

    @Serializable
    internal data class Media(
        val id: Int? = null,
        val episodes: Int? = null,
        val chapters: Int? = null,
        val volumes: Int? = null,
    )
}
