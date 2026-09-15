package io.morgan.mio.domain.sync

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Instant

class PendingOperationTest {
    @Test
    fun supportsEveryKindAndAGroupedAutomaticConsequence() {
        val forward = progressOperation("forward", 1, 2, PendingOperationKind.SET_PROGRESS_FORWARD)
        val correction = progressOperation("correction", 2, 1, PendingOperationKind.SET_PROGRESS_CORRECTION)
        val status =
            PendingOperation(
                id = OperationId("status"),
                groupId = GROUP_ID,
                accountId = ACCOUNT_ID,
                collectionEntryId = ENTRY_ID,
                kind = PendingOperationKind.SET_STATUS,
                base = OperationValue.Status(CollectionStatus.CURRENT),
                target = OperationValue.Status(CollectionStatus.COMPLETED),
                positionInGroup = 1,
                dependsOn = setOf(forward.id),
            )
        assertEquals(PendingOperationKind.SET_PROGRESS_FORWARD, forward.kind)
        assertEquals(PendingOperationKind.SET_PROGRESS_CORRECTION, correction.kind)
        assertEquals(PendingOperationKind.SET_STATUS, status.kind)

        val group =
            PendingOperationGroup(
                id = GROUP_ID,
                accountId = ACCOUNT_ID,
                createdAt = INSTANT,
                operations = listOf(status, forward),
            )
        assertEquals(listOf(status, forward), group.operations)
        assertEquals(setOf(forward.id), status.dependsOn)
    }

    @Test
    fun allowsIndependentSameTimestampGroupsAndExternalPredecessorsAndCompensation() {
        val firstGroupOperation = progressOperation("first", 1, 2, PendingOperationKind.SET_PROGRESS_FORWARD)
        val firstGroup =
            PendingOperationGroup(
                id = GROUP_ID,
                accountId = ACCOUNT_ID,
                createdAt = INSTANT,
                operations = listOf(firstGroupOperation),
            )
        val secondOperation =
            progressOperation(
                id = "second",
                base = 2,
                target = 1,
                kind = PendingOperationKind.SET_PROGRESS_CORRECTION,
                groupId = OperationGroupId("group-2"),
                dependsOn = setOf(firstGroupOperation.id),
            )
        val secondGroup =
            PendingOperationGroup(
                id = OperationGroupId("group-2"),
                accountId = ACCOUNT_ID,
                createdAt = INSTANT,
                operations = listOf(secondOperation),
                compensatesGroupId = firstGroup.id,
            )

        assertEquals(INSTANT, firstGroup.createdAt)
        assertEquals(INSTANT, secondGroup.createdAt)
        assertEquals(firstGroup.id, secondGroup.compensatesGroupId)
        assertNotEquals(firstGroup, secondGroup)
    }

    @Test
    fun snapshotsCollectionsAndHasStructuralEquality() {
        val dependencies = mutableSetOf(OperationId("predecessor"))
        val operation =
            progressOperation(
                id = "operation",
                base = 1,
                target = 2,
                kind = PendingOperationKind.SET_PROGRESS_FORWARD,
                dependsOn = dependencies,
            )
        dependencies += OperationId("another")
        assertEquals(setOf(OperationId("predecessor")), operation.dependsOn)

        val operations = mutableListOf(operation)
        val group = PendingOperationGroup(GROUP_ID, ACCOUNT_ID, INSTANT, operations)
        operations.clear()
        assertEquals(listOf(operation), group.operations)
        assertEquals(operation, operation.copyForEquality())
        assertEquals(operation.hashCode(), operation.copyForEquality().hashCode())
        assertEquals(group, PendingOperationGroup(GROUP_ID, ACCOUNT_ID, INSTANT, listOf(operation)))
        assertEquals(
            group.hashCode(),
            PendingOperationGroup(
                GROUP_ID,
                ACCOUNT_ID,
                INSTANT,
                listOf(operation),
            ).hashCode(),
        )
        assertTrue(operation.toString().contains("operation"))
        assertTrue(group.toString().contains("group-local"))
    }

