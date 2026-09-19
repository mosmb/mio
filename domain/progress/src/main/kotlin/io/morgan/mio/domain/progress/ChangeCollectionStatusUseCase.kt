package io.morgan.mio.domain.progress

import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.collection.CollectionStatusChange
import io.morgan.mio.domain.collection.CollectionWriteResult
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public class ChangeCollectionStatusUseCase(
    private val repository: CollectionRepository,
    private val clock: Clock,
) {
    public suspend operator fun invoke(
        accountId: AccountId,
        mediaId: MediaId,
        progressUnit: ProgressUnit,
        knownTotal: UnitTotal?,
        targetStatus: CollectionStatus,
        actionId: OperationGroupId,
        undoWindow: Duration,
    ): CollectionWriteResult {
        val existingAction = repository.findAction(actionId)
        if (existingAction != null &&
            (existingAction.accountId != accountId || existingAction.mediaId != mediaId)
        ) {
            return CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.ACTION_ID_REUSED)
        }
        if (!undoWindow.isFinite() || undoWindow < MIN_UNDO_WINDOW) {
            return CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.INVALID_REQUEST)
        }
        val before = repository.readEntryForMedia(accountId, mediaId)
        val unit = before?.progressUnit ?: progressUnit
        if (before != null && before.progressUnit != progressUnit) {
            return CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.INVALID_REQUEST)
        }
        val currentProgress = before?.progress ?: ProgressValue(0)
        val targetTotal = before?.total ?: knownTotal
        val targetProgress =
            if (targetStatus == CollectionStatus.COMPLETED && targetTotal != null) {
                ProgressValue(targetTotal.value)
            } else {
                currentProgress
            }
        if (existingAction != null) {
            return if (
                existingAction.after.status == targetStatus &&
                existingAction.after.progress == targetProgress &&
                existingAction.after.progressUnit == unit &&
                existingAction.after.total == targetTotal
            ) {
                CollectionWriteResult.AlreadyHandled(existingAction)
            } else {
                CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.ACTION_ID_REUSED)
            }
        }
        if (targetStatus !in repository.observeWritableStatuses(accountId, mediaId).first()) {
            return CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.UNSUPPORTED_STATUS)
        }
        if (
            before != null &&
            before.status == targetStatus &&
            before.progress == targetProgress &&
            before.total == targetTotal
        ) {
            return CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.INVALID_REQUEST)
        }
        val now = clock.now()
        return repository.commitStatus(
            CollectionStatusChange(
                actionId = actionId,
                accountId = accountId,
                mediaId = mediaId,
                progressUnit = unit,
                before = before,
                targetStatus = targetStatus,
                targetProgress = targetProgress,
                targetTotal = targetTotal,
                createdAt = now,
                undoUntil = now + undoWindow,
            ),
        )
    }

    private companion object {
        val MIN_UNDO_WINDOW: Duration = 6.seconds
    }
}
