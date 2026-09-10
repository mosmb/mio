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
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class UndoProgressUseCaseTest {
    private val createdAt = Instant.parse("2026-08-23T12:00:00Z")
    private val actionId = OperationGroupId("group-1")
    private val mediaId = MediaId("media-1")

    @Test
    fun onlyTheDisplayedLatestActionCanBeUndone() =
        runTest {
            val action = action()
            val repository = UndoRepository(action, snapshot(action))
            val result = UndoProgressUseCase(repository, UndoFixedClock(createdAt.plus(1.seconds)))(actionId)

            assertInstanceOf(ProgressWriteResult.Committed::class.java, result)
            assertEquals(listOf(actionId), repository.undoCalls)

            repository.latest = action.copy(id = OperationGroupId("newer"))
            val obsolete = UndoProgressUseCase(repository, UndoFixedClock(createdAt.plus(1.seconds)))(actionId)
            assertEquals(
                ProgressWriteResult.Rejection.UNDO_UNAVAILABLE,
                assertInstanceOf(ProgressWriteResult.Rejected::class.java, obsolete).reason,
            )
            assertEquals(listOf(actionId), repository.undoCalls)
        }

    @Test
    fun exactDeadlineIsRejectedButJustBeforeExpiryIsSubmitted() =
        runTest {
            val action = action()
            val repository = UndoRepository(action, snapshot(action))
            val deadline = action.undoUntil

            val expired = UndoProgressUseCase(repository, UndoFixedClock(deadline))(actionId)
            assertEquals(
                ProgressWriteResult.Rejection.UNDO_UNAVAILABLE,
                assertInstanceOf(ProgressWriteResult.Rejected::class.java, expired).reason,
            )
            assertEquals(0, repository.undoCalls.size)

            val beforeDeadline =
                UndoProgressUseCase(repository, UndoFixedClock(deadline.minus(1.seconds)))(actionId)
            assertInstanceOf(ProgressWriteResult.Committed::class.java, beforeDeadline)
            assertEquals(1, repository.undoCalls.size)
        }

    @Test
    fun staleRevisionOrEffectiveStateAndAlreadyUndoneAreNotRetargeted() =
        runTest {
            val action = action()
            val repository = UndoRepository(action, snapshot(action).copy(revision = 2))
            val stale = UndoProgressUseCase(repository, UndoFixedClock(createdAt.plus(1.seconds)))(actionId)
            assertEquals(
                ProgressWriteResult.Rejection.UNDO_UNAVAILABLE,
                assertInstanceOf(ProgressWriteResult.Rejected::class.java, stale).reason,
            )

            repository.snapshot =
                snapshot(action).copy(
                    entry = snapshot(action).entry.copy(progress = ProgressValue(8)),
                )
            val changed = UndoProgressUseCase(repository, UndoFixedClock(createdAt.plus(1.seconds)))(actionId)
            assertEquals(
                ProgressWriteResult.Rejection.UNDO_UNAVAILABLE,
                assertInstanceOf(ProgressWriteResult.Rejected::class.java, changed).reason,
            )

            repository.snapshot = snapshot(action)
            repository.latest = action.copy(undoneAt = createdAt.plus(2.seconds))
            val alreadyUndone = UndoProgressUseCase(repository, UndoFixedClock(createdAt.plus(1.seconds)))(actionId)
            assertEquals(
                ProgressWriteResult.Rejection.UNDO_UNAVAILABLE,
                assertInstanceOf(ProgressWriteResult.Rejected::class.java, alreadyUndone).reason,
            )
            assertEquals(0, repository.undoCalls.size)
        }

    @Test
    fun attemptedGroupFailureIsReturnedWithoutChangingTheRequestedIdentity() =
        runTest {
            val action = action()
            val repository = UndoRepository(action, snapshot(action))
            repository.undoResult =
                ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.UNDO_UNAVAILABLE)
            val result = UndoProgressUseCase(repository, UndoFixedClock(createdAt.plus(1.seconds)))(actionId)

            assertEquals(
                ProgressWriteResult.Rejection.UNDO_UNAVAILABLE,
                assertInstanceOf(ProgressWriteResult.Rejected::class.java, result).reason,
            )
            assertEquals(listOf(actionId), repository.undoCalls)
        }

    private fun action(
        id: OperationGroupId = actionId,
        undoneAt: Instant? = null,
    ): ProgressAction =
        ProgressAction(
            id = id,
            order = 1,
            accountId = AccountId("account-1"),
            entryId = CollectionEntryId("entry-1"),
            mediaId = mediaId,
            direction = ProgressDirection.INCREASE,
            unit = ProgressUnit.EPISODE,
            beforeProgress = ProgressValue(8),
            afterProgress = ProgressValue(9),
            beforeStatus = CollectionStatus.CURRENT,
            afterStatus = CollectionStatus.CURRENT,
            beforeUpdatedAt = createdAt,
            afterUpdatedAt = createdAt.plus(1.seconds),
            afterRevision = 1,
            createdAt = createdAt,
            undoUntil = createdAt.plus(6.seconds),
            undoneAt = undoneAt,
        )

    private fun snapshot(action: ProgressAction): ProgressSnapshot =
        ProgressSnapshot(
            entry =
                CollectionEntry(
                    id = action.entryId,
                    accountId = action.accountId,
                    mediaId = action.mediaId,
                    status = action.afterStatus,
                    progress = action.afterProgress,
                    progressUnit = action.unit,
                    total = UnitTotal(12),
                    updatedAt = action.afterUpdatedAt,
                ),
            revision = action.afterRevision,
        )
}

private class UndoFixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

private class UndoRepository(
    var latest: ProgressAction?,
    var snapshot: ProgressSnapshot?,
) : ProgressRepository {
    val undoCalls = mutableListOf<OperationGroupId>()
    var undoResult: ProgressWriteResult =
        ProgressWriteResult.Committed(requireNotNull(latest))

    override suspend fun readSnapshot(mediaId: MediaId): ProgressSnapshot? = snapshot

    override suspend fun findAction(actionId: OperationGroupId): ProgressAction? = null

    override fun observeLatestAction() = emptyFlow<ProgressAction?>()

    override suspend fun readLatestAction(): ProgressAction? = latest

    override suspend fun commit(change: ProgressChange): ProgressWriteResult =
        ProgressWriteResult.Rejected(ProgressWriteResult.Rejection.INVALID_REQUEST)

    override suspend fun undo(change: ProgressUndo): ProgressWriteResult {
        undoCalls += change.action.id
        return undoResult
    }
}