    @Test
    fun acceptsEveryStructurallyCompleteLifecycleState() {
        listOf(
            PendingOperationState.IN_FLIGHT,
            PendingOperationState.DELIVERED,
            PendingOperationState.BLOCKED,
            PendingOperationState.TERMINAL_FAILURE,
        ).forEach { state ->
            progressOperation(
                id = "valid-$state",
                base = 1,
                target = 2,
                kind = PendingOperationKind.SET_PROGRESS_FORWARD,
                state = state,
                attemptCount = 1,
                lastAttemptAt = INSTANT,
            )
        }
        progressOperation(
            id = "valid-retry",
            base = 1,
            target = 2,
            kind = PendingOperationKind.SET_PROGRESS_FORWARD,
            state = PendingOperationState.RETRYABLE_FAILURE,
            attemptCount = 1,
            lastAttemptAt = INSTANT,
            retryNotBefore = INSTANT,
        )
        progressOperation(
            id = "valid-cancelled-before-attempt",
            base = 1,
            target = 2,
            kind = PendingOperationKind.SET_PROGRESS_FORWARD,
            state = PendingOperationState.CANCELLED,
        )
        progressOperation(
            id = "valid-cancelled-after-attempt",
            base = 1,
            target = 2,
            kind = PendingOperationKind.SET_PROGRESS_FORWARD,
            state = PendingOperationState.CANCELLED,
            attemptCount = 1,
            lastAttemptAt = INSTANT,
        )
    }

