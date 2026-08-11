package io.morgan.mio.core.testing.repository

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

public class FakeCollectionRepository : CollectionRepository {
    private val entries = MutableStateFlow<List<CollectionEntry>>(emptyList())
    private val refreshedAccountIdValues = MutableStateFlow<List<AccountId>>(emptyList())

    public val refreshedAccountIds: List<AccountId>
        get() = refreshedAccountIdValues.value.toList()

    public fun setEntries(entries: List<CollectionEntry>) {
        this.entries.value = entries.toList()
    }

    override fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>> =
        entries
            .map { current ->
                current
                    .filter { entry -> entry.accountId == accountId }
                    .sortedBy { entry -> entry.id.value }
                    .toList()
            }.distinctUntilChanged()

    override fun observeEntry(entryId: CollectionEntryId): Flow<CollectionEntry?> =
        entries
            .map { current -> current.firstOrNull { entry -> entry.id == entryId } }
            .distinctUntilChanged()

    override fun observeEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<CollectionEntry?> =
        entries
            .map { current ->
                current.firstOrNull { entry ->
                    entry.accountId == accountId && entry.mediaId == mediaId
                }
            }.distinctUntilChanged()

    override suspend fun refreshCollection(accountId: AccountId) {
        refreshedAccountIdValues.update { accountIds -> accountIds + accountId }
    }
}
