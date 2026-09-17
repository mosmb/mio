package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.OperationId

public class RetrySyncIssueUseCase(
    private val repository: SyncRecoveryRepository,
) {
    public suspend operator fun invoke(operationId: OperationId): Boolean {
        val issue = repository.readIssue(operationId) ?: return false
        return when (issue.kind) {
            SyncIssueKind.REJECTED,
            SyncIssueKind.RETRY_EXHAUSTED,
            -> repository.rearm(issue)
            SyncIssueKind.AUTHENTICATION,
            SyncIssueKind.CONFLICT,
            SyncIssueKind.INVALID_REMOTE_DATA,
            -> false
        }
    }
}
