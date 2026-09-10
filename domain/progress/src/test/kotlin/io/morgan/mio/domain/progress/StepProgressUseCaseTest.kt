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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StepProgressUseCaseTest {
    private val now = Instant.parse("2026-08-23T12:00:00Z")
    private val mediaId = MediaId("media-1")

    @Test
    fun staleCasRecomputesTheTargetWithTheSameActionId() =
        runTest {
            val repository = InMemoryProgressRepository(entry(progress = 1))
            repository.returnStaleOnce = true
            val useCase = StepProgressUseCase(repository, StepFixedClock(now))

            val result = useCase(mediaId, ProgressDirection.INCREASE, OperationGroupId("group-retry"), 6.seconds)

            assertInstanceOf(ProgressWriteResult.Committed::class.java, result)
            assertEquals(listOf(2, 3), repository.attempts.map { change -> change.targetProgress.value })
            assertEquals(
                3,
                repository.commits
                    .last()
                    .targetProgress.value,
            )
            assertEquals(
                listOf(OperationGroupId("group-retry")),
                repository.commits.map(ProgressChange::actionId),
            )
        }

    @Test
    fun concurrentAcceptedRequestsBecomeSeparateOrderedSteps() =
        runTest {
            val repository = InMemoryProgressRepository(entry(progress = 0, total = null))
            val useCase = StepProgressUseCase(repository, StepFixedClock(now))
            val results =
                listOf("group-a", "group-b")
                    .map { id ->
                        async {
                            useCase(
                                mediaId,
                                ProgressDirection.INCREASE,
                                OperationGroupId(id),
                                6.seconds,
                            )
                        }
                    }.awaitAll()

            assertTrue(results.all { result -> result is ProgressWriteResult.Committed })
            assertEquals(2, repository.snapshot.entry.progress.value)
            assertEquals(2, repository.commits.size)
            assertEquals(
                setOf(OperationGroupId("group-a"), OperationGroupId("group-b")),
                repository.commits.map(ProgressChange::actionId).toSet(),
            )
        }

    @Test
    fun alreadyHandledIdIsReturnedBeforeReadingAndRemainsHandledAfterUndo() =
        runTest {
            val repository = InMemoryProgressRepository(entry(progress = 1))
            val action = repository.commitAction(OperationGroupId("group-existing"), ProgressDirection.INCREASE)
            val useCase = StepProgressUseCase(repository, StepFixedClock(now))
            val committedCount = repository.commits.size

            val beforeUndo = useCase(mediaId, ProgressDirection.INCREASE, action.id, 6.seconds)
            repository.actions[action.id] = action.copy(undoneAt = now.plus(1.seconds))
            val afterUndo = useCase(mediaId, ProgressDirection.INCREASE, action.id, 6.seconds)

            assertEquals(ProgressWriteResult.AlreadyHandled(action), beforeUndo)
            assertEquals(ProgressWriteResult.AlreadyHandled(action.copy(undoneAt = now.plus(1.seconds))), afterUndo)
            assertEquals(committedCount, repository.commits.size)
        }

    @Test
    fun invalidRequestAndCancellationDoNotSubmitACommand() =
        runTest {
            val repository = InMemoryProgressRepository(entry(progress = 1))
            val useCase = StepProgressUseCase(repository, StepFixedClock(now))

            val invalid = useCase(mediaId, ProgressDirection.INCREASE, OperationGroupId("invalid"), 5.seconds)
            assertEquals(
                ProgressWriteResult.Rejection.INVALID_REQUEST,
                assertInstanceOf(ProgressWriteResult.Rejected::class.java, invalid).reason,
            )

            val readStarted = CompletableDeferred<Unit>()
            val cancellationRepository = CancellationRepository(readStarted)
            val cancellationUseCase = StepProgressUseCase(cancellationRepository, StepFixedClock(now))
            val job =
                launch {
                    cancellationUseCase(mediaId, ProgressDirection.INCREASE, OperationGroupId("canceled"), 6.seconds)
                }
            readStarted.await()
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertEquals(0, cancellationRepository.commitCalls)
        }

    private fun entry(
        progress: Int,
        total: Int? = 12,
    ): CollectionEntry =
        CollectionEntry(
            id = CollectionEntryId("entry-1"),
            accountId = AccountId("account-1"),
            mediaId = mediaId,
            status = CollectionStatus.CURRENT,
            progress = ProgressValue(progress),
            progressUnit = ProgressUnit.EPISODE,
            total = total?.let(::UnitTotal),
            updatedAt = now,
        )
}

