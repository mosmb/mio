package io.morgan.mio.domain.progress

import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public class StepProgressUseCase(
    private val repository: ProgressRepository,
    private val clock: Clock,
) {
    public suspend operator fun invoke(
        mediaId: MediaId,
        direction: ProgressDirection,
        actionId: OperationGroupId,
        undoWindow: Duration,
    ): ProgressWriteResult {
        var staleAttempts = 0
        while (staleAttempts < MAX_STALE_RETRIES) {
            currentCoroutineContext().ensureActive()

            val existing = repository.findAction(actionId)
            if (existing != null) {
                return existingResult(existing, mediaId, direction)
            }

            if (!isUsableUndoWindow(undoWindow)) {
                return ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.INVALID_REQUEST)
            }

            val snapshot =
                repository.readSnapshot(mediaId)
                    ?: return ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.MISSING_ENTRY)
            when (val decision = ProgressRules.decide(snapshot.entry, direction)) {
                is ProgressRules.Decision.Rejected ->
                    return ProgressWriteResult.Rejected(decision.reason)
                is ProgressRules.Decision.Change -> {
                    val result =
                        repository.commit(
                            ProgressChange(
                                actionId = actionId,
                                before = snapshot,
                                direction = direction,
                                targetProgress = decision.progress,
                                targetStatus = decision.status,
                                undoWindow = undoWindow,
                            ),
                        )
                    if (result !is ProgressWriteResult.Stale) return result
                }
            }
            staleAttempts++
        }
        return ProgressWriteResult.Stale
    }

    private fun existingResult(
        action: ProgressAction,
        mediaId: MediaId,
        direction: ProgressDirection,
    ): ProgressWriteResult =
        if (action.mediaId == mediaId && action.direction == direction) {
            ProgressWriteResult.AlreadyHandled(action)
        } else {
            ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.ACTION_ID_REUSED)
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
