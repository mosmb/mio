package io.morgan.mio.domain.progress

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.ProgressValue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public class CorrectProgressUseCase(
    private val repository: ProgressRepository,
    private val clock: Clock,
) {
    public suspend operator fun invoke(
        mediaId: MediaId,
        targetProgress: Int,
        actionId: OperationGroupId,
        undoWindow: Duration,
    ): ProgressWriteResult {
        if (targetProgress < 0 || !isUsableUndoWindow(undoWindow)) {
            return ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.INVALID_REQUEST)
        }

        val existing = repository.findAction(actionId)
        if (existing != null) {
            return if (existing.mediaId == mediaId &&
                existing.direction == ProgressDirection.CORRECTION &&
                existing.afterProgress.value == targetProgress
            ) {
                ProgressWriteResult.AlreadyHandled(existing)
            } else {
                ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.ACTION_ID_REUSED)
            }
        }

        var staleAttempts = 0
        while (staleAttempts < MAX_STALE_RETRIES) {
            currentCoroutineContext().ensureActive()
            val snapshot =
                repository.readSnapshot(mediaId)
                    ?: return ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.MISSING_ENTRY)
            val entry = snapshot.entry
            if (entry.status !in setOf(CollectionStatus.CURRENT, CollectionStatus.COMPLETED)) {
                return ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.INELIGIBLE)
            }
            if (entry.total?.value?.let { total -> targetProgress > total } == true) {
                return ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.AT_BOUNDARY)
            }
            if (entry.progress.value == targetProgress) {
                return ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.INVALID_REQUEST)
            }

            val status =
                if (entry.total?.value == targetProgress) {
                    CollectionStatus.COMPLETED
                } else {
                    CollectionStatus.CURRENT
                }
            val result =
                repository.commit(
                    ProgressChange(
                        actionId = actionId,
                        before = snapshot,
                        direction = ProgressDirection.CORRECTION,
                        targetProgress = ProgressValue(targetProgress),
                        targetStatus = status,
                        undoWindow = undoWindow,
                    ),
                )
            if (result !is ProgressWriteResult.Stale) return result
            staleAttempts++
        }
        return ProgressWriteResult.Stale
    }

    private fun isUsableUndoWindow(window: Duration): Boolean {
        if (!window.isFinite() || window < MIN_UNDO_WINDOW) return false
        return try {
            clock.now() + window
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private companion object {
        const val MAX_STALE_RETRIES: Int = 3
        val MIN_UNDO_WINDOW: Duration = 6.seconds
    }
}
