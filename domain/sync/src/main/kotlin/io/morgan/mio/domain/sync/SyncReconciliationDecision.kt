package io.morgan.mio.domain.sync

public sealed interface SyncReconciliationDecision {
    public data object Satisfied : SyncReconciliationDecision

    public data class Write(
        public val value: OperationValue,
    ) : SyncReconciliationDecision

    public data object Conflict : SyncReconciliationDecision

    public data object Rejected : SyncReconciliationDecision
}
