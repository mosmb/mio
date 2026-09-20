package io.morgan.mio.feature.discover.impl

import io.morgan.mio.core.ui.title.MediaTitleNames

internal data class DiscoverUiState(
    val query: String = "",
    val filter: DiscoverMediaFilter = DiscoverMediaFilter.ALL,
    val results: List<DiscoverResultUiModel> = emptyList(),
    val hasObservedResults: Boolean = false,
    val hasMore: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val refreshFailed: Boolean = false,
    val loadMoreFailed: Boolean = false,
)

internal data class DiscoverResultUiModel(
    val mediaId: String,
    val title: String,
    val posterReference: String?,
    val type: DiscoverMediaType,
    val trackedStatus: DiscoverTrackedStatus?,
    val titleNames: MediaTitleNames =
        io.morgan.mio.core.ui.title
            .MediaTitleNames(),
)

internal enum class DiscoverMediaType {
    ANIME,
    MANGA,
}

internal enum class DiscoverTrackedStatus {
    CURRENT,
    PLANNED,
    PAUSED,
    COMPLETED,
    DROPPED,
    REPEATING,
}
