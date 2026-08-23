package io.morgan.mio.data.media

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.data.media.local.RoomMediaLocalDataSource
import io.morgan.mio.domain.media.Media
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.media.Release
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.flow.Flow

@ContributesBinding(AccountScope::class)
@SingleIn(AccountScope::class)
@Inject
public class DefaultMediaRepository(
    private val localDataSource: RoomMediaLocalDataSource,
    private val refreshRequester: MediaRefreshRequester,
) : MediaRepository {
    override fun observeMedia(mediaId: MediaId): Flow<Media?> = localDataSource.observeMedia(mediaId)

    override fun observeRelatedMedia(mediaId: MediaId): Flow<List<Media>> = localDataSource.observeRelatedMedia(mediaId)

    override fun observeReleases(mediaId: MediaId): Flow<List<Release>> = localDataSource.observeReleases(mediaId)

    override suspend fun refreshMedia(mediaId: MediaId) {
        refreshRequester.refreshMedia(mediaId)
    }
}