private class CancellationRepository(
    private val readStarted: CompletableDeferred<Unit>,
) : ProgressRepository {
    var commitCalls: Int = 0

    override suspend fun readSnapshot(mediaId: MediaId): ProgressSnapshot? {
        readStarted.complete(Unit)
        awaitCancellation()
    }

    override suspend fun findAction(actionId: OperationGroupId): ProgressAction? = null

    override fun observeLatestAction() = emptyFlow<ProgressAction?>()

    override suspend fun readLatestAction(): ProgressAction? = null

    override suspend fun commit(change: ProgressChange): ProgressWriteResult {
        commitCalls++
        error("canceled request reached commit")
    }

    override suspend fun undo(change: ProgressUndo): ProgressWriteResult =
        ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.UNDO_UNAVAILABLE)
}

private class StepFixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

private class InMemoryProgressRepository(
    initial: CollectionEntry,
) : ProgressRepository {
    private val mutex = Mutex()
    var snapshot = ProgressSnapshot(initial, revision = 0)
    var returnStaleOnce = false
    val attempts = mutableListOf<ProgressChange>()
    val commits = mutableListOf<ProgressChange>()
    val actions = mutableMapOf<OperationGroupId, ProgressAction>()

    override suspend fun readSnapshot(mediaId: MediaId): ProgressSnapshot? =
        mutex.withLock { snapshot.takeIf { value -> value.entry.mediaId == mediaId } }

    override suspend fun findAction(actionId: OperationGroupId): ProgressAction? = mutex.withLock { actions[actionId] }

    override fun observeLatestAction() = kotlinx.coroutines.flow.emptyFlow<ProgressAction?>()

    override suspend fun readLatestAction(): ProgressAction? = mutex.withLock { actions.values.lastOrNull() }

    override suspend fun commit(change: ProgressChange): ProgressWriteResult =
        mutex.withLock {
            attempts += change
            if (returnStaleOnce) {
                returnStaleOnce = false
                snapshot =
                    snapshot.copy(
                        entry = snapshot.entry.copy(progress = ProgressValue(snapshot.entry.progress.value + 1)),
                    )
                return@withLock ProgressWriteResult.Stale
            }
            if (change.before != snapshot) return@withLock ProgressWriteResult.Stale
            commits += change
            snapshot =
                ProgressSnapshot(
                    entry =
                        snapshot.entry.copy(
                            progress = change.targetProgress,
                            status = change.targetStatus,
                            updatedAt = snapshot.entry.updatedAt.plus(1.seconds),
                        ),
                    revision = snapshot.revision + 1,
                )
            val action = change.toAction(snapshot)
            actions[change.actionId] = action
            ProgressWriteResult.Committed(action)
        }

    override suspend fun undo(change: ProgressUndo): ProgressWriteResult =
        ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.UNDO_UNAVAILABLE)

    suspend fun commitAction(
        id: OperationGroupId,
        direction: ProgressDirection,
    ): ProgressAction {
        val before = readSnapshot(snapshot.entry.mediaId) ?: error("missing fixture")
        val change =
            ProgressChange(
                actionId = id,
                before = before,
                direction = direction,
                targetProgress = ProgressValue(before.entry.progress.value + 1),
                targetStatus = CollectionStatus.CURRENT,
                undoWindow = 6.seconds,
            )
        return assertInstanceOf(ProgressWriteResult.Committed::class.java, commit(change)).action
    }

    private fun ProgressChange.toAction(after: ProgressSnapshot): ProgressAction =
        ProgressAction(
            id = actionId,
            order = commits.size.toLong(),
            accountId = before.entry.accountId,
            entryId = before.entry.id,
            mediaId = before.entry.mediaId,
            direction = direction,
            unit = before.entry.progressUnit,
            beforeProgress = before.entry.progress,
            afterProgress = targetProgress,
            beforeStatus = before.entry.status,
            afterStatus = targetStatus,
            beforeUpdatedAt = before.entry.updatedAt,
            afterUpdatedAt = after.entry.updatedAt,
            afterRevision = after.revision,
            createdAt = after.entry.updatedAt,
            undoUntil = after.entry.updatedAt.plus(6.seconds),
            undoneAt = null,
        )
}
