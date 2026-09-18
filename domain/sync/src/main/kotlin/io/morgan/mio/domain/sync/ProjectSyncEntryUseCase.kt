package io.morgan.mio.domain.sync

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.ProgressValue

public class ProjectSyncEntryUseCase {
    public operator fun invoke(
        remote: SyncEntryState,
        current: SyncEntryState,
        pending: List<PendingOperation>,
    ): SyncEntryState {
        require(remote.accountId == current.accountId)
        require(remote.entryId == current.entryId)
        require(remote.unit == current.unit)

        val ordered =
            pending.filter { operation ->
                require(operation.accountId == current.accountId)
                require(operation.collectionEntryId == current.entryId)
                operation.state != PendingOperationState.DELIVERED &&
                    operation.state != PendingOperationState.CANCELLED
            }
        val hasExplicitProgressCorrection =
            ordered.any { operation -> operation.kind == PendingOperationKind.SET_PROGRESS_CORRECTION }
        var progress =
            if (hasExplicitProgressCorrection) {
                remote.progress.value
            } else {
                maxOf(
                    remote.progress.value,
                    current.progress.value,
                )
            }
        var status = if (remote.status == CollectionStatus.COMPLETED) CollectionStatus.COMPLETED else remote.status
        var total = remote.total ?: current.total

        ordered.forEach { operation ->
            when (operation.kind) {
                PendingOperationKind.SET_PROGRESS_FORWARD -> {
                    val target = (operation.target as OperationValue.Progress).value.value
                    progress = maxOf(progress, target)
                }

                PendingOperationKind.SET_PROGRESS_CORRECTION -> {
                    progress = (operation.target as OperationValue.Progress).value.value
                }

                PendingOperationKind.SET_STATUS,
                PendingOperationKind.SET_STATUS_CORRECTION,
                -> {
                    status = (operation.target as OperationValue.Status).value
                }
            }
        }

        val effectiveChanged =
            progress != current.progress.value ||
                status != current.status ||
                total != current.total
        val revision =
            if (effectiveChanged) {
                require(current.revision < Long.MAX_VALUE)
                current.revision + 1L
            } else {
                current.revision
            }
        val updatedAt =
            if (effectiveChanged && ordered.isEmpty()) remote.updatedAt else current.updatedAt

        return current.copy(
            progress = ProgressValue(progress),
            status = status,
            total = total,
            updatedAt = updatedAt,
            revision = revision,
        )
    }
}
