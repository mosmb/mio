package io.morgan.mio.data.sync.local

import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.data.sync.mapper.toDomain
import io.morgan.mio.data.sync.mapper.toPendingOperationDependencyEntities
import io.morgan.mio.data.sync.mapper.toPendingOperationEntity
import io.morgan.mio.data.sync.mapper.toPendingOperationGroupEntity
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
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
            signalDispatch(validatedGroup.accountId.value)
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

    internal suspend fun undo(
        accountId: AccountId,
        groupId: OperationGroupId,
        requestedCompensation: PendingOperationGroup,
    ): Boolean =
        database.inTransaction {
            val group = database.pendingOperationDao().getGroup(groupId.value) ?: return@inTransaction false
            if (group.accountId != accountId.value || group.compensatesGroupId != null) {
                return@inTransaction false
            }
            val operationEntities = database.pendingOperationDao().getOperationsForGroup(groupId.value)
            if (operationEntities.isEmpty()) return@inTransaction false
            val operations =
                operationEntities.map { entity ->
                    entity.toDomain(
                        database
                            .pendingOperationDao()
                            .getDependenciesForOperation(entity.operationId)
                            .mapTo(mutableSetOf()) { dependency ->
                                io.morgan.mio.domain.model
                                    .OperationId(dependency.dependsOnOperationId)
                            },
                    )
                }
            val original = group.toDomain(operations)
            val decision =
                io.morgan.mio.domain.sync
                    .PlanOperationUndoUseCase()(operations)
            val expectedGroupId = OperationGroupId("${groupId.value}:undo")
            if (requestedCompensation.id != expectedGroupId ||
                requestedCompensation.accountId != accountId ||
                requestedCompensation.compensatesGroupId != groupId
            ) {
                return@inTransaction false
            }
            if (database.pendingOperationDao().getGroup(expectedGroupId.value) != null) return@inTransaction false

            when (decision) {
                io.morgan.mio.domain.sync.SyncUndoDecision.Unavailable -> return@inTransaction false
                io.morgan.mio.domain.sync.SyncUndoDecision.Cancel -> {
                    val expected =
                        original.toCompensation(
                            requested = requestedCompensation,
                            decision =
                                io.morgan.mio.domain.sync.SyncUndoDecision.Compensate(
                                    cancelMemberIds = emptySet(),
                                    waitForMemberIds = emptySet(),
                                ),
                        )
                    if (!sameCompensationShape(requestedCompensation, expected)) return@inTransaction false
                    if (database.pendingOperationDao().countExternalDependents(groupId.value) != 0) {
                        return@inTransaction false
                    }
                    if (database.pendingOperationDao().deleteGroup(accountId.value, groupId.value) != 1) {
                        return@inTransaction false
                    }
                    true
                }
                is io.morgan.mio.domain.sync.SyncUndoDecision.Compensate -> {
                    val expected = original.toCompensation(requestedCompensation, decision)
                    if (!sameCompensationShape(requestedCompensation, expected)) return@inTransaction false
                    decision.cancelMemberIds.forEach { operationId ->
                        if (database.syncDao().cancelOperation(accountId.value, operationId.value) != 1) {
                            return@inTransaction false
                        }
                    }
                    val groupEntity = expected.toPendingOperationGroupEntity()
                    val operationEntitiesToInsert =
                        expected.operations
                            .sortedBy { operation -> operation.positionInGroup }
                            .map { operation -> operation.toPendingOperationEntity() }
                    val dependencyEntities =
                        expected.operations.flatMap { operation -> operation.toPendingOperationDependencyEntities() }
                    check(database.pendingOperationDao().insertGroup(groupEntity) > 0L)
                    database.pendingOperationDao().insertOperations(operationEntitiesToInsert)
                    if (dependencyEntities.isNotEmpty()) {
                        database.pendingOperationDao().insertDependencies(dependencyEntities)
                    }
                    signalDispatch(accountId.value)
                    true
                }
            }
        }

    private fun PendingOperationGroup.toCompensation(
        requested: PendingOperationGroup,
        decision: io.morgan.mio.domain.sync.SyncUndoDecision.Compensate,
    ): PendingOperationGroup {
        val originalOperations = operations.sortedBy { operation -> operation.positionInGroup }
        val activeDependencies =
            decision.waitForMemberIds
                .mapTo(linkedSetOf()) { operationId ->
                    io.morgan.mio.domain.model
                        .OperationId(operationId.value)
                }
        val progressId = OperationGroupId("${id.value}:undo")
        val progress =
            originalOperations.firstOrNull { operation ->
                operation.kind == io.morgan.mio.domain.sync.PendingOperationKind.SET_PROGRESS_FORWARD ||
                    operation.kind == io.morgan.mio.domain.sync.PendingOperationKind.SET_PROGRESS_CORRECTION
            }
        val membership =
            originalOperations.firstOrNull { operation ->
                operation.kind == io.morgan.mio.domain.sync.PendingOperationKind.CREATE_ENTRY ||
                    operation.kind == io.morgan.mio.domain.sync.PendingOperationKind.REMOVE_ENTRY
            }
        val inverseMembership =
            membership?.let {
                io.morgan.mio.domain.sync.PendingOperation(
                    id =
                        io.morgan.mio.domain.model
                            .OperationId("${id.value}:undo:membership"),
                    groupId = progressId,
                    accountId = accountId,
                    collectionEntryId = it.collectionEntryId,
                    kind =
                        if (it.kind == io.morgan.mio.domain.sync.PendingOperationKind.CREATE_ENTRY) {
                            io.morgan.mio.domain.sync.PendingOperationKind.REMOVE_ENTRY
                        } else {
                            io.morgan.mio.domain.sync.PendingOperationKind.CREATE_ENTRY
                        },
                    base = it.target,
                    target = it.base,
                    positionInGroup = 0,
                    dependsOn = activeDependencies,
                )
            }
        val inverseProgress =
            progress?.let {
                io.morgan.mio.domain.sync.PendingOperation(
                    id =
                        io.morgan.mio.domain.model
                            .OperationId("${id.value}:undo:progress"),
                    groupId = progressId,
                    accountId = accountId,
                    collectionEntryId = it.collectionEntryId,
                    kind = io.morgan.mio.domain.sync.PendingOperationKind.SET_PROGRESS_CORRECTION,
                    base = it.target,
                    target = it.base,
                    positionInGroup = if (inverseMembership != null) 1 else 0,
                    dependsOn = activeDependencies,
                )
            }
        val originalStatus =
            originalOperations.firstOrNull { operation ->
                operation.kind == io.morgan.mio.domain.sync.PendingOperationKind.SET_STATUS
            }
        val inverseOperations =
            buildList {
                inverseMembership?.let(::add)
                inverseProgress?.let(::add)
                if (originalStatus != null) {
                    add(
                        io.morgan.mio.domain.sync.PendingOperation(
                            id =
                                io.morgan.mio.domain.model
                                    .OperationId("${id.value}:undo:status"),
                            groupId = progressId,
                            accountId = accountId,
                            collectionEntryId = originalStatus.collectionEntryId,
                            kind = io.morgan.mio.domain.sync.PendingOperationKind.SET_STATUS,
                            base = originalStatus.target,
                            target = originalStatus.base,
                            positionInGroup =
                                listOfNotNull(inverseMembership, inverseProgress).size,
                            dependsOn =
                                activeDependencies +
                                    listOfNotNull(inverseMembership?.id, inverseProgress?.id),
                        ),
                    )
                }
            }
        if (inverseOperations.isEmpty()) return requested
        return PendingOperationGroup(
            id = progressId,
            accountId = accountId,
            createdAt = requested.createdAt,
            operations = inverseOperations,
            compensatesGroupId = id,
        )
    }

    private suspend fun signalDispatch(accountId: String) {
        val before = database.syncDao().getSchedule(accountId)?.requestedGeneration
        database.syncDao().requestDispatch(accountId)
        val after = database.syncDao().getSchedule(accountId)?.requestedGeneration
        check(after != null && (before == null && after == 1L || before != null && after == before + 1L)) {
            "Pending sync dispatch generation overflowed"
        }
    }

    private fun sameCompensationShape(
        requested: PendingOperationGroup,
        expected: PendingOperationGroup,
    ): Boolean {
        if (requested.operations.size != expected.operations.size) return false
        return requested.operations
            .sortedBy { operation -> operation.positionInGroup }
            .zip(expected.operations.sortedBy { operation -> operation.positionInGroup })
            .all { (actual, required) ->
                actual.id == required.id &&
                    actual.groupId == required.groupId &&
                    actual.accountId == required.accountId &&
                    actual.collectionEntryId == required.collectionEntryId &&
                    actual.kind == required.kind &&
                    actual.base == required.base &&
                    actual.target == required.target &&
                    actual.positionInGroup == required.positionInGroup &&
                    actual.dependsOn ==
                    required.dependsOn.filterTo(linkedSetOf()) { dependency ->
                        expected.operations.any { member -> member.id == dependency }
                    } &&
                    actual.state == PendingOperationState.ENQUEUED &&
                    actual.attemptCount == 0 &&
                    actual.lastAttemptAt == null &&
                    actual.retryNotBefore == null
            }
    }
}
