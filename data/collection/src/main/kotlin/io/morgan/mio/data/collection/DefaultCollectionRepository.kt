package io.morgan.mio.data.collection

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.data.collection.local.RoomCollectionLocalDataSource
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.flow.Flow

@ContributesBinding(AccountScope::class)
@SingleIn(AccountScope::class)
@Inject
public class DefaultCollectionRepository(
    private val localDataSource: RoomCollectionLocalDataSource,
    private val refreshRequester: CollectionRefreshRequester,
) : CollectionRepository {
    override fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>> =
        localDataSource.observeCollection(accountId)

    override fun observeEntry(entryId: CollectionEntryId): Flow<CollectionEntry?> =
        localDataSource.observeEntry(entryId)

    override fun observeEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<CollectionEntry?> = localDataSource.observeEntryForMedia(accountId, mediaId)

    override suspend fun refreshCollection(accountId: AccountId) {
        refreshRequester.refreshCollection(accountId)
    }
}
