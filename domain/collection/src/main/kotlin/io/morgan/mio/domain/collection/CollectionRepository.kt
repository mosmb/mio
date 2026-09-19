package io.morgan.mio.domain.collection

import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.flow.Flow

public interface CollectionRepository {
    public fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>>

    public fun observeEntry(entryId: CollectionEntryId): Flow<CollectionEntry?>

    public fun observeEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<CollectionEntry?>

    public suspend fun readEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): CollectionEntry?

    public fun observeWritableStatuses(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<List<CollectionStatus>>

    public suspend fun findAction(actionId: io.morgan.mio.domain.model.OperationGroupId): CollectionAction?

    public fun observeLatestAction(): Flow<CollectionAction?>

    public suspend fun readLatestAction(): CollectionAction?

    public suspend fun commitStatus(change: CollectionStatusChange): CollectionWriteResult

    public suspend fun undo(actionId: io.morgan.mio.domain.model.OperationGroupId): CollectionWriteResult

    public suspend fun refreshCollection(accountId: AccountId)
}
