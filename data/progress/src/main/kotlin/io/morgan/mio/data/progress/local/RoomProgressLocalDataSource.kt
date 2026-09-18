package io.morgan.mio.data.progress.local

import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.LocalProgressActivityEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.data.progress.mapper.toProgressAction
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.progress.ProgressAction
import io.morgan.mio.domain.progress.ProgressDirection
import io.morgan.mio.domain.progress.RecordedProgressActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public class RoomProgressLocalDataSource(
    private val database: MioDatabase,
) {
    internal suspend fun getEntryForMedia(
        accountId: AccountId,
        mediaId: MediaId,
    ): CollectionEntryEntity? = database.progressDao().getEntryForMedia(accountId.value, mediaId.value)

    internal suspend fun updateEffectiveState(
        entryId: String,
        accountId: AccountId,
        mediaId: MediaId,
        expectedRevision: Long,
        expectedStatus: StoredCollectionStatus,
        expectedProgress: Int,
        expectedTotal: Int?,
        expectedUpdatedAtUtcMs: kotlin.time.Instant,
        targetStatus: StoredCollectionStatus,
        targetProgress: Int,
        targetUpdatedAtUtcMs: kotlin.time.Instant,
        targetRevision: Long,
    ): Int =
        database.progressDao().updateEffectiveState(
            entryId = entryId,
            accountId = accountId.value,
            mediaId = mediaId.value,
            expectedRevision = expectedRevision,
            expectedStatus = expectedStatus,
            expectedProgress = expectedProgress,
            expectedTotal = expectedTotal,
            expectedUpdatedAtUtcMs = expectedUpdatedAtUtcMs,
            targetStatus = targetStatus,
            targetProgress = targetProgress,
            targetUpdatedAtUtcMs = targetUpdatedAtUtcMs,
            targetRevision = targetRevision,
        )

    internal suspend fun insertActivity(activity: LocalProgressActivityEntity): Long =
        database.progressDao().insertActivity(activity)

    internal suspend fun getActivity(
        accountId: AccountId,
        groupId: OperationGroupId,
    ): ProgressAction? = database.progressDao().getActivity(accountId.value, groupId.value)?.toProgressAction()

    internal suspend fun getLatestActivity(accountId: AccountId): ProgressAction? =
        database.progressDao().getLatestActivity(accountId.value)?.toProgressAction()

    internal fun observeLatestActivity(accountId: AccountId): Flow<ProgressAction?> =
        database.progressDao().observeLatestActivity(accountId.value).map { activity -> activity?.toProgressAction() }

    internal fun observeRecordedActivity(
        accountId: AccountId,
        fromInclusive: kotlin.time.Instant,
        untilExclusive: kotlin.time.Instant,
    ): Flow<List<RecordedProgressActivity>> =
        database.progressDao().observeRecordedActivity(accountId.value, fromInclusive, untilExclusive).map { rows ->
            rows.map { row ->
                RecordedProgressActivity(
                    createdAt = row.createdAtUtcMs,
                    mediaType =
                        when (row.mediaType) {
                            io.morgan.mio.core.database.model.StoredMediaType.ANIME -> MediaType.ANIME
                            io.morgan.mio.core.database.model.StoredMediaType.MANGA -> MediaType.MANGA
                        },
                    direction =
                        when (row.direction) {
                            "increase" -> ProgressDirection.INCREASE
                            "decrease" -> ProgressDirection.DECREASE
                            "correction" -> ProgressDirection.CORRECTION
                            else -> error("Unknown local progress direction: ${row.direction}")
                        },
                )
            }
        }

    internal suspend fun markUndone(
        accountId: AccountId,
        groupId: OperationGroupId,
        undoneAtUtcMs: kotlin.time.Instant,
    ): Int = database.progressDao().markUndone(accountId.value, groupId.value, undoneAtUtcMs)
}
