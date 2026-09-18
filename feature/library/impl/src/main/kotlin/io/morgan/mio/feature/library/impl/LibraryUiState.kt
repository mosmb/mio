package io.morgan.mio.feature.library.impl

internal data class LibraryUiState(
    val isLoading: Boolean = true,
    val hasObservedCollection: Boolean = false,
    val observationFailed: Boolean = false,
    val collectionSize: Int = 0,
    val rows: List<LibraryRowUiModel> = emptyList(),
    val availableStatusFilters: List<LibraryStatusFilter> = DEFAULT_STATUS_FILTERS,
    val mediaFilter: LibraryMediaFilter = LibraryMediaFilter.ALL,
    val statusFilter: LibraryStatusFilter = LibraryStatusFilter.CURRENT,
) {
    val isCollectionEmpty: Boolean
        get() = hasObservedCollection && collectionSize == 0

    val isFilteredEmpty: Boolean
        get() = hasObservedCollection && collectionSize > 0 && rows.isEmpty()

    companion object {
        val DEFAULT_STATUS_FILTERS =
            listOf(
                LibraryStatusFilter.ALL,
                LibraryStatusFilter.READY,
                LibraryStatusFilter.CURRENT,
                LibraryStatusFilter.PLANNED,
                LibraryStatusFilter.PAUSED,
                LibraryStatusFilter.COMPLETED,
                LibraryStatusFilter.DROPPED,
            )
    }
}
