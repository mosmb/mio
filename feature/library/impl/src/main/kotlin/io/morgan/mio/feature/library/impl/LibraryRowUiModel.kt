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
    val controls: LibraryProgressControlsUiModel?,
)

internal data class LibraryProgressControlsUiModel(
    val decreaseEnabled: Boolean,
    val increaseEnabled: Boolean,
)

internal data class LibraryUndoUiModel(
    val groupId: String,
    val mediaId: String,
    val progress: Int,
    val unit: LibraryProgressUnit,
)

internal data class LibraryProgressErrorUiModel(
    val retryable: Boolean,
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
