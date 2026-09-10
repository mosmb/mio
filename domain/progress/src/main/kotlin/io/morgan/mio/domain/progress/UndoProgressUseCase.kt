package io.morgan.mio.domain.progress

import io.morgan.mio.domain.model.OperationGroupId
import kotlin.time.Clock

public class UndoProgressUseCase(
    private val repository: ProgressRepository,
    private val clock: Clock,
) {
    public suspend operator fun invoke(groupId: OperationGroupId): ProgressWriteResult {
        val action =
            repository.readLatestAction()
                ?: return unavailable()
        if (action.id != groupId || action.undoneAt != null) return unavailable()

        val now = clock.now()
        if (now >= action.undoUntil) return unavailable()

        val current =
            repository.readSnapshot(action.mediaId)
                ?: return unavailable()
        if (!matchesTarget(action, current)) return unavailable()

        val requestedAt = clock.now()
        if (requestedAt >= action.undoUntil) return unavailable()
        return repository.undo(
            ProgressUndo(
                action = action,
                current = current,
                requestedAt = requestedAt,
            ),
        )
    }

    private fun matchesTarget(
        action: ProgressAction,
        current: ProgressSnapshot,
    ): Boolean {
        val entry = current.entry
        return current.revision == action.afterRevision &&
            entry.id == action.entryId &&
            entry.accountId == action.accountId &&
            entry.mediaId == action.mediaId &&
            entry.progressUnit == action.unit &&
            entry.progress == action.afterProgress &&
            entry.status == action.afterStatus &&
            entry.updatedAt == action.afterUpdatedAt
    }

    private fun unavailable(): ProgressWriteResult =
        ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.UNDO_UNAVAILABLE)
}
