package io.morgan.mio.domain.sync

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressValue
import kotlin.time.Instant

public sealed interface OperationValue {
    public data class Progress(
        public val value: ProgressValue,
    ) : OperationValue

    public data class Status(
        public val value: CollectionStatus,
    ) : OperationValue
}

public class PendingOperation(
    public val id: OperationId,
    public val groupId: OperationGroupId,
    public val accountId: AccountId,
    public val collectionEntryId: CollectionEntryId,
    public val kind: PendingOperationKind,
    public val base: OperationValue,
    public val target: OperationValue,
    public val positionInGroup: Int,
    dependsOn: Set<OperationId> = emptySet(),
    public val state: PendingOperationState = PendingOperationState.ENQUEUED,
    public val attemptCount: Int = 0,
    public val lastAttemptAt: Instant? = null,
    public val retryNotBefore: Instant? = null,
) {
    public val dependsOn: Set<OperationId> = dependsOn.toSet()

    init {
        require(positionInGroup >= 0)
        require(attemptCount >= 0)
        require(id !in this.dependsOn)

        when (kind) {
            PendingOperationKind.SET_PROGRESS_FORWARD -> {
                require(base is OperationValue.Progress && target is OperationValue.Progress)
                require(target.value.value > base.value.value)
            }

            PendingOperationKind.SET_PROGRESS_CORRECTION -> {
                require(base is OperationValue.Progress && target is OperationValue.Progress)
                require(target.value != base.value)
            }

            PendingOperationKind.SET_STATUS,
            PendingOperationKind.SET_STATUS_CORRECTION,
            -> {
                require(base is OperationValue.Status && target is OperationValue.Status)
                require(target.value != base.value)
            }
        }

        when (state) {
            PendingOperationState.ENQUEUED -> {
                require(attemptCount == 0)
                require(lastAttemptAt == null)
                require(retryNotBefore == null)
            }

            PendingOperationState.IN_FLIGHT,
            PendingOperationState.DELIVERED,
            -> {
                require(attemptCount > 0)
                require(lastAttemptAt != null)
            }

            PendingOperationState.RETRYABLE_FAILURE -> {
                require(attemptCount > 0)
                require(lastAttemptAt != null)
                require(retryNotBefore != null)
            }

            PendingOperationState.BLOCKED,
            PendingOperationState.TERMINAL_FAILURE,
            -> {
                require(attemptCount > 0)
                require(lastAttemptAt != null)
            }

            PendingOperationState.CANCELLED -> {
                require(retryNotBefore == null)
                if (attemptCount == 0) {
                    require(lastAttemptAt == null)
                } else {
                    require(lastAttemptAt != null)
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PendingOperation &&
                    id == other.id &&
                    groupId == other.groupId &&
                    accountId == other.accountId &&
                    collectionEntryId == other.collectionEntryId &&
                    kind == other.kind &&
                    base == other.base &&
                    target == other.target &&
                    positionInGroup == other.positionInGroup &&
                    dependsOn == other.dependsOn &&
                    state == other.state &&
                    attemptCount == other.attemptCount &&
                    lastAttemptAt == other.lastAttemptAt &&
                    retryNotBefore == other.retryNotBefore
            )

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + groupId.hashCode()
        result = 31 * result + accountId.hashCode()
        result = 31 * result + collectionEntryId.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + base.hashCode()
        result = 31 * result + target.hashCode()
        result = 31 * result + positionInGroup
        result = 31 * result + dependsOn.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + attemptCount
        result = 31 * result + (lastAttemptAt?.hashCode() ?: 0)
        result = 31 * result + (retryNotBefore?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "PendingOperation(id=$id, groupId=$groupId, accountId=$accountId, " +
            "collectionEntryId=$collectionEntryId, kind=$kind, base=$base, target=$target, " +
            "positionInGroup=$positionInGroup, dependsOn=$dependsOn, state=$state, " +
            "attemptCount=$attemptCount, lastAttemptAt=$lastAttemptAt, retryNotBefore=$retryNotBefore)"
}

public class PendingOperationGroup(
    public val id: OperationGroupId,
    public val accountId: AccountId,
    public val createdAt: Instant,
    operations: List<PendingOperation>,
    public val compensatesGroupId: OperationGroupId? = null,
) {
    public val operations: List<PendingOperation> = operations.toList()

    init {
        require(this.operations.isNotEmpty())
        require(this.operations.all { operation -> operation.groupId == id })
        require(this.operations.all { operation -> operation.accountId == accountId })
        require(
            this.operations
                .map { operation -> operation.id }
                .distinct()
                .size == this.operations.size,
        )

        val sortedPositions = this.operations.map { operation -> operation.positionInGroup }.sorted()
        require(sortedPositions == (0 until this.operations.size).toList())

        val positionsById = this.operations.associate { operation -> operation.id to operation.positionInGroup }
        this.operations.forEach { operation ->
            operation.dependsOn.forEach { dependencyId ->
                val dependencyPosition = positionsById[dependencyId]
                if (dependencyPosition != null) {
                    require(dependencyPosition < operation.positionInGroup)
                }
            }
        }

        require(compensatesGroupId != id)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PendingOperationGroup &&
                    id == other.id &&
                    accountId == other.accountId &&
                    createdAt == other.createdAt &&
                    operations == other.operations &&
                    compensatesGroupId == other.compensatesGroupId
            )

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + accountId.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + operations.hashCode()
        result = 31 * result + (compensatesGroupId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "PendingOperationGroup(id=$id, accountId=$accountId, createdAt=$createdAt, " +
            "operations=$operations, compensatesGroupId=$compensatesGroupId)"
}
