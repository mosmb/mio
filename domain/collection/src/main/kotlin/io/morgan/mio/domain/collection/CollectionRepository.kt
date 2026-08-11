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

    public suspend fun refreshCollection(accountId: AccountId)
}
