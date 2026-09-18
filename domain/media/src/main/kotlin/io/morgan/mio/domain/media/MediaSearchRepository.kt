package io.morgan.mio.domain.media

import kotlinx.coroutines.flow.Flow

public interface MediaSearchRepository {
    public fun observeSearch(
        query: String,
        filter: MediaSearchFilter,
    ): Flow<MediaSearchResults?>

    public suspend fun refreshSearch(
        query: String,
        filter: MediaSearchFilter,
    ): MediaSearchCommandOutcome

    public suspend fun loadMore(
        query: String,
        filter: MediaSearchFilter,
    ): MediaSearchCommandOutcome
}
