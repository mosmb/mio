package io.morgan.mio.domain.media

import io.morgan.mio.domain.model.MediaType
import kotlinx.coroutines.flow.Flow

public interface MediaBrowseRepository {
    public fun categories(type: MediaType): List<MediaBrowseCategory>

    public fun observe(
        type: MediaType,
        category: MediaBrowseCategory,
    ): Flow<List<Media>?>

    public suspend fun refresh(
        type: MediaType,
        category: MediaBrowseCategory,
    ): MediaSearchCommandOutcome
}
