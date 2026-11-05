package io.morgan.mio.data.collection.local

import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.LocalCollectionActivityEntity
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.data.collection.mapper.CollectionEntryPersistenceSnapshot
import io.morgan.mio.data.collection.mapper.toCollectionAction
import io.morgan.mio.data.collection.mapper.toEffectiveDomain
import io.morgan.mio.data.collection.mapper.toEntity
import io.morgan.mio.data.collection.mapper.toInitialEntity
import io.morgan.mio.data.collection.mapper.toStoredStatus
import io.morgan.mio.data.collection.mapper.toStoredUnit
import io.morgan.mio.data.sync.PendingOperationWriter
import io.morgan.mio.domain.collection.CollectionAction
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.collection.CollectionStatusChange
import io.morgan.mio.domain.collection.CollectionWriteResult
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.PendingOperation
import io.morgan.mio.domain.sync.PendingOperationGroup
import io.morgan.mio.domain.sync.PendingOperationKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.ZERO

@Inject
public class RoomCollectionLocalDataSource(
    private val database: MioDatabase,
    private val clock: Clock,
) {
    public fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>> =
        database.collectionEntryDao().observeCollection(accountId.value).map { entries ->
            entries.map { entry -> entry.toEffectiveDomain() }.toList()
        }

    public fun observeEntry(entryId: CollectionEntryId): Flow<CollectionEntry?> =
        database.collectionEntryDao().observeEntry(entryId.value).map { entry -> entry?.toEffectiveDomain() }

    public fun observeEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<CollectionEntry?> =
        database
            .collectionEntryDao()
            .observeEntryForMedia(accountId.value, mediaId.value)
            .map { entry -> entry?.toEffectiveDomain() }

    public suspend fun readEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): CollectionEntry? =
        database
            .collectionEntryDao()
            .getEntryForMedia(accountId.value, mediaId.value)
            ?.takeIf { entry -> entry.effectivePresent }
            ?.toEffectiveDomain()

    public fun observeWritableStatuses(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<List<io.morgan.mio.domain.collection.CollectionStatus>> =
        database
            .selectedAccountDao()
            .observeSelectedAccount()
            .map { selected ->
                val statuses =
                    io.morgan.mio.domain.collection.CollectionStatus.entries
                        .toList()
                if (selected?.connectedProvider == StoredConnectedProvider.MY_ANIME_LIST) {
                    statuses.filterNot { status ->
                        status == io.morgan.mio.domain.collection.CollectionStatus.REPEATING
                    }
                } else {
                    statuses
                }
            }

    public suspend fun findAction(
        accountId: AccountId,
        actionId: OperationGroupId,
    ): CollectionAction? =
        database
            .collectionActivityDao()
            .get(accountId = accountId.value, groupId = actionId.value)
            ?.toCollectionAction()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    public fun observeLatestAction(accountId: AccountId): Flow<CollectionAction?> =
        database
            .collectionActivityDao()
            .observeLatest(accountId.value, clock.now())
            .flatMapLatest { activity ->
                flow {
                    val action = activity?.toCollectionAction()
                    if (action == null) {
                        emit(null)
                        return@flow
                    }
                    val remaining = action.undoUntil - clock.now()
                    if (remaining <= ZERO) {
                        emit(null)
                        return@flow
                    }
                    emit(action)
                    delay(remaining)
                    emit(null)
                }
            }

    public suspend fun readLatestAction(accountId: AccountId): CollectionAction? =
        database.collectionActivityDao().getLatest(accountId.value, clock.now())?.toCollectionAction()

    public suspend fun commitStatus(
        change: CollectionStatusChange,
        pendingOperationWriter: PendingOperationWriter,
    ): CollectionWriteResult =
        database.inTransaction {
            val existingAction = database.collectionActivityDao().get(change.accountId.value, change.actionId.value)
            if (existingAction != null) {
                return@inTransaction if (existingAction.mediaId == change.mediaId.value &&
                    existingAction.afterStatus == change.targetStatus.toStoredStatus() &&
                    existingAction.afterProgress == change.targetProgress.value &&
                    existingAction.progressUnit == change.progressUnit.toStoredUnit() &&
                    existingAction.afterTotal == change.targetTotal?.value
                ) {
                    CollectionWriteResult.AlreadyHandled(existingAction.toCollectionAction())
                } else {
                    CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.ACTION_ID_REUSED)
                }
            }
            val selectedAccount = database.syncDao().getSelectedAccount()
            if (selectedAccount?.accountId != change.accountId.value) {
                return@inTransaction CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.MISSING_ENTRY)
            }
            if (
                selectedAccount.connectedProvider == StoredConnectedProvider.MY_ANIME_LIST &&
                change.targetStatus == CollectionStatus.REPEATING
            ) {
                return@inTransaction CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.UNSUPPORTED_STATUS)
            }
            val actual = database.collectionEntryDao().getEntryForMedia(change.accountId.value, change.mediaId.value)
            if (actual?.effectivePresent == true && actual.toEffectiveDomain() != change.before) {
                return@inTransaction CollectionWriteResult.Stale
            }
            if (actual?.effectivePresent != true &&
                change.before != null
            ) {
                return@inTransaction CollectionWriteResult.Stale
            }

            val now = change.createdAt
            val beforeRevision = actual?.localProgressRevision
            val afterRevision =
                if (actual == null) {
                    0L
                } else {
                    actual.localProgressRevision.takeIf { it < Long.MAX_VALUE }?.plus(1L)
                        ?: return@inTransaction CollectionWriteResult.Rejected(
                            CollectionWriteResult.Rejection.INVALID_REQUEST,
                        )
                }
            val entryId = actual?.collectionEntryId ?: "local-${UUID.randomUUID()}"
            val after =
                CollectionEntry(
                    id = CollectionEntryId(entryId),
                    accountId = change.accountId,
                    mediaId = change.mediaId,
                    status = change.targetStatus,
                    progress = change.targetProgress,
                    progressUnit = change.progressUnit,
                    total = change.targetTotal,
                    updatedAt = now,
                )
            val afterEntity =
                CollectionEntryEntity(
                    collectionEntryId = entryId,
                    accountId = change.accountId.value,
                    mediaId = change.mediaId.value,
                    progressUnit = change.progressUnit.toStoredUnit(),
                    remoteStatus = actual?.remoteStatus ?: change.targetStatus.toStoredStatus(),
                    remoteProgress = actual?.remoteProgress ?: change.targetProgress.value,
                    remoteTotal = actual?.remoteTotal,
                    remoteUpdatedAtUtcMs = actual?.remoteUpdatedAtUtcMs ?: now,
                    remotePresent = actual?.remotePresent ?: false,
                    effectiveStatus = change.targetStatus.toStoredStatus(),
                    effectiveProgress = change.targetProgress.value,
                    effectiveTotal = change.targetTotal?.value,
                    effectiveUpdatedAtUtcMs = now,
                    effectivePresent = true,
                    localProgressRevision = afterRevision,
                )
            if (actual == null) {
                database.collectionEntryDao().upsertEntries(listOf(afterEntity))
            } else {
                check(
                    database.collectionEntryDao().updateEffectiveState(
                        entryId = actual.collectionEntryId,
                        accountId = change.accountId.value,
                        mediaId = change.mediaId.value,
                        expectedRevision = actual.localProgressRevision,
                        expectedPresent = actual.effectivePresent,
                        expectedStatus = actual.effectiveStatus,
                        expectedProgress = actual.effectiveProgress,
                        expectedTotal = actual.effectiveTotal,
                        expectedUpdatedAtUtcMs = actual.effectiveUpdatedAtUtcMs,
                        targetPresent = true,
                        targetStatus = afterEntity.effectiveStatus,
                        targetProgress = afterEntity.effectiveProgress,
                        targetTotal = afterEntity.effectiveTotal,
                        targetUpdatedAtUtcMs = afterEntity.effectiveUpdatedAtUtcMs,
                        targetRevision = afterRevision,
                    ) == 1,
                )
            }
            val operations = buildOperations(change, actual, after)
            val action =
                LocalCollectionActivityEntity(
                    operationGroupId = change.actionId.value,
                    accountId = change.accountId.value,
                    collectionEntryId = entryId,
                    mediaId = change.mediaId.value,
                    progressUnit = change.progressUnit.toStoredUnit(),
                    actionKind = if (actual?.effectivePresent != true) "add" else "status",
                    beforePresent = actual?.effectivePresent == true,
                    beforeProgress = actual?.takeIf { it.effectivePresent }?.effectiveProgress,
                    beforeStatus = actual?.takeIf { it.effectivePresent }?.effectiveStatus,
                    beforeTotal = actual?.takeIf { it.effectivePresent }?.effectiveTotal,
                    beforeUpdatedAtUtcMs = actual?.takeIf { it.effectivePresent }?.effectiveUpdatedAtUtcMs,
                    beforeRevision = beforeRevision.takeIf { actual?.effectivePresent == true },
                    afterProgress = after.progress.value,
                    afterStatus = after.status.toStoredStatus(),
                    afterTotal = after.total?.value,
                    afterUpdatedAtUtcMs = after.updatedAt,
                    afterRevision = afterRevision,
                    createdAtUtcMs = change.createdAt,
                    undoUntilUtcMs = change.undoUntil,
                    undoneAtUtcMs = null,
                )
            database.collectionActivityDao().expireUndo(change.accountId.value, now)
            database.progressDao().expireUndo(change.accountId.value, now)
            database.collectionActivityDao().insert(action)
            pendingOperationWriter.enqueue(
                PendingOperationGroup(
                    id = change.actionId,
                    accountId = change.accountId,
                    createdAt = change.createdAt,
                    operations = operations,
                ),
            )
            CollectionWriteResult.Committed(action.toCollectionAction())
        }

    public suspend fun undo(
        accountId: AccountId,
        actionId: OperationGroupId,
        pendingOperationWriter: PendingOperationWriter,
    ): CollectionWriteResult =
        database.inTransaction {
            val activity =
                database.collectionActivityDao().get(accountId.value, actionId.value)
                    ?: return@inTransaction CollectionWriteResult.Rejected(
                        CollectionWriteResult.Rejection.UNDO_UNAVAILABLE,
                    )
            val action = activity.toCollectionAction()
            if (activity.undoneAtUtcMs != null || clock.now() >= activity.undoUntilUtcMs) {
                return@inTransaction CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.UNDO_UNAVAILABLE)
            }
            val actual =
                database.collectionEntryDao().getEntry(activity.collectionEntryId)
                    ?: return@inTransaction CollectionWriteResult.Rejected(
                        CollectionWriteResult.Rejection.UNDO_UNAVAILABLE,
                    )
            if (actual.accountId != accountId.value ||
                !actual.effectivePresent ||
                actual.localProgressRevision != activity.afterRevision ||
                actual.effectiveStatus != activity.afterStatus ||
                actual.effectiveProgress != activity.afterProgress ||
                actual.effectiveTotal != activity.afterTotal ||
                actual.effectiveUpdatedAtUtcMs != activity.afterUpdatedAtUtcMs ||
                database.collectionActivityDao().getLatest(accountId.value, clock.now())?.operationGroupId !=
                actionId.value ||
                actual.localProgressRevision == Long.MAX_VALUE
            ) {
                return@inTransaction CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.UNDO_UNAVAILABLE)
            }
            val operations = database.pendingOperationDao().getOperationsForGroup(actionId.value)
            if (operations.isEmpty()) {
                return@inTransaction CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.UNDO_UNAVAILABLE)
            }
            val compensation = buildCompensation(action, operations)
            if (!pendingOperationWriter.undo(actionId, compensation)) {
                return@inTransaction CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.UNDO_UNAVAILABLE)
            }
            val targetPresent = if (!activity.beforePresent) false else true
            val targetStatus = activity.beforeStatus ?: actual.effectiveStatus
            val targetProgress = activity.beforeProgress ?: actual.effectiveProgress
            val targetTotal = activity.beforeTotal
            val targetUpdatedAt = activity.beforeUpdatedAtUtcMs ?: actual.effectiveUpdatedAtUtcMs
            val targetRevision =
                actual.localProgressRevision.takeIf { it < Long.MAX_VALUE }?.plus(1L)
                    ?: return@inTransaction CollectionWriteResult.Rejected(
                        CollectionWriteResult.Rejection.UNDO_UNAVAILABLE,
                    )
            check(
                database.collectionEntryDao().updateEffectiveState(
                    entryId = actual.collectionEntryId,
                    accountId = accountId.value,
                    mediaId = actual.mediaId,
                    expectedRevision = actual.localProgressRevision,
                    expectedPresent = actual.effectivePresent,
                    expectedStatus = actual.effectiveStatus,
                    expectedProgress = actual.effectiveProgress,
                    expectedTotal = actual.effectiveTotal,
                    expectedUpdatedAtUtcMs = actual.effectiveUpdatedAtUtcMs,
                    targetPresent = targetPresent,
                    targetStatus = targetStatus,
                    targetProgress = targetProgress,
                    targetTotal = targetTotal,
                    targetUpdatedAtUtcMs = targetUpdatedAt,
                    targetRevision = targetRevision,
                ) == 1,
            )
            database.collectionActivityDao().markUndone(accountId.value, actionId.value, clock.now())
            CollectionWriteResult.Committed(action.copy(undoneAt = clock.now()))
        }

    internal suspend fun replaceInitialCollectionSnapshot(
        accountId: AccountId,
        input: List<CollectionEntryPersistenceSnapshot>,
        isComplete: Boolean,
    ) {
        val snapshots = input.toList()
        snapshots.forEach { snapshot ->
            require(snapshot.entry.accountId == accountId)
            require(snapshot.providerIds.all { mapping -> mapping.entryId == snapshot.entry.id })
        }
        val entryIds = snapshots.map { snapshot -> snapshot.entry.id }.distinct()
        require(entryIds.size == snapshots.size)
        val mediaIds = snapshots.map { snapshot -> snapshot.entry.mediaId }
        require(mediaIds.distinct().size == mediaIds.size)
        val entries = snapshots.map { snapshot -> snapshot.entry.toInitialEntity() }
        val providerMappings =
            snapshots
                .flatMap { snapshot -> snapshot.providerIds.toList() }
                .map { mapping -> mapping.toEntity() }

        database.inTransaction {
            if (isComplete) {
                if (entryIds.isEmpty()) {
                    database.collectionEntryDao().deleteAllForAccount(accountId.value)
                } else {
                    database.collectionEntryDao().deleteEntriesNotIn(
                        accountId = accountId.value,
                        entryIds = entryIds.map(CollectionEntryId::value),
                    )
                }
            }
            entryIds.forEach { entryId ->
                database.providerIdDao().deleteCollectionEntryProviderIds(entryId.value)
            }
            if (entries.isNotEmpty()) database.collectionEntryDao().upsertEntries(entries)
            if (providerMappings.isNotEmpty()) {
                database.providerIdDao().upsertCollectionEntryProviderIds(providerMappings)
            }
        }
    }

    private fun buildOperations(
        change: CollectionStatusChange,
        actual: CollectionEntryEntity?,
        after: CollectionEntry,
    ): List<PendingOperation> {
        val operations = mutableListOf<PendingOperation>()
        if (actual?.effectivePresent != true) {
            operations +=
                PendingOperation(
                    id =
                        io.morgan.mio.domain.model
                            .OperationId("${change.actionId.value}:create"),
                    groupId = change.actionId,
                    accountId = change.accountId,
                    collectionEntryId = after.id,
                    kind = PendingOperationKind.CREATE_ENTRY,
                    base = OperationValue.Membership(false),
                    target = OperationValue.Membership(true),
                    positionInGroup = 0,
                )
            return operations
        }
        val progressChanged =
            actual.effectiveProgress != after.progress.value
        if (progressChanged) {
            operations +=
                PendingOperation(
                    id =
                        io.morgan.mio.domain.model
                            .OperationId("${change.actionId.value}:progress"),
                    groupId = change.actionId,
                    accountId = change.accountId,
                    collectionEntryId = after.id,
                    kind = PendingOperationKind.SET_PROGRESS_CORRECTION,
                    base =
                        OperationValue.Progress(
                            io.morgan.mio.domain.model
                                .ProgressValue(actual.effectiveProgress),
                        ),
                    target = OperationValue.Progress(after.progress),
                    positionInGroup = 0,
                )
        }
        if (actual.effectiveStatus != after.status.toStoredStatus()) {
            operations +=
                PendingOperation(
                    id =
                        io.morgan.mio.domain.model
                            .OperationId("${change.actionId.value}:status"),
                    groupId = change.actionId,
                    accountId = change.accountId,
                    collectionEntryId = after.id,
                    kind = PendingOperationKind.SET_STATUS,
                    base = OperationValue.Status(actual.effectiveStatus.toDomainStatus()),
                    target = OperationValue.Status(after.status),
                    positionInGroup = operations.size,
                    dependsOn = operations.mapTo(linkedSetOf()) { operation -> operation.id },
                )
        }
        require(operations.isNotEmpty())
        return operations
    }

    private fun buildCompensation(
        action: CollectionAction,
        original: List<io.morgan.mio.core.database.entity.PendingOperationEntity>,
    ): PendingOperationGroup {
        val ordered = original.sortedBy { operation -> operation.positionInGroup }
        val membership =
            ordered.firstOrNull { operation ->
                operation.kind == io.morgan.mio.core.database.model.StoredPendingOperationKind.CREATE_ENTRY ||
                    operation.kind == io.morgan.mio.core.database.model.StoredPendingOperationKind.REMOVE_ENTRY
            }
        val progress =
            ordered.firstOrNull { operation ->
                operation.kind == io.morgan.mio.core.database.model.StoredPendingOperationKind.SET_PROGRESS_FORWARD ||
                    operation.kind ==
                    io.morgan.mio.core.database.model.StoredPendingOperationKind.SET_PROGRESS_CORRECTION
            }
        val status =
            ordered.firstOrNull { operation ->
                operation.kind == io.morgan.mio.core.database.model.StoredPendingOperationKind.SET_STATUS
            }
        val groupId = OperationGroupId("${action.id.value}:undo")
        val inverseMembership =
            membership?.let { operation ->
                val basePresent = requireNotNull(operation.targetPresent)
                val targetPresent = requireNotNull(operation.basePresent)
                PendingOperation(
                    id =
                        io.morgan.mio.domain.model
                            .OperationId("${action.id.value}:undo:membership"),
                    groupId = groupId,
                    accountId = action.accountId,
                    collectionEntryId = action.entryId,
                    kind =
                        if (operation.kind ==
                            io.morgan.mio.core.database.model.StoredPendingOperationKind.CREATE_ENTRY
                        ) {
                            PendingOperationKind.REMOVE_ENTRY
                        } else {
                            PendingOperationKind.CREATE_ENTRY
                        },
                    base = OperationValue.Membership(basePresent),
                    target = OperationValue.Membership(targetPresent),
                    positionInGroup = 0,
                    dependsOn = emptySet(),
                )
            }
        val inverseProgress =
            progress?.let { operation ->
                PendingOperation(
                    id =
                        io.morgan.mio.domain.model
                            .OperationId("${action.id.value}:undo:progress"),
                    groupId = groupId,
                    accountId = action.accountId,
                    collectionEntryId = action.entryId,
                    kind = PendingOperationKind.SET_PROGRESS_CORRECTION,
                    base =
                        OperationValue.Progress(
                            io.morgan.mio.domain.model
                                .ProgressValue(requireNotNull(operation.targetProgress)),
                        ),
                    target =
                        OperationValue.Progress(
                            io.morgan.mio.domain.model
                                .ProgressValue(requireNotNull(operation.baseProgress)),
                        ),
                    positionInGroup = if (inverseMembership != null) 1 else 0,
                    dependsOn = emptySet(),
                )
            }
        val inverseStatus =
            status?.let { operation ->
                PendingOperation(
                    id =
                        io.morgan.mio.domain.model
                            .OperationId("${action.id.value}:undo:status"),
                    groupId = groupId,
                    accountId = action.accountId,
                    collectionEntryId = action.entryId,
                    kind = PendingOperationKind.SET_STATUS,
                    base = OperationValue.Status(requireNotNull(operation.targetStatus).toDomainStatus()),
                    target = OperationValue.Status(requireNotNull(operation.baseStatus).toDomainStatus()),
                    positionInGroup = listOfNotNull(inverseMembership, inverseProgress).size,
                    dependsOn = listOfNotNull(inverseMembership?.id, inverseProgress?.id).toSet(),
                )
            }
        val operations = listOfNotNull(inverseMembership, inverseProgress, inverseStatus)
        return PendingOperationGroup(
            id = groupId,
            accountId = action.accountId,
            createdAt = clock.now(),
            operations = operations,
            compensatesGroupId = action.id,
        )
    }

    private fun io.morgan.mio.core.database.model.StoredCollectionStatus.toDomainStatus(): CollectionStatus =
        when (this) {
            io.morgan.mio.core.database.model.StoredCollectionStatus.CURRENT -> CollectionStatus.CURRENT
            io.morgan.mio.core.database.model.StoredCollectionStatus.PLANNED -> CollectionStatus.PLANNED
            io.morgan.mio.core.database.model.StoredCollectionStatus.PAUSED -> CollectionStatus.PAUSED
            io.morgan.mio.core.database.model.StoredCollectionStatus.COMPLETED -> CollectionStatus.COMPLETED
            io.morgan.mio.core.database.model.StoredCollectionStatus.DROPPED -> CollectionStatus.DROPPED
            io.morgan.mio.core.database.model.StoredCollectionStatus.REPEATING -> CollectionStatus.REPEATING
        }
}
