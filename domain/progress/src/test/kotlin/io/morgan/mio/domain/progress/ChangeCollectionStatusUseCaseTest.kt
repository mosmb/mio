package io.morgan.mio.domain.progress

import io.morgan.mio.domain.collection.CollectionAction
import io.morgan.mio.domain.collection.CollectionActionKind
import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.collection.CollectionStatusChange
import io.morgan.mio.domain.collection.CollectionWriteResult
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ChangeCollectionStatusUseCaseTest {
    private val now = Instant.parse("2026-08-23T12:00:00Z")
    private val accountId = AccountId("account-1")
    private val mediaId = MediaId("media-1")

    @Test
    fun completionUsesKnownTotalButPreservesUnknownTotalProgress() =
        runTest {
            val known = InMemoryCollectionRepository(entry(progress = 4, total = 12))

            val knownResult = change(known, CollectionStatus.COMPLETED, knownTotal = UnitTotal(12))

            val knownChange = known.commits.single()
            assertEquals(12, knownChange.targetProgress.value)
            assertEquals(UnitTotal(12), knownChange.targetTotal)
            assertInstanceOf(CollectionWriteResult.Committed::class.java, knownResult)

            val unknown = InMemoryCollectionRepository(entry(progress = 4, total = null))
            change(unknown, CollectionStatus.COMPLETED, knownTotal = null)

            val unknownChange = unknown.commits.single()
            assertEquals(4, unknownChange.targetProgress.value)
            assertEquals(null, unknownChange.targetTotal)
        }

    @Test
    fun untrackedStatusIsExplicitAndSameStatusOrUnsupportedStatusDoesNotCommit() =
        runTest {
            val untracked = InMemoryCollectionRepository(null)
            val added = change(untracked, CollectionStatus.CURRENT, knownTotal = UnitTotal(12))

            val committedChange = untracked.commits.single()
            assertEquals(null, committedChange.before)
            assertEquals(CollectionStatus.CURRENT, committedChange.targetStatus)
            assertEquals(ProgressUnit.EPISODE, committedChange.progressUnit)
            assertEquals(0, committedChange.targetProgress.value)
            assertEquals(UnitTotal(12), committedChange.targetTotal)
            assertInstanceOf(CollectionWriteResult.Committed::class.java, added)

            val completed = InMemoryCollectionRepository(null)
            change(completed, CollectionStatus.COMPLETED, knownTotal = UnitTotal(12))
            assertEquals(
                12,
                completed.commits
                    .single()
                    .targetProgress.value,
            )
            assertEquals(UnitTotal(12), completed.commits.single().targetTotal)

            val sameRepository =
                InMemoryCollectionRepository(entry(progress = 4, total = 12, status = CollectionStatus.CURRENT))
            val sameResult = change(sameRepository, CollectionStatus.CURRENT, knownTotal = UnitTotal(12))
            assertEquals(
                CollectionWriteResult.Rejection.INVALID_REQUEST,
                assertInstanceOf(CollectionWriteResult.Rejected::class.java, sameResult).reason,
            )

            val unsupportedRepository = InMemoryCollectionRepository(entry(progress = 4, total = 12))
            unsupportedRepository.writableStatuses.value = listOf(CollectionStatus.CURRENT)
            val unsupported = change(unsupportedRepository, CollectionStatus.COMPLETED, knownTotal = UnitTotal(12))
            assertEquals(
                CollectionWriteResult.Rejection.UNSUPPORTED_STATUS,
                assertInstanceOf(CollectionWriteResult.Rejected::class.java, unsupported).reason,
            )
        }

    private suspend fun change(
        repository: InMemoryCollectionRepository,
        target: CollectionStatus,
        knownTotal: UnitTotal?,
    ): CollectionWriteResult =
        ChangeCollectionStatusUseCase(repository, FixedClock(now))(
            accountId = accountId,
            mediaId = mediaId,
            progressUnit = ProgressUnit.EPISODE,
            knownTotal = knownTotal,
            targetStatus = target,
            actionId = OperationGroupId("action-${repository.commits.size}"),
            undoWindow = 6.seconds,
        )

    private fun entry(
        progress: Int,
        total: Int?,
        status: CollectionStatus = CollectionStatus.CURRENT,
    ): CollectionEntry =
        CollectionEntry(
            id = CollectionEntryId("entry-1"),
            accountId = accountId,
            mediaId = mediaId,
            status = status,
            progress = ProgressValue(progress),
            progressUnit = ProgressUnit.EPISODE,
            total = total?.let(::UnitTotal),
            updatedAt = now,
        )

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }

    private class InMemoryCollectionRepository(
        initial: CollectionEntry?,
    ) : CollectionRepository {
        val entries = MutableStateFlow(listOfNotNull(initial))
        val writableStatuses = MutableStateFlow(CollectionStatus.entries.toList())
        val commits = mutableListOf<CollectionStatusChange>()
        private val actions = mutableMapOf<OperationGroupId, CollectionAction>()

        override fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>> = entries

        override fun observeEntry(entryId: CollectionEntryId): Flow<CollectionEntry?> =
            flowOf(entries.value.firstOrNull { entry -> entry.id == entryId })

        override fun observeEntryForMedia(
            accountId: AccountId,
            mediaId: MediaId,
        ): Flow<CollectionEntry?> =
            flowOf(entries.value.firstOrNull { entry -> entry.accountId == accountId && entry.mediaId == mediaId })

        override suspend fun readEntryForMedia(
            accountId: AccountId,
            mediaId: MediaId,
        ): CollectionEntry? =
            entries.value.firstOrNull { entry -> entry.accountId == accountId && entry.mediaId == mediaId }

        override fun observeWritableStatuses(
            accountId: AccountId,
            mediaId: MediaId,
        ): Flow<List<CollectionStatus>> = writableStatuses

        override suspend fun findAction(actionId: OperationGroupId): CollectionAction? = actions[actionId]

        override fun observeLatestAction(): Flow<CollectionAction?> = flowOf(actions.values.lastOrNull())

        override suspend fun readLatestAction(): CollectionAction? = actions.values.lastOrNull()

        override suspend fun commitStatus(change: CollectionStatusChange): CollectionWriteResult {
            commits += change
            val after =
                CollectionEntry(
                    id = change.before?.id ?: CollectionEntryId("entry-new"),
                    accountId = change.accountId,
                    mediaId = change.mediaId,
                    status = change.targetStatus,
                    progress = change.targetProgress,
                    progressUnit = change.progressUnit,
                    total = change.targetTotal,
                    updatedAt = change.createdAt,
                )
            entries.value = listOf(after)
            val action =
                CollectionAction(
                    id = change.actionId,
                    accountId = change.accountId,
                    entryId = after.id,
                    mediaId = after.mediaId,
                    kind = if (change.before == null) CollectionActionKind.ADD else CollectionActionKind.STATUS,
                    before = change.before,
                    after = after,
                    createdAt = change.createdAt,
                    undoUntil = change.undoUntil,
                    undoneAt = null,
                )
            actions[change.actionId] = action
            return CollectionWriteResult.Committed(action)
        }

        override suspend fun undo(actionId: OperationGroupId): CollectionWriteResult =
            CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.UNDO_UNAVAILABLE)

        override suspend fun refreshCollection(accountId: AccountId) = Unit
    }
}