    @Test
    fun rejectsOperationShapeAndLifecycleViolations() {
        assertThrows<IllegalArgumentException> {
            progressOperation("negative-position", 1, 2, PendingOperationKind.SET_PROGRESS_FORWARD, position = -1)
        }
        assertThrows<IllegalArgumentException> {
            progressOperation(
                "self",
                1,
                2,
                PendingOperationKind.SET_PROGRESS_FORWARD,
                dependsOn = setOf(OperationId("self")),
            )
        }
        assertThrows<IllegalArgumentException> {
            progressOperation("forward-not-progress", 1, 2, PendingOperationKind.SET_PROGRESS_FORWARD)
                .withValues(
                    OperationValue.Status(CollectionStatus.CURRENT),
                    OperationValue.Status(CollectionStatus.COMPLETED),
                )
        }
        assertThrows<IllegalArgumentException> {
            progressOperation("forward-backward", 2, 1, PendingOperationKind.SET_PROGRESS_FORWARD)
        }
        assertThrows<IllegalArgumentException> {
            progressOperation("correction-same", 1, 1, PendingOperationKind.SET_PROGRESS_CORRECTION)
        }
        assertThrows<IllegalArgumentException> {
            PendingOperation(
                OperationId("correction-status"),
                GROUP_ID,
                ACCOUNT_ID,
                ENTRY_ID,
                PendingOperationKind.SET_PROGRESS_CORRECTION,
                OperationValue.Status(CollectionStatus.CURRENT),
                OperationValue.Status(CollectionStatus.COMPLETED),
                0,
            )
        }
        assertThrows<IllegalArgumentException> {
            PendingOperation(
                OperationId("status-progress"),
                GROUP_ID,
                ACCOUNT_ID,
                ENTRY_ID,
                PendingOperationKind.SET_STATUS,
                OperationValue.Progress(ProgressValue(1)),
                OperationValue.Progress(ProgressValue(2)),
                0,
            )
        }
        assertThrows<IllegalArgumentException> {
            PendingOperation(
                OperationId("status-same"),
                GROUP_ID,
                ACCOUNT_ID,
                ENTRY_ID,
                PendingOperationKind.SET_STATUS,
                OperationValue.Status(CollectionStatus.CURRENT),
                OperationValue.Status(CollectionStatus.CURRENT),
                0,
            )
        }
        assertThrows<IllegalArgumentException> {
            progressOperation("negative-attempt", 1, 2, PendingOperationKind.SET_PROGRESS_FORWARD, attemptCount = -1)
        }
        assertThrows<IllegalArgumentException> {
            progressOperation("enqueued-attempt", 1, 2, PendingOperationKind.SET_PROGRESS_FORWARD, attemptCount = 1)
        }
        assertThrows<IllegalArgumentException> {
            progressOperation("enqueued-time", 1, 2, PendingOperationKind.SET_PROGRESS_FORWARD, lastAttemptAt = INSTANT)
        }
        assertThrows<IllegalArgumentException> {
            progressOperation(
                "enqueued-retry-time",
                1,
                2,
                PendingOperationKind.SET_PROGRESS_FORWARD,
                retryNotBefore = INSTANT,
            )
        }
        listOf(
            PendingOperationState.IN_FLIGHT,
            PendingOperationState.DELIVERED,
            PendingOperationState.BLOCKED,
            PendingOperationState.TERMINAL_FAILURE,
        ).forEach { state ->
            assertThrows<IllegalArgumentException> {
                progressOperation(
                    "missing-attempt-$state",
                    1,
                    2,
                    PendingOperationKind.SET_PROGRESS_FORWARD,
                    state = state,
                )
            }
            assertThrows<IllegalArgumentException> {
                progressOperation(
                    "missing-last-attempt-$state",
                    1,
                    2,
                    PendingOperationKind.SET_PROGRESS_FORWARD,
                    state = state,
                    attemptCount = 1,
                )
            }
            assertThrows<IllegalArgumentException> {
                progressOperation(
                    "missing-positive-attempt-$state",
                    1,
                    2,
                    PendingOperationKind.SET_PROGRESS_FORWARD,
                    state = state,
                    lastAttemptAt = INSTANT,
                )
            }
        }
        assertThrows<IllegalArgumentException> {
            progressOperation(
                "retry-missing-time",
                1,
                2,
                PendingOperationKind.SET_PROGRESS_FORWARD,
                state = PendingOperationState.RETRYABLE_FAILURE,
                attemptCount = 1,
                lastAttemptAt = INSTANT,
            )
        }
        assertThrows<IllegalArgumentException> {
            progressOperation(
                "retry-missing-attempt",
                1,
                2,
                PendingOperationKind.SET_PROGRESS_FORWARD,
                state = PendingOperationState.RETRYABLE_FAILURE,
                retryNotBefore = INSTANT,
            )
        }
        assertThrows<IllegalArgumentException> {
            progressOperation(
                "retry-missing-last-attempt",
                1,
                2,
                PendingOperationKind.SET_PROGRESS_FORWARD,
                state = PendingOperationState.RETRYABLE_FAILURE,
                attemptCount = 1,
                retryNotBefore = INSTANT,
            )
        }
    }

