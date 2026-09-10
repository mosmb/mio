package io.morgan.mio.domain.progress

import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObserveProgressUndoUseCaseTest {
    private val start = Instant.parse("2026-08-23T12:00:00Z")

    @Test
    fun deadlineEmitsNullWithoutAnotherDatabaseWrite() =
        runTest {
            val clock = MutableClock(start)
            val action = action("group-1", start)
            val repository = ObservableUndoRepository(action)
            val values = mutableListOf<ProgressAction?>()
            val job = launch { ObserveProgressUndoUseCase(repository, clock)().collect(values::add) }

            runCurrent()
            assertEquals(listOf(action), values)
            clock.set(action.undoUntil)
            advanceTimeBy(6_000)
            runCurrent()

            assertNull(values.last())
            assertEquals(1, repository.observationCount)
            job.cancel()
        }

    @Test
    fun newerActionReplacesOldAndUndoneNewestDoesNotRevealOlder() =
        runTest {
            val clock = MutableClock(start)
            val old = action("old", start)
            val newer = action("newer", start.plus(1.seconds))
            val repository = ObservableUndoRepository(old)
            val values = mutableListOf<ProgressAction?>()
            val job = launch { ObserveProgressUndoUseCase(repository, clock)().collect(values::add) }
            runCurrent()

            repository.actions.value = newer
            runCurrent()
            assertEquals(newer, values.last())

            repository.actions.value = newer.copy(undoneAt = start.plus(2.seconds))
            runCurrent()
            assertNull(values.last())
            job.cancel()
        }

    @Test
    fun subscriptionAfterExpiryIsImmediatelyEmptyAndCancellationStopsTimer() =
        runTest {
            val clock = MutableClock(start)
            val action = action("group-1", start)
            val repository = ObservableUndoRepository(action)
            clock.set(action.undoUntil)
            val values = mutableListOf<ProgressAction?>()
            val job = launch { ObserveProgressUndoUseCase(repository, clock)().collect(values::add) }
            runCurrent()
            assertEquals(listOf<ProgressAction?>(null), values)
            job.cancel()

            clock.set(start)
            repository.actions.value = action
            values.clear()
            val secondJob = launch { ObserveProgressUndoUseCase(repository, clock)().collect(values::add) }
            runCurrent()
            assertEquals(listOf(action), values)
            secondJob.cancel()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(listOf(action), values)
        }

    @Test
    fun clockMovingBackAtFirstWakeupStillExpiresOnTheNextWakeup() =
        runTest {
            val clock = MutableClock(start)
            val action = action("clock-change", start)
            val values = mutableListOf<ProgressAction?>()
            val job =
                launch { ObserveProgressUndoUseCase(ObservableUndoRepository(action), clock)().collect(values::add) }
            runCurrent()
            clock.set(start - 1.seconds)
            advanceTimeBy(6_000)
            runCurrent()
            assertEquals(action, values.last())
            clock.set(action.undoUntil)
            advanceTimeBy(7_000)
            runCurrent()
            assertNull(values.last())
            job.cancel()
        }

    private fun action(
        id: String,
        createdAt: Instant,
    ): ProgressAction =
        ProgressAction(
            id = OperationGroupId(id),
            order = 1,
            accountId = AccountId("account-1"),
            entryId = CollectionEntryId("entry-1"),
            mediaId = MediaId("media-1"),
            direction = ProgressDirection.INCREASE,
            unit = ProgressUnit.EPISODE,
            beforeProgress = ProgressValue(1),
            afterProgress = ProgressValue(2),
            beforeStatus = CollectionStatus.CURRENT,
            afterStatus = CollectionStatus.CURRENT,
            beforeUpdatedAt = createdAt,
            afterUpdatedAt = createdAt,
            afterRevision = 1,
            createdAt = createdAt,
            undoUntil = createdAt.plus(6.seconds),
            undoneAt = null,
        )
}

private class MutableClock(
    private var instant: Instant,
) : Clock {
    override fun now(): Instant = instant

    fun set(value: Instant) {
        instant = value
    }
}

private class ObservableUndoRepository(
    initial: ProgressAction?,
) : ProgressRepository {
    val actions = MutableStateFlow(initial)
    var observationCount: Int = 0

    override suspend fun readSnapshot(mediaId: MediaId): ProgressSnapshot? = null

    override suspend fun findAction(actionId: OperationGroupId): ProgressAction? = null

    override fun observeLatestAction(): Flow<ProgressAction?> = actions.also { observationCount++ }

    override suspend fun readLatestAction(): ProgressAction? = actions.value

    override suspend fun commit(change: ProgressChange): ProgressWriteResult =
        ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.INVALID_REQUEST)

    override suspend fun undo(change: ProgressUndo): ProgressWriteResult =
        ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.UNDO_UNAVAILABLE)
}
