package io.morgan.mio.domain.sync

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class ReconcilePendingOperationUseCaseTest {
    private val now = Instant.parse("2026-09-11T10:00:00Z")
    private val reconcile = ReconcilePendingOperationUseCase()

    @Test
    fun forwardAcknowledgesAheadRemoteAndWritesTheOperationTargetWhenBehind() {
        val operation = progressOperation("forward", 4, 5, PendingOperationKind.SET_PROGRESS_FORWARD)
        val candidate = candidate(operation)

        assertEquals(
            SyncReconciliationDecision.Satisfied,
            reconcile(SyncReconciliationInput(candidate, remote(progress = 5))),
        )
        assertEquals(
            SyncReconciliationDecision.Satisfied,
            reconcile(SyncReconciliationInput(candidate, remote(progress = 7))),
        )
        assertEquals(
            SyncReconciliationDecision.Write(OperationValue.Progress(ProgressValue(5))),
            reconcile(SyncReconciliationInput(candidate, remote(progress = 3))),
        )
    }

    @Test
    fun correctionsRequireTheirRecordedBaseAndNeverUseAThirdRemoteValue() {
        ProgressUnit.entries.forEach { unit ->
            val operation = progressOperation("correction-$unit", 5, 4, PendingOperationKind.SET_PROGRESS_CORRECTION)
            val candidate = candidate(operation, unit = unit)

            assertEquals(
                SyncReconciliationDecision.Satisfied,
                reconcile(SyncReconciliationInput(candidate, remote(progress = 4, unit = unit))),
            )
            assertEquals(
                SyncReconciliationDecision.Write(OperationValue.Progress(ProgressValue(4))),
                reconcile(SyncReconciliationInput(candidate, remote(progress = 5, unit = unit))),
            )
            assertEquals(
                SyncReconciliationDecision.Conflict,
                reconcile(SyncReconciliationInput(candidate, remote(progress = 6, unit = unit))),
            )
        }
    }

    @Test
    fun explicitStatusChoiceRequiresTheObservedProviderStatus() {
        val operation =
            PendingOperation(
                id = OperationId("choice:status"),
                groupId = OperationGroupId("choice"),
                accountId = ACCOUNT,
                collectionEntryId = ENTRY,
                kind = PendingOperationKind.SET_STATUS_CORRECTION,
                base = OperationValue.Status(CollectionStatus.PAUSED),
                target = OperationValue.Status(CollectionStatus.COMPLETED),
                positionInGroup = 0,
            )
        val candidate = candidate(operation)
        assertEquals(
            SyncReconciliationDecision.Write(operation.target),
            reconcile(SyncReconciliationInput(candidate, remote(progress = 12, status = CollectionStatus.PAUSED))),
        )
        assertEquals(
            SyncReconciliationDecision.Satisfied,
            reconcile(SyncReconciliationInput(candidate, remote(progress = 12, status = CollectionStatus.COMPLETED))),
        )
        assertEquals(
            SyncReconciliationDecision.Conflict,
            reconcile(SyncReconciliationInput(candidate, remote(progress = 12, status = CollectionStatus.DROPPED))),
        )
        assertEquals(
            SyncReconciliationDecision.Conflict,
            reconcile(
                SyncReconciliationInput(candidate, remote(progress = 11, total = 12, status = CollectionStatus.PAUSED)),
            ),
        )
    }

    @Test
    fun correctionReopensCompletedOnlyWhenRemoteStillHasTheCorrectedProgress() {
        val operation =
            PendingOperation(
                id = OperationId("correction:status"),
                groupId = OperationGroupId("correction"),
                accountId = ACCOUNT,
                collectionEntryId = ENTRY,
                kind = PendingOperationKind.SET_STATUS,
                base = OperationValue.Status(CollectionStatus.COMPLETED),
                target = OperationValue.Status(CollectionStatus.CURRENT),
                positionInGroup = 1,
            )
        val candidate = candidate(operation).copy(correctionProgress = ProgressValue(9))
        assertEquals(
            SyncReconciliationDecision.Write(OperationValue.Status(CollectionStatus.CURRENT)),
            reconcile(SyncReconciliationInput(candidate, remote(progress = 9, status = CollectionStatus.COMPLETED))),
        )
        assertEquals(
            SyncReconciliationDecision.Conflict,
            reconcile(SyncReconciliationInput(candidate, remote(progress = 12, status = CollectionStatus.COMPLETED))),
        )
        assertEquals(
            SyncReconciliationDecision.Rejected,
            reconcile(SyncReconciliationInput(candidate.copy(correctionProgress = null), remote(progress = 9))),
        )
    }

    @Test
    fun automaticCompletionValidatesKnownTotalsAndItsInverseIsExplicit() {
        val completion =
            PendingOperation(
                id = OperationId("completion-status"),
                groupId = OperationGroupId("completion"),
                accountId = ACCOUNT,
                collectionEntryId = ENTRY,
                kind = PendingOperationKind.SET_STATUS,
                base = OperationValue.Status(CollectionStatus.CURRENT),
                target = OperationValue.Status(CollectionStatus.COMPLETED),
                positionInGroup = 1,
            )
        val candidate = candidate(completion, isAutomaticCompletion = true)
        val correctionUndo = candidate(completion, compensatesGroupId = OperationGroupId("earlier-correction"))
        assertEquals(
            SyncReconciliationDecision.Write(OperationValue.Status(CollectionStatus.COMPLETED)),
            reconcile(SyncReconciliationInput(correctionUndo, remote(progress = 12, total = 12))),
        )
        assertEquals(
            SyncReconciliationDecision.Conflict,
            reconcile(SyncReconciliationInput(correctionUndo, remote(progress = 9, total = 12))),
        )

        assertEquals(
            SyncReconciliationDecision.Write(OperationValue.Status(CollectionStatus.COMPLETED)),
            reconcile(SyncReconciliationInput(candidate, remote(progress = 12, total = 12))),
        )
        assertEquals(
            SyncReconciliationDecision.Satisfied,
            reconcile(
                SyncReconciliationInput(
                    candidate,
                    remote(progress = 12, total = 12, status = CollectionStatus.COMPLETED),
                ),
            ),
        )
        assertEquals(
            SyncReconciliationDecision.Conflict,
            reconcile(SyncReconciliationInput(candidate, remote(progress = 11, total = 12))),
        )
        assertEquals(
            SyncReconciliationDecision.Conflict,
            reconcile(
                SyncReconciliationInput(
                    candidate,
                    remote(progress = 12, total = 12, status = CollectionStatus.PLANNED),
                ),
            ),
        )

        val inverse =
            completion.copyFor(
                groupId = "completion:undo",
                id = "completion:undo:status",
                base = OperationValue.Status(CollectionStatus.COMPLETED),
                target = OperationValue.Status(CollectionStatus.CURRENT),
            )
        val inverseCandidate = candidate(inverse, compensatesGroupId = OperationGroupId("completion"))
        assertEquals(
            SyncReconciliationDecision.Write(OperationValue.Status(CollectionStatus.CURRENT)),
            reconcile(
                SyncReconciliationInput(
                    inverseCandidate,
                    remote(progress = 12, total = 12, status = CollectionStatus.COMPLETED),
                ),
            ),
        )
        assertEquals(
            SyncReconciliationDecision.Conflict,
            reconcile(
                SyncReconciliationInput(
                    inverseCandidate,
                    remote(progress = 12, total = 12, status = CollectionStatus.REPEATING),
                ),
            ),
        )
    }

    @Test
    fun unrelatedStatusIntentIsRejectedEvenIfTheRemoteAlreadyMatches() {
        val operation =
            PendingOperation(
                id = OperationId("unrelated-status"),
                groupId = OperationGroupId("unrelated"),
                accountId = ACCOUNT,
                collectionEntryId = ENTRY,
                kind = PendingOperationKind.SET_STATUS,
                base = OperationValue.Status(CollectionStatus.CURRENT),
                target = OperationValue.Status(CollectionStatus.COMPLETED),
                positionInGroup = 0,
            )

        assertEquals(
            SyncReconciliationDecision.Rejected,
            reconcile(SyncReconciliationInput(candidate(operation), remote(status = CollectionStatus.COMPLETED))),
        )
    }

    private fun candidate(
        operation: PendingOperation,
        unit: ProgressUnit = ProgressUnit.EPISODE,
        isAutomaticCompletion: Boolean = false,
        compensatesGroupId: OperationGroupId? = null,
    ): SyncCandidate =
        SyncCandidate(
            operation = operation,
            current =
                state(
                    unit = unit,
                    progress = 4,
                    status = CollectionStatus.CURRENT,
                    total = if (unit == ProgressUnit.EPISODE) 12 else null,
                ),
            groupOrder = 1,
            isAutomaticCompletion = isAutomaticCompletion,
            compensatesGroupId = compensatesGroupId,
        )

    private fun progressOperation(
        id: String,
        base: Int,
        target: Int,
        kind: PendingOperationKind,
    ): PendingOperation =
        PendingOperation(
            id = OperationId(id),
            groupId = OperationGroupId("group-$id"),
            accountId = ACCOUNT,
            collectionEntryId = ENTRY,
            kind = kind,
            base = OperationValue.Progress(ProgressValue(base)),
            target = OperationValue.Progress(ProgressValue(target)),
            positionInGroup = 0,
        )

    private fun remote(
        progress: Int = 4,
        status: CollectionStatus = CollectionStatus.CURRENT,
        total: Int? = 12,
        unit: ProgressUnit = ProgressUnit.EPISODE,
    ): SyncEntryState = state(unit, progress, status, total)

    private fun state(
        unit: ProgressUnit,
        progress: Int,
        status: CollectionStatus,
        total: Int?,
    ): SyncEntryState =
        SyncEntryState(
            accountId = ACCOUNT,
            entryId = ENTRY,
            unit = unit,
            progress = ProgressValue(progress),
            status = status,
            total = total?.let(::UnitTotal),
            updatedAt = now,
        )

    private fun PendingOperation.copyFor(
        id: String,
        groupId: String,
        base: OperationValue = this.base,
        target: OperationValue = this.target,
    ): PendingOperation =
        PendingOperation(
            id = OperationId(id),
            groupId = OperationGroupId(groupId),
            accountId = accountId,
            collectionEntryId = collectionEntryId,
            kind = kind,
            base = base,
            target = target,
            positionInGroup = positionInGroup,
            dependsOn = dependsOn,
        )

    private companion object {
        val ACCOUNT = AccountId("account-1")
        val ENTRY = CollectionEntryId("entry-1")
    }
}
