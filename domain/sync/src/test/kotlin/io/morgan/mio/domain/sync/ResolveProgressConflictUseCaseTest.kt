package io.morgan.mio.domain.sync

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

class ResolveProgressConflictUseCaseTest {
    private val now = Instant.parse("2026-09-18T10:00:00Z")

    @Test
    fun undoDuringCreateConflictKeepsTheLatestUntrackedIntent() =
        runTest {
            val conflict =
                issue().copy(
                    base = OperationValue.Membership(false),
                    target = OperationValue.Membership(true),
                    localPresent = false,
                )
            val repository = RecordingRepository(conflict)
            assertTrue(
                ResolveProgressConflictUseCase(
                    repository,
                    FixedClock(now),
                )(conflict, ProgressConflictChoice.KEEP_DEVICE, OperationGroupId("keep-untracked")),
            )
            assertEquals(
                PendingOperationKind.REMOVE_ENTRY,
                repository.resolution
                    ?.operations
                    ?.single()
                    ?.kind,
            )
        }

    @Test
    fun membershipConflictRequiresExplicitChoiceAndNeverRecreatesAnExistingEntry() =
        runTest {
            val conflict =
                issue().copy(
                    base = OperationValue.Membership(false),
                    target = OperationValue.Membership(true),
                )
            val repository = RecordingRepository(conflict)
            assertTrue(
                ResolveProgressConflictUseCase(
                    repository,
                    FixedClock(now),
                )(conflict, ProgressConflictChoice.KEEP_DEVICE, OperationGroupId("keep")),
            )
            assertEquals(
                PendingOperationKind.SET_PROGRESS_CORRECTION,
                repository.resolution
                    ?.operations
                    ?.single()
                    ?.kind,
            )
            assertTrue(
                ResolveProgressConflictUseCase(
                    repository,
                    FixedClock(now),
                )(conflict, ProgressConflictChoice.USE_PROVIDER, OperationGroupId("accept")),
            )
            assertEquals(
                OperationValue.Membership(true),
                repository.resolution
                    ?.operations
                    ?.single()
                    ?.target,
            )
        }

    @Test
    fun bothExplicitChoicesCreateCorrectionOperationsFromTheCurrentSnapshot() =
        runTest {
            ProgressConflictChoice.entries.forEach { choice ->
                val repository = RecordingRepository(issue())
                val groupId = OperationGroupId("resolution-${choice.name.lowercase()}")

                assertTrue(ResolveProgressConflictUseCase(repository, FixedClock(now))(issue(), choice, groupId))

                val operation = repository.resolution?.operations?.single()
                assertEquals(PendingOperationKind.SET_PROGRESS_CORRECTION, operation?.kind)
                assertEquals(groupId, operation?.groupId)
                assertEquals(
                    when (choice) {
                        ProgressConflictChoice.KEEP_DEVICE -> OperationValue.Progress(ProgressValue(7))
                        ProgressConflictChoice.USE_PROVIDER -> OperationValue.Progress(ProgressValue(9))
                    },
                    operation?.base,
                )
                assertEquals(
                    when (choice) {
                        ProgressConflictChoice.KEEP_DEVICE -> OperationValue.Progress(ProgressValue(9))
                        ProgressConflictChoice.USE_PROVIDER -> OperationValue.Progress(ProgressValue(7))
                    },
                    operation?.target,
                )
            }
        }

    @Test
    fun staleOrNonProgressIssuesCannotBeResolved() =
        runTest {
            val conflict = issue()
            val staleRepository =
                RecordingRepository(conflict.copy(detectedAt = now.plus(kotlin.time.Duration.parse("1s"))))
            assertTrue(
                !ResolveProgressConflictUseCase(staleRepository, FixedClock(now))(
                    conflict,
                    ProgressConflictChoice.KEEP_DEVICE,
                    OperationGroupId("stale-resolution"),
                ),
            )
            val authenticationRepository = RecordingRepository(conflict.copy(kind = SyncIssueKind.AUTHENTICATION))
            assertTrue(
                !ResolveProgressConflictUseCase(authenticationRepository, FixedClock(now))(
                    authenticationRepository.currentIssue,
                    ProgressConflictChoice.KEEP_DEVICE,
                    OperationGroupId("not-conflict"),
                ),
            )
            assertEquals(null, staleRepository.resolution)
            assertEquals(null, authenticationRepository.resolution)
        }

    private fun issue() =
        SyncIssue(
            accountId = AccountId("account-resolution"),
            entryId = CollectionEntryId("entry-resolution"),
            groupId = OperationGroupId("failed-correction"),
            operationId = OperationId("failed-correction:progress"),
            kind = SyncIssueKind.CONFLICT,
            base = OperationValue.Progress(ProgressValue(5)),
            target = OperationValue.Progress(ProgressValue(9)),
            local = state(9),
            remote = state(7).copy(revision = 0),
            detectedAt = now,
        )

    private fun state(progress: Int) =
        SyncEntryState(
            accountId = AccountId("account-resolution"),
            entryId = CollectionEntryId("entry-resolution"),
            unit = ProgressUnit.CHAPTER,
            progress = ProgressValue(progress),
            status = CollectionStatus.CURRENT,
            total = UnitTotal(20),
            updatedAt = now,
            revision = 4,
        )

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }

    private class RecordingRepository(
        val currentIssue: SyncIssue,
    ) : SyncRecoveryRepository {
        var resolution: PendingOperationGroup? = null

        override fun observeState() = emptyFlow<SyncRecoveryState>()

        override suspend fun readIssue(operationId: OperationId): SyncIssue? =
            currentIssue.takeIf { issue -> issue.operationId == operationId }

        override suspend fun rearm(issue: SyncIssue): Boolean = false

        override suspend fun resolveConflict(
            issue: SyncIssue,
            resolution: PendingOperationGroup,
        ): Boolean {
            if (issue != currentIssue) return false
            this.resolution = resolution
            return true
        }

        override suspend fun recoverAuthentication(
            accountId: AccountId,
            issueIds: Set<String>,
        ): Boolean = false
    }
}
