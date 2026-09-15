package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.Serializable

@Serializable
internal data class AniListSyncMutationRequestDto(
    val query: String,
    val variables: Variables,
) {
    @Serializable
    internal data class Variables(
        val id: Int,
        val progress: Int? = null,
        val progressVolumes: Int? = null,
        val status: String? = null,
    ) {
        init {
            require(listOf(progress, progressVolumes, status).count { value -> value != null } == 1)
        }
    }
}
