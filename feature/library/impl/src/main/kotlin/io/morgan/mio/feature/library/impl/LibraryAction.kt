package io.morgan.mio.feature.library.impl

internal sealed interface LibraryAction {
    data class SelectMediaFilter(
        val filter: LibraryMediaFilter,
    ) : LibraryAction

    data class SelectStatusFilter(
        val filter: LibraryStatusFilter,
    ) : LibraryAction

    data object ClearFilters : LibraryAction

    data object RetryObservation : LibraryAction
}
