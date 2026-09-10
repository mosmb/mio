package io.morgan.mio.data.sync.local

import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.data.sync.mapper.toPendingOperationDependencyEntities
import io.morgan.mio.data.sync.mapper.toPendingOperationEntity
import io.morgan.mio.data.sync.mapper.toPendingOperationGroupEntity
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.sync.PendingOperationGroup
import io.morgan.mio.domain.sync.PendingOperationState

@Inject
public class RoomPendingOperationLocalDataSource(
    private val database: MioDatabase,
) {
    internal suspend fun enqueue(group: PendingOperationGroup) {
        val validatedGroup =
            PendingOperationGroup(
                id = group.id,
                accountId = group.accountId,
                createdAt = group.createdAt,
                operations = group.operations,
                compensatesGroupId = group.compensatesGroupId,
            )
        require(
            validatedGroup.operations.all { operation ->
                operation.state == PendingOperationState.ENQUEUED &&
                    operation.attemptCount == 0 &&
                    operation.lastAttemptAt == null &&
                    operation.retryNotBefore == null
            },
        )

        val orderedOperations = validatedGroup.operations.sortedBy { operation -> operation.positionInGroup }
        val groupEntity = validatedGroup.toPendingOperationGroupEntity()
        val operationEntities = orderedOperations.map { operation -> operation.toPendingOperationEntity() }
        val dependencyEntities =
            orderedOperations.flatMap { operation ->
                operation.toPendingOperationDependencyEntities()
            }
        val operationIds = orderedOperations.mapTo(mutableSetOf()) { operation -> operation.id.value }
        val entryIds = orderedOperations.map { operation -> operation.collectionEntryId.value }.distinct()
        val externalDependencyIds =
            orderedOperations
                .flatMap { operation -> operation.dependsOn.map { dependencyId -> dependencyId.value } }
                .filterNot(operationIds::contains)
                .distinct()

        database.inTransaction {
            entryIds.forEach { entryId ->
                val entry = requireNotNull(database.collectionEntryDao().getEntry(entryId))
                require(entry.accountId == validatedGroup.accountId.value)
            }

            externalDependencyIds.forEach { operationId ->
                val predecessor = requireNotNull(database.pendingOperationDao().getOperation(operationId))
                require(predecessor.accountId == validatedGroup.accountId.value)
            }

            require(database.pendingOperationDao().insertGroup(groupEntity) > 0L)
            database.pendingOperationDao().insertOperations(operationEntities)
            if (dependencyEntities.isNotEmpty()) {
                database.pendingOperationDao().insertDependencies(dependencyEntities)
            }
        }
    }

    internal suspend fun cancelUndelivered(
        accountId: AccountId,
        groupId: OperationGroupId,
    ): Boolean =
        database.inTransaction {
            val groupEntity = database.pendingOperationDao().getGroup(groupId.value) ?: return@inTransaction false
            if (groupEntity.accountId != accountId.value) return@inTransaction false

            val operations = database.pendingOperationDao().getOperationsForGroup(groupId.value)
            if (operations.isEmpty()) return@inTransaction false
            if (
                operations.any { operation ->
                    operation.accountId != accountId.value ||
                        operation.lifecycleState != StoredPendingOperationState.ENQUEUED ||
                        operation.attemptCount != 0 ||
                        operation.lastAttemptAtUtcMs != null ||
                        operation.retryNotBeforeUtcMs != null
                }
            ) {
                return@inTransaction false
            }

            for (operation in operations) {
                database
                    .pendingOperationDao()
                    .getDependenciesForOperation(operation.operationId)
                    .forEach { dependency ->
                        val predecessor =
                            database.pendingOperationDao().getOperation(dependency.dependsOnOperationId)
                                ?: return@inTransaction false
                        if (predecessor.accountId != accountId.value) return@inTransaction false
                    }
            }

            if (database.pendingOperationDao().countExternalDependents(groupId.value) != 0) {
                return@inTransaction false
            }

            database.pendingOperationDao().deleteGroup(accountId.value, groupId.value) == 1
        }
}
