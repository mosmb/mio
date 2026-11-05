package io.morgan.mio.core.testing.repository

import io.morgan.mio.core.testing.time.FakeClock
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant

public class FakeCollectionRepository(
    private val clock: Clock = FakeClock(Instant.parse("2026-01-01T00:00:00Z")),
) : CollectionRepository {
    private val entries = MutableStateFlow<List<CollectionEntry>>(emptyList())
    private val refreshedAccountIdValues = MutableStateFlow<List<AccountId>>(emptyList())
    private val latestAction = MutableStateFlow<CollectionAction?>(null)

    public val refreshedAccountIds: List<AccountId>
        get() = refreshedAccountIdValues.value.toList()

    public fun setEntries(entries: List<CollectionEntry>) {
        this.entries.value = entries.toList()
    }

    override fun observeCollection(accountId: AccountId): Flow<List<CollectionEntry>> =
        entries
            .map { current ->
                current
                    .filter { entry -> entry.accountId == accountId }
                    .sortedBy { entry -> entry.id.value }
                    .toList()
            }.distinctUntilChanged()

    override fun observeEntry(entryId: CollectionEntryId): Flow<CollectionEntry?> =
        entries
            .map { current -> current.firstOrNull { entry -> entry.id == entryId } }
            .distinctUntilChanged()

    override fun observeEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<CollectionEntry?> =
        entries
            .map { current ->
                current.firstOrNull { entry ->
                    entry.accountId == accountId && entry.mediaId == mediaId
                }
            }.distinctUntilChanged()

    override suspend fun readEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): CollectionEntry? =
        entries.value.firstOrNull { entry -> entry.accountId == accountId && entry.mediaId == mediaId }

    override fun observeWritableStatuses(
        accountId: AccountId,
        mediaId: MediaId,
    ): Flow<List<CollectionStatus>> = kotlinx.coroutines.flow.flowOf(CollectionStatus.entries.toList())

    override suspend fun findAction(actionId: OperationGroupId): CollectionAction? =
        latestAction.value?.takeIf { action -> action.id == actionId }

    override fun observeLatestAction(): Flow<CollectionAction?> = latestAction

    override suspend fun readLatestAction(): CollectionAction? = latestAction.value

    override suspend fun commitStatus(change: CollectionStatusChange): CollectionWriteResult {
        val entry =
            CollectionEntry(
                id = change.before?.id ?: CollectionEntryId("fake-${change.mediaId.value}"),
                accountId = change.accountId,
                mediaId = change.mediaId,
                status = change.targetStatus,
                progress = change.targetProgress,
                progressUnit = change.progressUnit,
                total = change.targetTotal,
                updatedAt = change.createdAt,
            )
        entries.update { current -> current.filterNot { it.mediaId == change.mediaId } + entry }
        val action =
            CollectionAction(
                id = change.actionId,
                accountId = change.accountId,
                entryId = entry.id,
                mediaId = change.mediaId,
                kind = if (change.before == null) CollectionActionKind.ADD else CollectionActionKind.STATUS,
                before = change.before,
                after = entry,
                createdAt = change.createdAt,
                undoUntil = change.undoUntil,
                undoneAt = null,
            )
        latestAction.value = action
        return CollectionWriteResult.Committed(action)
    }

    override suspend fun undo(actionId: OperationGroupId): CollectionWriteResult {
        val action =
            latestAction.value?.takeIf { it.id == actionId }
                ?: return CollectionWriteResult.Rejected(CollectionWriteResult.Rejection.UNDO_UNAVAILABLE)
        action.before?.let { before ->
            entries.update { current ->
                current.filterNot { it.id == action.entryId } +
                    before
            }
        }
            ?: entries.update { current -> current.filterNot { it.id == action.entryId } }
        val undone = action.copy(undoneAt = clock.now())
        latestAction.value = undone
        return CollectionWriteResult.Committed(undone)
    }

    override suspend fun refreshCollection(accountId: AccountId) {
        refreshedAccountIdValues.update { accountIds -> accountIds + accountId }
    }
}
