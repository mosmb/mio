package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import kotlin.time.Clock
import kotlin.time.Instant

public class ResolveProgressConflictUseCase(
    private val repository: SyncRecoveryRepository,
    private val clock: Clock,
) {
    public suspend operator fun invoke(
        expectedIssue: SyncIssue,
        choice: ProgressConflictChoice,
        groupId: OperationGroupId,
    ): Boolean {
        val issue = repository.readIssue(expectedIssue.operationId) ?: return false
        if (issue != expectedIssue || !issue.isProgressConflict()) return false
        val remote = requireNotNull(issue.remote)
        val localValue: OperationValue =
            if (issue.base is OperationValue.Status) {
                OperationValue.Status(issue.local.status)
            } else {
                OperationValue.Progress(issue.local.progress)
            }
        val remoteValue: OperationValue =
            if (issue.base is OperationValue.Status) {
                OperationValue.Status(remote.status)
            } else {
                OperationValue.Progress(remote.progress)
            }
        val (selectedBase, target) =
            when (choice) {
                ProgressConflictChoice.KEEP_DEVICE -> remoteValue to localValue
                ProgressConflictChoice.USE_PROVIDER -> localValue to remoteValue
            }
        val base =
            if (selectedBase != target) {
                selectedBase
            } else {
                // The two sources may converge while a persisted issue is still awaiting acknowledgement.
                if (issue.base != target) issue.base else issue.target
            }
        if (base == target) return false

        val suffix = if (base is OperationValue.Status) "status" else "progress"
        val operationId = OperationId("${groupId.value}:$suffix")
        val progressOperation =
            PendingOperation(
                id = operationId,
                groupId = groupId,
                accountId = issue.accountId,
                collectionEntryId = issue.entryId,
                kind =
                    if (base is OperationValue.Status) {
                        PendingOperationKind.SET_STATUS_CORRECTION
                    } else {
                        PendingOperationKind.SET_PROGRESS_CORRECTION
                    },
                base = base,
                target = target,
                positionInGroup = 0,
            )
        val resolution =
            PendingOperationGroup(
                id = groupId,
                accountId = issue.accountId,
                createdAt = clock.now().let { Instant.fromEpochMilliseconds(it.toEpochMilliseconds()) },
                operations =
                    buildList {
                        add(progressOperation)
                        if (base is OperationValue.Progress &&
                            choice == ProgressConflictChoice.KEEP_DEVICE &&
                            issue.local.status != remote.status
                        ) {
                            add(
                                PendingOperation(
                                    id = OperationId("${groupId.value}:status"),
                                    groupId = groupId,
                                    accountId = issue.accountId,
                                    collectionEntryId = issue.entryId,
                                    kind = PendingOperationKind.SET_STATUS_CORRECTION,
                                    base = OperationValue.Status(remote.status),
                                    target = OperationValue.Status(issue.local.status),
                                    positionInGroup = 1,
                                    dependsOn = setOf(operationId),
                                ),
                            )
                        }
                    },
            )
        return repository.resolveConflict(issue, resolution)
    }

    private fun SyncIssue.isProgressConflict(): Boolean =
        kind == SyncIssueKind.CONFLICT &&
            remote != null &&
            local.unit == remote.unit &&
            (
                (base is OperationValue.Progress && target is OperationValue.Progress) ||
                    (base is OperationValue.Status && target is OperationValue.Status)
            )
}
