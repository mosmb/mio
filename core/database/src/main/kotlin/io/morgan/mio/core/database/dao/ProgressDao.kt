package io.morgan.mio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.LocalProgressActivityEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
public abstract class ProgressDao {
    @Query(
        "SELECT * FROM collection_entry " +
            "WHERE account_id = :accountId AND media_id = :mediaId",
    )
    public abstract suspend fun getEntryForMedia(
        accountId: String,
        mediaId: String,
    ): CollectionEntryEntity?

    @Query(
        "UPDATE collection_entry SET " +
            "effective_status = :targetStatus, " +
            "effective_progress = :targetProgress, " +
            "effective_updated_at_utc_ms = :targetUpdatedAtUtcMs, " +
            "local_progress_revision = :targetRevision " +
            "WHERE collection_entry_id = :entryId " +
            "AND account_id = :accountId " +
            "AND media_id = :mediaId " +
            "AND local_progress_revision = :expectedRevision " +
            "AND effective_status = :expectedStatus " +
            "AND effective_progress = :expectedProgress " +
            "AND ((:expectedTotal IS NULL AND effective_total IS NULL) OR effective_total = :expectedTotal) " +
            "AND effective_updated_at_utc_ms = :expectedUpdatedAtUtcMs",
    )
    public abstract suspend fun updateEffectiveState(
        entryId: String,
        accountId: String,
        mediaId: String,
        expectedRevision: Long,
        expectedStatus: StoredCollectionStatus,
        expectedProgress: Int,
        expectedTotal: Int?,
        expectedUpdatedAtUtcMs: Instant,
        targetStatus: StoredCollectionStatus,
        targetProgress: Int,
        targetUpdatedAtUtcMs: Instant,
        targetRevision: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertActivity(activity: LocalProgressActivityEntity): Long

    @Query(
        "SELECT * FROM local_progress_activity " +
            "WHERE account_id = :accountId AND operation_group_id = :groupId",
    )
    public abstract suspend fun getActivity(
        accountId: String,
        groupId: String,
    ): LocalProgressActivityEntity?

    @Query(
        "SELECT account_id FROM local_progress_activity " +
            "WHERE operation_group_id = :groupId",
    )
    public abstract suspend fun getActivityAccountId(groupId: String): String?

    @Query(
        "SELECT * FROM local_progress_activity " +
            "WHERE account_id = :accountId " +
            "ORDER BY activity_order DESC LIMIT 1",
    )
    public abstract suspend fun getLatestActivity(accountId: String): LocalProgressActivityEntity?

    @Query(
        "SELECT * FROM local_progress_activity " +
            "WHERE account_id = :accountId " +
            "ORDER BY activity_order DESC LIMIT 1",
    )
    public abstract fun observeLatestActivity(accountId: String): Flow<LocalProgressActivityEntity?>

    @Query(
        "UPDATE local_progress_activity SET undone_at_utc_ms = :undoneAtUtcMs " +
            "WHERE account_id = :accountId " +
            "AND operation_group_id = :groupId " +
            "AND undone_at_utc_ms IS NULL",
    )
    public abstract suspend fun markUndone(
        accountId: String,
        groupId: String,
        undoneAtUtcMs: Instant,
    ): Int
}
