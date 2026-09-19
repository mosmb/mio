package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AniListSyncMutationResponseDto(
    @SerialName("data") val data: Data? = null,
    @SerialName("errors") val errors: List<AniListGraphQlErrorDto?>? = null,
) {
    @Serializable
    internal data class Data(
        @SerialName("SaveMediaListEntry") val mediaList: Entry? = null,
        @SerialName("DeleteMediaListEntry") val deleteResult: DeleteResult? = null,
    )

    @Serializable
    internal data class DeleteResult(
        val deleted: Boolean? = null,
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
