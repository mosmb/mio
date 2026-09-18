package io.morgan.mio.feature.discover.impl

internal sealed interface DiscoverAction {
    data class QueryChanged(
        val query: String,
    ) : DiscoverAction

    data object SubmitQuery : DiscoverAction

    data class SelectFilter(
        val filter: DiscoverMediaFilter,
    ) : DiscoverAction

    data object Retry : DiscoverAction

    data object LoadMore : DiscoverAction
}