    @Test
    fun rejectsGroupMembershipPositionsAndDependencyViolations() {
        val first = progressOperation("first", 1, 2, PendingOperationKind.SET_PROGRESS_FORWARD)
        val second = progressOperation("second", 2, 3, PendingOperationKind.SET_PROGRESS_FORWARD, position = 1)

        assertThrows<IllegalArgumentException> { PendingOperationGroup(GROUP_ID, ACCOUNT_ID, INSTANT, emptyList()) }
        assertThrows<IllegalArgumentException> {
            PendingOperationGroup(
                GROUP_ID,
                ACCOUNT_ID,
                INSTANT,
                listOf(first, second.copyFor(groupId = OperationGroupId("other"))),
            )
        }
        assertThrows<IllegalArgumentException> {
            PendingOperationGroup(
                GROUP_ID,
                ACCOUNT_ID,
                INSTANT,
                listOf(first, second.copyFor(accountId = AccountId("other-account"))),
            )
        }
        assertThrows<IllegalArgumentException> {
            PendingOperationGroup(
                GROUP_ID,
                ACCOUNT_ID,
                INSTANT,
                listOf(first, second.copyFor(id = first.id.value)),
            )
        }
        assertThrows<IllegalArgumentException> {
            PendingOperationGroup(
                GROUP_ID,
                ACCOUNT_ID,
                INSTANT,
                listOf(first, first.copyFor(id = "duplicate", position = 0)),
            )
        }
        assertThrows<IllegalArgumentException> {
            PendingOperationGroup(
                GROUP_ID,
                ACCOUNT_ID,
                INSTANT,
                listOf(first, second.copyFor(position = 2)),
            )
        }
        assertThrows<IllegalArgumentException> {
            PendingOperationGroup(
                GROUP_ID,
                ACCOUNT_ID,
                INSTANT,
                listOf(second.copyFor(position = 0), first.copyFor(position = 0)),
            )
        }
        assertThrows<IllegalArgumentException> {
            PendingOperationGroup(
                GROUP_ID,
                ACCOUNT_ID,
                INSTANT,
                listOf(first.copyFor(dependsOn = setOf(second.id)), second),
            )
        }
        assertThrows<IllegalArgumentException> {
            PendingOperationGroup(
                GROUP_ID,
                ACCOUNT_ID,
                INSTANT,
                listOf(first),
                compensatesGroupId = GROUP_ID,
            )
        }
    }

    private fun progressOperation(
        id: String,
        base: Int,
        target: Int,
        kind: PendingOperationKind,
        groupId: OperationGroupId = GROUP_ID,
        position: Int = 0,
        dependsOn: Set<OperationId> = emptySet(),
        state: PendingOperationState = PendingOperationState.ENQUEUED,
        attemptCount: Int = 0,
        lastAttemptAt: Instant? = null,
        retryNotBefore: Instant? = null,
    ): PendingOperation =
        PendingOperation(
            id = OperationId(id),
            groupId = groupId,
            accountId = ACCOUNT_ID,
            collectionEntryId = ENTRY_ID,
            kind = kind,
            base = OperationValue.Progress(ProgressValue(base)),
            target = OperationValue.Progress(ProgressValue(target)),
            positionInGroup = position,
            dependsOn = dependsOn,
            state = state,
            attemptCount = attemptCount,
            lastAttemptAt = lastAttemptAt,
            retryNotBefore = retryNotBefore,
        )

    private fun PendingOperation.withValues(
        base: OperationValue,
        target: OperationValue,
    ): PendingOperation =
        PendingOperation(
            id = id,
            groupId = groupId,
            accountId = accountId,
            collectionEntryId = collectionEntryId,
            kind = kind,
            base = base,
            target = target,
            positionInGroup = positionInGroup,
            dependsOn = dependsOn,
            state = state,
            attemptCount = attemptCount,
            lastAttemptAt = lastAttemptAt,
            retryNotBefore = retryNotBefore,
        )

    private fun PendingOperation.copyFor(
        id: String = this.id.value,
        groupId: OperationGroupId = this.groupId,
        accountId: AccountId = this.accountId,
        position: Int = this.positionInGroup,
        dependsOn: Set<OperationId> = this.dependsOn,
    ): PendingOperation =
        PendingOperation(
            id = OperationId(id),
            groupId = groupId,
            accountId = accountId,
            collectionEntryId = collectionEntryId,
            kind = kind,
            base = base,
            target = target,
            positionInGroup = position,
            dependsOn = dependsOn,
            state = state,
            attemptCount = attemptCount,
            lastAttemptAt = lastAttemptAt,
            retryNotBefore = retryNotBefore,
        )

    private fun PendingOperation.copyForEquality(): PendingOperation = copyFor()

    private companion object {
        val ACCOUNT_ID = AccountId("account-local")
        val ENTRY_ID = CollectionEntryId("entry-local")
        val GROUP_ID = OperationGroupId("group-local")
        val INSTANT = Instant.parse("2026-01-02T03:04:05.006Z")
    }
}
