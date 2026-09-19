package io.morgan.mio.feature.library.impl

internal sealed interface LibraryAction {
    data class QueryChanged(
        val query: String,
    ) : LibraryAction

    data class SelectMediaFilter(
        val filter: LibraryMediaFilter,
    ) : LibraryAction

    data class SelectStatusFilter(
        val filter: LibraryStatusFilter,
    ) : LibraryAction

    data object ClearFilters : LibraryAction

    data class Step(
        val mediaId: String,
        val direction: LibraryProgressDirection,
        val timeoutMillis: Long,
    ) : LibraryAction

    data class Undo(
        val groupId: String,
    ) : LibraryAction

    data class InteractionChanged(
        val mediaId: String,
        val pressed: Boolean,
    ) : LibraryAction

    data object RetryProgress : LibraryAction

    data object DismissProgressError : LibraryAction

    data object RetryObservation : LibraryAction
}

internal enum class LibraryProgressDirection {
    INCREASE,
    DECREASE,
}
