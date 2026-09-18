package io.morgan.mio.domain.progress

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitTotal
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CorrectProgressUseCaseTest {
    private val now = Instant.parse("2026-08-23T12:00:00Z")
    private val mediaId = MediaId("media-correction")

    @Test
    fun exactCorrectionUsesEntryUnitAndAutomaticallyCompletesKnownFinalValue() =
        runTest {
            val repository = InMemoryRepository(entry(progress = 8), mediaId, now)

            val result =
                CorrectProgressUseCase(repository, FixedClock(now))(
                    mediaId,
                    targetProgress = 12,
                    actionId = OperationGroupId("correction-final"),
                    undoWindow = 6.seconds,
                )

            assertInstanceOf(ProgressWriteResult.Committed::class.java, result)
            val change = repository.committed.single()
            assertEquals(ProgressDirection.CORRECTION, change.direction)
            assertEquals(ProgressUnit.CHAPTER, change.before.entry.progressUnit)
            assertEquals(12, change.targetProgress.value)
            assertEquals(CollectionStatus.COMPLETED, change.targetStatus)
        }

    @Test
    fun knownMaximumAndIneligibleStatusAreRejectedWithoutCommit() =
        runTest {
            val repository = InMemoryRepository(entry(progress = 8), mediaId, now)
            val useCase = CorrectProgressUseCase(repository, FixedClock(now))

            val tooHigh = useCase(mediaId, 13, OperationGroupId("too-high"), 6.seconds)
            assertEquals(
                ProgressWriteResult.Rejection.AT_BOUNDARY,
                assertInstanceOf(ProgressWriteResult.Rejected::class.java, tooHigh).reason,
            )
            repository.snapshot = ProgressSnapshot(repository.snapshot.entry.copy(status = CollectionStatus.PAUSED), 0)
            val paused = useCase(mediaId, 3, OperationGroupId("paused"), 6.seconds)
            assertEquals(
                ProgressWriteResult.Rejection.INELIGIBLE,
                assertInstanceOf(ProgressWriteResult.Rejected::class.java, paused).reason,
            )
            assertTrue(repository.committed.isEmpty())
        }

    @Test
    fun staleWriteRecomputesFromTheLatestExactSnapshot() =
        runTest {
            val repository = InMemoryRepository(entry(progress = 4), mediaId, now).apply { staleOnce = true }

            val result =
                CorrectProgressUseCase(repository, FixedClock(now))(
                    mediaId,
                    targetProgress = 9,
                    actionId = OperationGroupId("correction-retry"),
                    undoWindow = 6.seconds,
                )

            assertInstanceOf(ProgressWriteResult.Committed::class.java, result)
            assertEquals(listOf(9, 9), repository.attempts.map { change -> change.targetProgress.value })
            assertEquals(9, repository.snapshot.entry.progress.value)
        }

    private fun entry(
        progress: Int,
        status: CollectionStatus = CollectionStatus.CURRENT,
    ) = CollectionEntry(
        id = CollectionEntryId("entry-correction"),
        accountId = AccountId("account-correction"),
        mediaId = mediaId,
        status = status,
        progress = ProgressValue(progress),
        progressUnit = ProgressUnit.CHAPTER,
        total = UnitTotal(12),
        updatedAt = now,
    )

    private class FixedClock(
        private val instant: Instant,
    ) : Clock {
        override fun now(): Instant = instant
    }

    private class InMemoryRepository(
        initial: CollectionEntry,
        private val mediaId: MediaId,
        private val now: Instant,
    ) : ProgressRepository {
        var snapshot = ProgressSnapshot(initial, 0)
        var staleOnce = false
        val attempts = mutableListOf<ProgressChange>()
        val committed = mutableListOf<ProgressChange>()

        override suspend fun readSnapshot(mediaId: MediaId): ProgressSnapshot? =
            snapshot.takeIf { value -> value.entry.mediaId == mediaId }

        override suspend fun findAction(actionId: OperationGroupId): ProgressAction? = null

        override fun observeLatestAction() = emptyFlow<ProgressAction?>()

        override suspend fun readLatestAction(): ProgressAction? = null

        override suspend fun commit(change: ProgressChange): ProgressWriteResult {
            attempts += change
            if (staleOnce) {
                staleOnce = false
                return ProgressWriteResult.Stale
            }
            if (change.before != snapshot) return ProgressWriteResult.Stale
            committed += change
            snapshot =
                ProgressSnapshot(change.before.entry.copy(progress = change.targetProgress), snapshot.revision + 1)
            return ProgressWriteResult.Committed(
                ProgressAction(
                    id = change.actionId,
                    order = committed.lastIndex.toLong(),
                    accountId = change.before.entry.accountId,
                    entryId = change.before.entry.id,
                    mediaId = mediaId,
                    direction = change.direction,
                    unit = change.before.entry.progressUnit,
                    beforeProgress = change.before.entry.progress,
                    afterProgress = change.targetProgress,
                    beforeStatus = change.before.entry.status,
                    afterStatus = change.targetStatus,
                    beforeUpdatedAt = change.before.entry.updatedAt,
                    afterUpdatedAt = now,
                    afterRevision = snapshot.revision,
                    createdAt = now,
                    undoUntil = now.plus(6.seconds),
                    undoneAt = null,
                ),
            )
        }

        override suspend fun undo(change: ProgressUndo): ProgressWriteResult =
            ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.UNDO_UNAVAILABLE)
    }
}
