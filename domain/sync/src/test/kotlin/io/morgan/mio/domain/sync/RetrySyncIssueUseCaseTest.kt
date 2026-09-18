package io.morgan.mio.domain.sync

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class RetrySyncIssueUseCaseTest {
    private val issue = issue(SyncIssueKind.REJECTED)

    @Test
    fun rejectedAndExhaustedIssuesAreEligibleForMutationRetry() =
        runTest {
            val repository = RecordingRepository()
            val useCase = RetrySyncIssueUseCase(repository)

            repository.issues[issue.operationId] = issue
            assertTrue(useCase(issue.operationId))
            assertEquals(listOf(issue), repository.rearmed)

            val exhausted = issue(SyncIssueKind.RETRY_EXHAUSTED)
            repository.issues[exhausted.operationId] = exhausted
            assertTrue(useCase(exhausted.operationId))
            assertEquals(listOf(issue, exhausted), repository.rearmed)
        }

    @Test
    fun conflictInvalidAndAuthenticationIssuesNeverInvokeRearm() =
        runTest {
            val repository = RecordingRepository()
            val useCase = RetrySyncIssueUseCase(repository)

            SyncIssueKind.entries
                .filter { kind ->
                    kind == SyncIssueKind.CONFLICT ||
                        kind == SyncIssueKind.INVALID_REMOTE_DATA ||
                        kind == SyncIssueKind.AUTHENTICATION
                }.forEach { kind ->
                    val value = issue(kind)
                    repository.issues[value.operationId] = value
                    assertFalse(useCase(value.operationId))
                }

            assertTrue(repository.rearmed.isEmpty())
        }

    @Test
    fun missingOrStaleIssueReturnsFalse() =
        runTest {
            val repository = RecordingRepository()
            val useCase = RetrySyncIssueUseCase(repository)
            val stale = issue(SyncIssueKind.REJECTED)
            repository.issues[stale.operationId] = stale.copy(detectedAt = Instant.parse("2026-09-12T00:00:00Z"))

            assertFalse(useCase(OperationId("missing")))
            repository.rearmResult = false
            assertFalse(useCase(stale.operationId))
            assertEquals(1, repository.rearmed.size)
        }

    private fun issue(kind: SyncIssueKind): SyncIssue =
        SyncIssue(
            accountId = AccountId("account-1"),
            entryId = CollectionEntryId("entry-1"),
            groupId = OperationGroupId("group-${kind.name.lowercase()}"),
            operationId = OperationId("operation-${kind.name.lowercase()}"),
            kind = kind,
            base = OperationValue.Progress(ProgressValue(1)),
            target = OperationValue.Progress(ProgressValue(2)),
            local =
                SyncEntryState(
                    accountId = AccountId("account-1"),
                    entryId = CollectionEntryId("entry-1"),
                    unit = ProgressUnit.EPISODE,
                    progress = ProgressValue(1),
                    status = CollectionStatus.CURRENT,
                    total = UnitTotal(12),
                    updatedAt = Instant.parse("2026-09-11T10:00:00Z"),
                ),
            remote = null,
            detectedAt = Instant.parse("2026-09-11T10:00:00Z"),
        )

    private class RecordingRepository : SyncRecoveryRepository {
        val issues = mutableMapOf<OperationId, SyncIssue>()
        val rearmed = mutableListOf<SyncIssue>()
        var rearmResult = true

        override fun observeState(): Flow<SyncRecoveryState> =
            flowOf(SyncRecoveryState(accountIssue = null, entryIssues = issues.values.toList()))

        override suspend fun readIssue(operationId: OperationId): SyncIssue? = issues[operationId]

        override suspend fun rearm(issue: SyncIssue): Boolean {
            rearmed += issue
            return rearmResult
        }

        override suspend fun resolveConflict(
            issue: SyncIssue,
            resolution: PendingOperationGroup,
        ): Boolean = false

        override suspend fun recoverAuthentication(
            accountId: AccountId,
            issueIds: Set<String>,
        ): Boolean = false
    }
}
