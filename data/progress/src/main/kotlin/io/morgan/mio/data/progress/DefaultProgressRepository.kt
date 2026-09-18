package io.morgan.mio.data.progress

import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.LocalProgressActivityEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.data.progress.local.RoomProgressLocalDataSource
import io.morgan.mio.data.progress.mapper.toCompensatingOperationGroup
import io.morgan.mio.data.progress.mapper.toPendingOperationGroup
import io.morgan.mio.data.progress.mapper.toProgressAction
import io.morgan.mio.data.progress.mapper.toProgressSnapshot
import io.morgan.mio.data.sync.PendingOperationWriter
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.progress.ProgressAction
import io.morgan.mio.domain.progress.ProgressChange
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.ProgressRepository
import io.morgan.mio.domain.progress.ProgressSnapshot
import io.morgan.mio.domain.progress.ProgressUndo
import io.morgan.mio.domain.progress.ProgressWriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

public class DefaultProgressRepository(
    private val accountId: AccountId,
    private val database: MioDatabase,
    private val localDataSource: RoomProgressLocalDataSource,
    private val pendingOperationWriter: PendingOperationWriter,
    private val clock: Clock,
) : ProgressRepository {
    override suspend fun readSnapshot(mediaId: MediaId): ProgressSnapshot? =
        localDataSource.getEntryForMedia(accountId, mediaId)?.toProgressSnapshot()

    override suspend fun findAction(actionId: OperationGroupId): ProgressAction? =
        database.progressDao().getActivity(accountId.value, actionId.value)?.toProgressAction()

    override fun observeLatestAction(): Flow<ProgressAction?> =
        database.progressDao().observeLatestActivity(accountId.value).map { activity -> activity?.toProgressAction() }

    override suspend fun readLatestAction(): ProgressAction? =
        database.progressDao().getLatestActivity(accountId.value)?.toProgressAction()

    override suspend fun commit(change: ProgressChange): ProgressWriteResult =
        database.inTransaction {
            val existing = database.progressDao().getActivity(accountId.value, change.actionId.value)
            if (existing != null) return@inTransaction existingResult(existing.toProgressAction(), change)
            if (database.progressDao().getActivityAccountId(change.actionId.value) != null) {
                return@inTransaction ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.ACTION_ID_REUSED)
            }
            if (database.pendingOperationDao().getGroup(change.actionId.value) != null) {
                return@inTransaction ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.ACTION_ID_REUSED)
            }
            if (!isValidRequest(change)) {
                return@inTransaction ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.INVALID_REQUEST)
            }

            val actual =
                localDataSource.getEntryForMedia(accountId, change.before.entry.mediaId)
                    ?: return@inTransaction ProgressWriteResult.Rejected(
                        ProgressWriteResult.Rejection.MISSING_ENTRY,
                    )
            if (!matchesBefore(actual, change.before)) return@inTransaction ProgressWriteResult.Stale

            val afterRevision =
                change.before.revision
                    .takeIf { revision -> revision != Long.MAX_VALUE }
                    ?.plus(1L)
                    ?: return@inTransaction ProgressWriteResult.Rejected(
                        ProgressWriteResult.Rejection.INVALID_REQUEST,
                    )
            val (createdAt, undoUntil) =
                timestampFor(change.undoWindow)
                    ?: return@inTransaction ProgressWriteResult.Rejected(
                        ProgressWriteResult.Rejection.INVALID_REQUEST,
                    )
            val afterStatus = change.targetStatus.toStoredStatus()
            val progressUpdated =
                localDataSource.updateEffectiveState(
                    entryId = actual.collectionEntryId,
                    accountId = accountId,
                    mediaId = actual.mediaId.let(::MediaId),
                    expectedRevision = actual.localProgressRevision,
                    expectedStatus = actual.effectiveStatus,
                    expectedProgress = actual.effectiveProgress,
                    expectedTotal = actual.effectiveTotal,
                    expectedUpdatedAtUtcMs = actual.effectiveUpdatedAtUtcMs,
                    targetStatus = afterStatus,
                    targetProgress = change.targetProgress.value,
                    targetUpdatedAtUtcMs = createdAt,
                    targetRevision = afterRevision,
                )
            if (progressUpdated != 1) return@inTransaction ProgressWriteResult.Stale

            val persistedChange = change.copy(before = actual.toProgressSnapshot())
            pendingOperationWriter.enqueue(persistedChange.toPendingOperationGroup(createdAt))
            val activity =
                LocalProgressActivityEntity(
                    operationGroupId = change.actionId.value,
                    accountId = accountId.value,
                    collectionEntryId = actual.collectionEntryId,
                    mediaId = actual.mediaId,
                    direction = change.direction.toStoredDirection(),
                    progressUnit = actual.progressUnit,
                    beforeProgress = actual.effectiveProgress,
                    afterProgress = change.targetProgress.value,
                    beforeStatus = actual.effectiveStatus,
                    afterStatus = afterStatus,
                    beforeUpdatedAtUtcMs = actual.effectiveUpdatedAtUtcMs,
                    afterUpdatedAtUtcMs = createdAt,
                    afterRevision = afterRevision,
                    createdAtUtcMs = createdAt,
                    undoUntilUtcMs = undoUntil,
                    undoneAtUtcMs = null,
                )
            val order = localDataSource.insertActivity(activity)
            ProgressWriteResult.Committed(activity.copy(activityOrder = order).toProgressAction())
        }

    override suspend fun undo(change: ProgressUndo): ProgressWriteResult =
        database.inTransaction {
            val stored =
                database.progressDao().getActivity(accountId.value, change.action.id.value)
                    ?: return@inTransaction unavailable()
            val storedAction = stored.toProgressAction()
            val latest =
                database.progressDao().getLatestActivity(accountId.value)
                    ?: return@inTransaction unavailable()
            if (latest.operationGroupId != stored.operationGroupId || storedAction != change.action) {
                return@inTransaction unavailable()
            }
            if (stored.undoneAtUtcMs != null) return@inTransaction unavailable()
            val actual =
                localDataSource.getEntryForMedia(accountId, stored.mediaId.let(::MediaId))
                    ?: return@inTransaction unavailable()
            if (!matchesUndoCurrent(actual, change.current, storedAction)) return@inTransaction unavailable()

            val restoredRevision =
                actual.localProgressRevision.takeIf { revision -> revision != Long.MAX_VALUE }?.plus(1L)
                    ?: return@inTransaction unavailable()
            val cancellationTime = clock.now()
            if (cancellationTime >= stored.undoUntilUtcMs) return@inTransaction unavailable()
            val compensation = change.toCompensatingOperationGroup(cancellationTime)
            if (!pendingOperationWriter.undo(change.action.id, compensation)) return@inTransaction unavailable()

            val restored =
                localDataSource.updateEffectiveState(
                    entryId = actual.collectionEntryId,
                    accountId = accountId,
                    mediaId = MediaId(actual.mediaId),
                    expectedRevision = actual.localProgressRevision,
                    expectedStatus = actual.effectiveStatus,
                    expectedProgress = actual.effectiveProgress,
                    expectedTotal = actual.effectiveTotal,
                    expectedUpdatedAtUtcMs = actual.effectiveUpdatedAtUtcMs,
                    targetStatus = stored.beforeStatus,
                    targetProgress = stored.beforeProgress,
                    targetUpdatedAtUtcMs = stored.beforeUpdatedAtUtcMs,
                    targetRevision = restoredRevision,
                )
            if (restored != 1) throw IllegalStateException("Progress Undo lost its expected revision")
            val undoneAt = toStorageInstant(cancellationTime)
            if (localDataSource.markUndone(accountId, change.action.id, undoneAt) != 1) {
                throw IllegalStateException("Progress activity was not marked undone")
            }
            ProgressWriteResult.Committed(stored.copy(undoneAtUtcMs = undoneAt).toProgressAction())
        }

    private fun existingResult(
        existing: ProgressAction,
        change: ProgressChange,
    ): ProgressWriteResult =
        if (existing.entryId == change.before.entry.id &&
            existing.mediaId == change.before.entry.mediaId &&
            existing.direction == change.direction
        ) {
            ProgressWriteResult.AlreadyHandled(existing)
        } else {
            ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.ACTION_ID_REUSED)
        }

    private fun isValidRequest(change: ProgressChange): Boolean {
        val entry = change.before.entry
        if (entry.accountId != accountId) return false
        if (entry.status !in setOf(CollectionStatus.CURRENT, CollectionStatus.COMPLETED)) return false
        if (change.targetProgress.value < 0) return false
        if (change.targetStatus !in setOf(CollectionStatus.CURRENT, CollectionStatus.COMPLETED)) return false
        if (!change.undoWindow.isFinite() || change.undoWindow < MIN_UNDO_WINDOW) return false
        val total = entry.total?.value
        return when (change.direction) {
            ProgressDirection.INCREASE ->
                entry.status == CollectionStatus.CURRENT &&
                    change.targetProgress.value.toLong() == entry.progress.value.toLong() + 1L &&
                    (total == null || entry.progress.value < total) &&
                    change.targetStatus ==
                    if (total == change.targetProgress.value) {
                        CollectionStatus.COMPLETED
                    } else {
                        CollectionStatus.CURRENT
                    }

            ProgressDirection.DECREASE ->
                entry.status == CollectionStatus.CURRENT &&
                    entry.progress.value > 0 &&
                    change.targetProgress.value == entry.progress.value - 1 &&
                    change.targetStatus == CollectionStatus.CURRENT

            ProgressDirection.CORRECTION ->
                change.targetProgress != entry.progress &&
                    (total == null || change.targetProgress.value <= total) &&
                    change.targetStatus ==
                    if (total == change.targetProgress.value) {
                        CollectionStatus.COMPLETED
                    } else {
                        CollectionStatus.CURRENT
                    }
        }
    }

    private fun matchesBefore(
        actual: io.morgan.mio.core.database.entity.CollectionEntryEntity,
        before: ProgressSnapshot,
    ): Boolean =
        actual.collectionEntryId == before.entry.id.value &&
            actual.accountId == accountId.value &&
            actual.mediaId == before.entry.mediaId.value &&
            actual.progressUnit == before.entry.progressUnit.toStoredUnit() &&
            actual.effectiveStatus == before.entry.status.toStoredStatus() &&
            actual.effectiveProgress == before.entry.progress.value &&
            actual.effectiveTotal == before.entry.total?.value &&
            actual.effectiveUpdatedAtUtcMs == before.entry.updatedAt &&
            actual.localProgressRevision == before.revision

    private fun matchesUndoCurrent(
        actual: io.morgan.mio.core.database.entity.CollectionEntryEntity,
        requested: ProgressSnapshot,
        action: ProgressAction,
    ): Boolean =
        actual.toProgressSnapshot() == requested &&
            actual.collectionEntryId == action.entryId.value &&
            actual.accountId == accountId.value &&
            actual.mediaId == action.mediaId.value &&
            actual.effectiveProgress == action.afterProgress.value &&
            actual.effectiveStatus == action.afterStatus.toStoredStatus() &&
            actual.effectiveUpdatedAtUtcMs == action.afterUpdatedAt &&
            actual.localProgressRevision == action.afterRevision

    private fun timestampFor(window: Duration): Pair<Instant, Instant>? {
        if (!window.isFinite() || window < MIN_UNDO_WINDOW) return null
        return try {
            val now = clock.now()
            val deadline = now + window
            toStorageInstant(now) to toStorageInstant(deadline)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun toStorageInstant(value: Instant): Instant = Instant.fromEpochMilliseconds(value.toEpochMilliseconds())

    private fun unavailable(): ProgressWriteResult =
        ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.UNDO_UNAVAILABLE)

    private fun ProgressDirection.toStoredDirection(): String =
        when (this) {
            ProgressDirection.INCREASE -> "increase"
            ProgressDirection.DECREASE -> "decrease"
            ProgressDirection.CORRECTION -> "correction"
        }

    private fun CollectionStatus.toStoredStatus(): StoredCollectionStatus =
        when (this) {
            CollectionStatus.CURRENT -> StoredCollectionStatus.CURRENT
            CollectionStatus.PLANNED -> StoredCollectionStatus.PLANNED
            CollectionStatus.PAUSED -> StoredCollectionStatus.PAUSED
            CollectionStatus.COMPLETED -> StoredCollectionStatus.COMPLETED
            CollectionStatus.DROPPED -> StoredCollectionStatus.DROPPED
            CollectionStatus.REPEATING -> StoredCollectionStatus.REPEATING
        }

    private fun ProgressUnit.toStoredUnit(): StoredProgressUnit =
        when (this) {
            ProgressUnit.EPISODE -> StoredProgressUnit.EPISODE
            ProgressUnit.CHAPTER -> StoredProgressUnit.CHAPTER
            ProgressUnit.VOLUME -> StoredProgressUnit.VOLUME
        }

    private companion object {
        val MIN_UNDO_WINDOW: Duration = 6.seconds
    }
}
