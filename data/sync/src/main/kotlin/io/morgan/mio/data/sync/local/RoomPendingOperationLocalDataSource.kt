package io.morgan.mio.data.sync.local

import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.data.sync.mapper.toPendingOperationDependencyEntities
import io.morgan.mio.data.sync.mapper.toPendingOperationEntity
import io.morgan.mio.data.sync.mapper.toPendingOperationGroupEntity
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
}
