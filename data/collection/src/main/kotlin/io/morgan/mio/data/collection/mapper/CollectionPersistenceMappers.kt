package io.morgan.mio.data.collection.mapper

import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.LocalCollectionActivityEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.domain.collection.CollectionAction
import io.morgan.mio.domain.collection.CollectionActionKind
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
        remotePresent = true,
        effectiveStatus = status.toStoredValue(),
        effectiveProgress = progress.value,
        effectiveTotal = total?.value,
        effectiveUpdatedAtUtcMs = updatedAt,
        effectivePresent = true,
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

internal fun LocalCollectionActivityEntity.toCollectionAction(): CollectionAction {
    val after =
        CollectionEntry(
            id = CollectionEntryId(collectionEntryId),
            accountId = AccountId(accountId),
            mediaId = MediaId(mediaId),
            status = afterStatus.toDomain(),
            progress = ProgressValue(afterProgress),
            progressUnit = progressUnit.toDomain(),
            total = afterTotal?.let(::UnitTotal),
            updatedAt = afterUpdatedAtUtcMs,
        )
    val before =
        if (!beforePresent) {
            null
        } else {
            CollectionEntry(
                id = CollectionEntryId(collectionEntryId),
                accountId = AccountId(accountId),
                mediaId = MediaId(mediaId),
                status = requireNotNull(beforeStatus).toDomain(),
                progress = ProgressValue(requireNotNull(beforeProgress)),
                progressUnit = progressUnit.toDomain(),
                total = beforeTotal?.let(::UnitTotal),
                updatedAt = requireNotNull(beforeUpdatedAtUtcMs),
            )
        }
    return CollectionAction(
        id =
            io.morgan.mio.domain.model
                .OperationGroupId(operationGroupId),
        accountId = AccountId(accountId),
        entryId = CollectionEntryId(collectionEntryId),
        mediaId = MediaId(mediaId),
        kind = if (actionKind == "add") CollectionActionKind.ADD else CollectionActionKind.STATUS,
        before = before,
        after = after,
        createdAt = createdAtUtcMs,
        undoUntil = undoUntilUtcMs,
        undoneAt = undoneAtUtcMs,
    )
}

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

internal fun ProgressUnit.toStoredUnit(): StoredProgressUnit = toStoredValue()

internal fun CollectionStatus.toStoredStatus(): StoredCollectionStatus = toStoredValue()

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
