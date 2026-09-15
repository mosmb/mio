package io.morgan.mio.domain.sync

import io.morgan.mio.domain.model.OperationGroupId

public data class SyncCandidate(
    public val operation: PendingOperation,
    public val current: SyncEntryState,
    public val groupOrder: Long,
    public val isAutomaticCompletion: Boolean = false,
    public val compensatesGroupId: OperationGroupId? = null,
    public val attemptBudgetExhausted: Boolean = false,
) {
    init {
        require(groupOrder >= 0L)
        require(operation.accountId == current.accountId)
        require(operation.collectionEntryId == current.entryId)
        require(compensatesGroupId == null || compensatesGroupId != operation.groupId)
    }
}
