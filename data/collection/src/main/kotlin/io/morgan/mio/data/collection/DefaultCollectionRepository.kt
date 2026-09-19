package io.morgan.mio.data.collection

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.data.collection.local.RoomCollectionLocalDataSource
import io.morgan.mio.data.sync.PendingOperationWriter
import io.morgan.mio.domain.collection.CollectionAction
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.collection.CollectionStatusChange
import io.morgan.mio.domain.collection.CollectionWriteResult
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import kotlinx.coroutines.flow.Flow

@ContributesBinding(AccountScope::class)
@SingleIn(AccountScope::class)
@Inject
public class DefaultCollectionRepository(
    private val accountId: AccountId,
    private val localDataSource: RoomCollectionLocalDataSource,
    private val refreshRequester: CollectionRefreshRequester,
    private val pendingOperationWriter: PendingOperationWriter,
) : CollectionRepository {
    override fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>> =
        localDataSource.observeCollection(accountId)

    override fun observeEntry(entryId: CollectionEntryId): Flow<CollectionEntry?> =
        localDataSource.observeEntry(entryId)

    override fun observeEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<CollectionEntry?> = localDataSource.observeEntryForMedia(accountId, mediaId)

    override suspend fun readEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): CollectionEntry? = localDataSource.readEntryForMedia(accountId, mediaId)

    override fun observeWritableStatuses(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<List<CollectionStatus>> = localDataSource.observeWritableStatuses(accountId, mediaId)

    override suspend fun findAction(actionId: OperationGroupId): CollectionAction? =
        localDataSource.findAction(accountId, actionId)

    override fun observeLatestAction(): Flow<CollectionAction?> = localDataSource.observeLatestAction(accountId)

    override suspend fun readLatestAction(): CollectionAction? = localDataSource.readLatestAction(accountId)

    override suspend fun commitStatus(change: CollectionStatusChange): CollectionWriteResult =
        localDataSource.commitStatus(change, pendingOperationWriter)

    override suspend fun undo(actionId: OperationGroupId): CollectionWriteResult =
        localDataSource.undo(accountId, actionId, pendingOperationWriter)

    override suspend fun refreshCollection(accountId: AccountId) {
        refreshRequester.refreshCollection(accountId)
    }
}
