package io.morgan.mio.domain.sync

import io.morgan.mio.domain.collection.CollectionStatus

public class ReconcilePendingOperationUseCase {
    public operator fun invoke(input: SyncReconciliationInput): SyncReconciliationDecision {
        val operation = input.candidate.operation
        val remote = input.remote
        if (remote.accountId != input.candidate.current.accountId || remote.entryId != operation.collectionEntryId) {
            return SyncReconciliationDecision.Rejected
        }
        if (remote.unit != input.candidate.current.unit) return SyncReconciliationDecision.Rejected

        return when (operation.kind) {
            PendingOperationKind.SET_PROGRESS_FORWARD -> reconcileForward(operation, remote)
            PendingOperationKind.SET_PROGRESS_CORRECTION -> reconcileCorrection(operation, remote)
            PendingOperationKind.SET_STATUS -> reconcileStatus(input.candidate, remote)
        }
    }

    private fun reconcileForward(
        operation: PendingOperation,
        remote: SyncEntryState,
    ): SyncReconciliationDecision {
        (operation.base as? OperationValue.Progress)?.value ?: return SyncReconciliationDecision.Rejected
        val target = (operation.target as? OperationValue.Progress)?.value ?: return SyncReconciliationDecision.Rejected
        return if (remote.progress.value >= target.value) {
            SyncReconciliationDecision.Satisfied
        } else {
            SyncReconciliationDecision.Write(OperationValue.Progress(target))
        }
    }

    private fun reconcileCorrection(
        operation: PendingOperation,
        remote: SyncEntryState,
    ): SyncReconciliationDecision {
        val base = (operation.base as? OperationValue.Progress)?.value ?: return SyncReconciliationDecision.Rejected
        val target = (operation.target as? OperationValue.Progress)?.value ?: return SyncReconciliationDecision.Rejected
        return when {
            remote.progress == target -> SyncReconciliationDecision.Satisfied
            remote.progress == base -> SyncReconciliationDecision.Write(OperationValue.Progress(target))
            else -> SyncReconciliationDecision.Conflict
        }
    }

    private fun reconcileStatus(
        candidate: SyncCandidate,
        remote: SyncEntryState,
    ): SyncReconciliationDecision {
        val base =
            (candidate.operation.base as? OperationValue.Status)?.value
                ?: return SyncReconciliationDecision.Rejected
        val target =
            (candidate.operation.target as? OperationValue.Status)?.value
                ?: return SyncReconciliationDecision.Rejected
        val isCompensation = candidate.compensatesGroupId != null
        if (isCompensation) {
            if (base != CollectionStatus.COMPLETED || target != CollectionStatus.CURRENT) {
                return SyncReconciliationDecision.Rejected
            }
            return when {
                remote.status == target -> SyncReconciliationDecision.Satisfied
                remote.status == base -> SyncReconciliationDecision.Write(OperationValue.Status(target))
                else -> SyncReconciliationDecision.Conflict
            }
        }
        if (!candidate.isAutomaticCompletion ||
            base != CollectionStatus.CURRENT ||
            target != CollectionStatus.COMPLETED
        ) {
            return SyncReconciliationDecision.Rejected
        }
        val total = remote.total
        if (total != null && remote.progress.value < total.value) return SyncReconciliationDecision.Conflict
        if (remote.status == target) return SyncReconciliationDecision.Satisfied
        return if (remote.status == base) {
            SyncReconciliationDecision.Write(OperationValue.Status(target))
        } else {
            SyncReconciliationDecision.Conflict
        }
    }
}
