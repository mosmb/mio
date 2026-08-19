package io.morgan.mio.data.provider.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AniListViewerResponseDto(
    @SerialName("data") val data: AniListViewerDataDto?,
    @SerialName("errors") val errors: List<AniListGraphQlErrorDto?>?,
)

@Serializable
internal data class AniListViewerDataDto(
    @SerialName("Viewer") val viewer: AniListViewerDto?,
)

@Serializable
internal data class AniListViewerDto(
    @SerialName("id") val id: Int?,
    @SerialName("name") val name: String?,
    @SerialName("avatar") val avatar: AniListAvatarDto?,
)

@Serializable
internal data class AniListAvatarDto(
    @SerialName("large") val large: String?,
    @SerialName("medium") val medium: String?,
)
