package io.morgan.mio.data.collection.mapper

import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal

internal data class CollectionEntryProviderIdInput(
    val entryId: CollectionEntryId,
    val provider: StoredConnectedProvider,
    val remoteId: String,
) {
    init {
        require(remoteId.isNotBlank())
    }
}

internal data class CollectionEntryPersistenceSnapshot(
    val entry: CollectionEntry,
    val providerIds: List<CollectionEntryProviderIdInput> = emptyList(),
)

internal fun CollectionEntry.toInitialEntity(): CollectionEntryEntity =
    CollectionEntryEntity(
        collectionEntryId = id.value,
        accountId = accountId.value,
        mediaId = mediaId.value,
        progressUnit = progressUnit.toStoredValue(),
        remoteStatus = status.toStoredValue(),
        remoteProgress = progress.value,
        remoteTotal = total?.value,
        remoteUpdatedAtUtcMs = updatedAt,
        effectiveStatus = status.toStoredValue(),
        effectiveProgress = progress.value,
        effectiveTotal = total?.value,
        effectiveUpdatedAtUtcMs = updatedAt,
    )

internal fun CollectionEntryEntity.toEffectiveDomain(): CollectionEntry =
    CollectionEntry(
        id = CollectionEntryId(collectionEntryId),
        accountId = AccountId(accountId),
        mediaId = MediaId(mediaId),
        status = effectiveStatus.toDomain(),
        progress = ProgressValue(effectiveProgress),
        progressUnit = progressUnit.toDomain(),
        total = effectiveTotal?.let(::UnitTotal),
        updatedAt = effectiveUpdatedAtUtcMs,
    )

internal fun CollectionEntryProviderIdInput.toEntity(): CollectionEntryProviderIdEntity =
    CollectionEntryProviderIdEntity(
        provider = provider,
        remoteCollectionEntryId = remoteId,
        collectionEntryId = entryId.value,
    )

private fun ProgressUnit.toStoredValue(): StoredProgressUnit =
    when (this) {
        ProgressUnit.EPISODE -> StoredProgressUnit.EPISODE
        ProgressUnit.CHAPTER -> StoredProgressUnit.CHAPTER
        ProgressUnit.VOLUME -> StoredProgressUnit.VOLUME
    }

private fun StoredProgressUnit.toDomain(): ProgressUnit =
    when (this) {
        StoredProgressUnit.EPISODE -> ProgressUnit.EPISODE
        StoredProgressUnit.CHAPTER -> ProgressUnit.CHAPTER
        StoredProgressUnit.VOLUME -> ProgressUnit.VOLUME
    }

private fun CollectionStatus.toStoredValue(): StoredCollectionStatus =
    when (this) {
        CollectionStatus.CURRENT -> StoredCollectionStatus.CURRENT
        CollectionStatus.PLANNED -> StoredCollectionStatus.PLANNED
        CollectionStatus.PAUSED -> StoredCollectionStatus.PAUSED
        CollectionStatus.COMPLETED -> StoredCollectionStatus.COMPLETED
        CollectionStatus.DROPPED -> StoredCollectionStatus.DROPPED
        CollectionStatus.REPEATING -> StoredCollectionStatus.REPEATING
    }

private fun StoredCollectionStatus.toDomain(): CollectionStatus =
    when (this) {
        StoredCollectionStatus.CURRENT -> CollectionStatus.CURRENT
        StoredCollectionStatus.PLANNED -> CollectionStatus.PLANNED
        StoredCollectionStatus.PAUSED -> CollectionStatus.PAUSED
        StoredCollectionStatus.COMPLETED -> CollectionStatus.COMPLETED
        StoredCollectionStatus.DROPPED -> CollectionStatus.DROPPED
        StoredCollectionStatus.REPEATING -> CollectionStatus.REPEATING
    }
