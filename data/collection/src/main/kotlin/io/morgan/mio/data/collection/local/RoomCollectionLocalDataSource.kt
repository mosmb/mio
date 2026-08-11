package io.morgan.mio.data.collection.local

import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.data.collection.mapper.CollectionEntryPersistenceSnapshot
import io.morgan.mio.data.collection.mapper.toEffectiveDomain
import io.morgan.mio.data.collection.mapper.toEntity
import io.morgan.mio.data.collection.mapper.toInitialEntity
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public class RoomCollectionLocalDataSource(
    private val database: MioDatabase,
) {
    public fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>> =
        database.collectionEntryDao().observeCollection(accountId.value).map { entries ->
            entries.map { entry -> entry.toEffectiveDomain() }.toList()
        }

    public fun observeEntry(entryId: CollectionEntryId): Flow<CollectionEntry?> =
        database.collectionEntryDao().observeEntry(entryId.value).map { entry -> entry?.toEffectiveDomain() }

    public fun observeEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<CollectionEntry?> =
        database
            .collectionEntryDao()
            .observeEntryForMedia(accountId.value, mediaId.value)
            .map { entry -> entry?.toEffectiveDomain() }

    internal suspend fun replaceInitialCollectionSnapshot(
        accountId: AccountId,
        input: List<CollectionEntryPersistenceSnapshot>,
        isComplete: Boolean,
    ) {
        val snapshots = input.toList()
        snapshots.forEach { snapshot ->
            require(snapshot.entry.accountId == accountId)
            require(snapshot.providerIds.all { mapping -> mapping.entryId == snapshot.entry.id })
        }
        val entryIds = snapshots.map { snapshot -> snapshot.entry.id }.distinct()
        require(entryIds.size == snapshots.size)
        val mediaIds = snapshots.map { snapshot -> snapshot.entry.mediaId }
        require(mediaIds.distinct().size == mediaIds.size)
        val entries = snapshots.map { snapshot -> snapshot.entry.toInitialEntity() }
        val providerMappings =
            snapshots
                .flatMap { snapshot -> snapshot.providerIds.toList() }
                .map { mapping -> mapping.toEntity() }

        database.inTransaction {
            if (isComplete) {
                if (entryIds.isEmpty()) {
                    database.collectionEntryDao().deleteAllForAccount(accountId.value)
                } else {
                    database.collectionEntryDao().deleteEntriesNotIn(
                        accountId = accountId.value,
                        entryIds = entryIds.map(CollectionEntryId::value),
                    )
                }
            }
            entryIds.forEach { entryId ->
                database.providerIdDao().deleteCollectionEntryProviderIds(entryId.value)
            }
            if (entries.isNotEmpty()) database.collectionEntryDao().upsertEntries(entries)
            if (providerMappings.isNotEmpty()) {
                database.providerIdDao().upsertCollectionEntryProviderIds(providerMappings)
            }
        }
    }
}
