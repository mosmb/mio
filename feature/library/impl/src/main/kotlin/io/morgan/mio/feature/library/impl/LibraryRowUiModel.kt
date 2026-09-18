package io.morgan.mio.feature.library.impl

internal data class LibraryRowUiModel(
    val entryId: String,
    val mediaId: String,
    val title: String,
    val posterReference: String?,
    val mediaType: LibraryRowMediaType?,
    val status: LibraryRowStatus,
    val progress: Int,
    val progressUnit: LibraryProgressUnit,
    val total: Int?,
)

internal enum class LibraryRowMediaType {
    ANIME,
    MANGA,
}

internal enum class LibraryRowStatus {
    CURRENT,
    PLANNED,
    PAUSED,
    COMPLETED,
    DROPPED,
    REPEATING,
}

internal enum class LibraryProgressUnit {
    EPISODE,
    CHAPTER,
    VOLUME,
}
