package io.morgan.mio.domain.media

import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.flow.Flow

public interface MediaRepository {
    public fun observeMedia(mediaId: MediaId): Flow<Media?>

    public fun observeReleases(mediaId: MediaId): Flow<List<Release>>

    public suspend fun refreshMedia(mediaId: MediaId)
}
