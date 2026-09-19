package io.morgan.mio.feature.library.impl

internal data class LibraryUiState(
    val isLoading: Boolean = true,
    val hasObservedCollection: Boolean = false,
    val observationFailed: Boolean = false,
    val collectionSize: Int = 0,
    val rows: List<LibraryRowUiModel> = emptyList(),
    val query: String = "",
    val availableStatusFilters: List<LibraryStatusFilter> = DEFAULT_STATUS_FILTERS,
    val mediaFilter: LibraryMediaFilter = LibraryMediaFilter.ALL,
    val statusFilter: LibraryStatusFilter = LibraryStatusFilter.CURRENT,
    val latestUndo: LibraryUndoUiModel? = null,
    val progressError: LibraryProgressErrorUiModel? = null,
) {
    val isCollectionEmpty: Boolean
        get() = hasObservedCollection && collectionSize == 0

    val isSearchEmpty: Boolean
        get() = hasObservedCollection && collectionSize > 0 && query.isNotBlank() && rows.isEmpty()

    val isFilteredEmpty: Boolean
        get() = hasObservedCollection && collectionSize > 0 && query.isBlank() && rows.isEmpty()

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
