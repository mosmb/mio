package io.morgan.mio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.morgan.mio.core.database.entity.LocalCollectionActivityEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
public abstract class CollectionActivityDao {
    @Query(
        "UPDATE local_collection_activity SET " +
            "undo_until_utc_ms = MAX(created_at_utc_ms, MIN(undo_until_utc_ms, :now)) " +
            "WHERE account_id = :accountId AND undone_at_utc_ms IS NULL",
    )
    public abstract suspend fun expireUndo(
        accountId: String,
        now: Instant,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insert(activity: LocalCollectionActivityEntity): Long

    @Query(
        "SELECT * FROM local_collection_activity " +
            "WHERE account_id = :accountId AND operation_group_id = :groupId",
    )
    public abstract suspend fun get(
        accountId: String,
        groupId: String,
    ): LocalCollectionActivityEntity?

    @Query(
        "SELECT * FROM local_collection_activity " +
            "WHERE account_id = :accountId AND undone_at_utc_ms IS NULL " +
            "AND undo_until_utc_ms > :nowUtcMs " +
            "AND EXISTS (SELECT 1 FROM pending_operation_group " +
            "WHERE operation_group_id = local_collection_activity.operation_group_id) " +
            "ORDER BY activity_order DESC LIMIT 1",
    )
    public abstract suspend fun getLatest(
        accountId: String,
        nowUtcMs: Instant,
    ): LocalCollectionActivityEntity?

    @Query(
        "SELECT * FROM local_collection_activity " +
            "WHERE account_id = :accountId AND undone_at_utc_ms IS NULL " +
            "AND undo_until_utc_ms > :nowUtcMs " +
            "AND EXISTS (SELECT 1 FROM pending_operation_group " +
            "WHERE operation_group_id = local_collection_activity.operation_group_id) " +
            "ORDER BY activity_order DESC LIMIT 1",
    )
    public abstract fun observeLatest(
        accountId: String,
        nowUtcMs: Instant,
    ): Flow<LocalCollectionActivityEntity?>

    @Query(
        "UPDATE local_collection_activity SET undone_at_utc_ms = :undoneAtUtcMs " +
            "WHERE account_id = :accountId AND operation_group_id = :groupId " +
            "AND undone_at_utc_ms IS NULL",
    )
    public abstract suspend fun markUndone(
        accountId: String,
        groupId: String,
        undoneAtUtcMs: Instant,
    ): Int
}
