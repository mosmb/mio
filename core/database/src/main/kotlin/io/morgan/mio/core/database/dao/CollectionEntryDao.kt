package io.morgan.mio.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.model.CollectionStatisticsAggregate
import io.morgan.mio.core.database.model.StoredCollectionStatus
import kotlinx.coroutines.flow.Flow

@Dao
public abstract class CollectionEntryDao {
    @Transaction
    @Query(
        "SELECT * FROM collection_entry " +
            "WHERE account_id = :accountId AND effective_present = 1 ORDER BY collection_entry_id",
    )
    public abstract fun observeStatistics(accountId: String): Flow<List<CollectionStatisticsAggregate>>

    @Query(
        "SELECT * FROM collection_entry " +
            "WHERE account_id = :accountId " +
            "AND effective_present = 1 " +
            "ORDER BY collection_entry_id",
    )
    public abstract fun observeCollection(accountId: String): Flow<List<CollectionEntryEntity>>

    @Query(
        "SELECT * FROM collection_entry " +
            "WHERE collection_entry_id = :entryId AND effective_present = 1",
    )
    public abstract fun observeEntry(entryId: String): Flow<CollectionEntryEntity?>

    @Query("SELECT * FROM collection_entry WHERE collection_entry_id = :entryId")
    public abstract suspend fun getEntry(entryId: String): CollectionEntryEntity?

    @Query(
        "SELECT * FROM collection_entry " +
            "WHERE account_id = :accountId AND media_id = :mediaId LIMIT 1",
    )
    public abstract suspend fun getEntryForMedia(
        accountId: String,
        mediaId: String,
    ): CollectionEntryEntity?

    @Query(
        "SELECT * FROM collection_entry " +
            "WHERE account_id = :accountId AND media_id = :mediaId AND effective_present = 1",
    )
    public abstract fun observeEntryForMedia(
        accountId: String,
        mediaId: String,
    ): Flow<CollectionEntryEntity?>

    @Query(
        "UPDATE collection_entry SET " +
            "effective_status = :targetStatus, " +
            "effective_progress = :targetProgress, " +
            "effective_total = :targetTotal, " +
            "effective_updated_at_utc_ms = :targetUpdatedAtUtcMs, " +
            "effective_present = :targetPresent, " +
            "local_progress_revision = :targetRevision " +
            "WHERE collection_entry_id = :entryId AND account_id = :accountId " +
            "AND media_id = :mediaId AND local_progress_revision = :expectedRevision " +
            "AND effective_present = :expectedPresent AND effective_status = :expectedStatus " +
            "AND effective_progress = :expectedProgress " +
            "AND ((:expectedTotal IS NULL AND effective_total IS NULL) OR effective_total = :expectedTotal) " +
            "AND effective_updated_at_utc_ms = :expectedUpdatedAtUtcMs",
    )
    public abstract suspend fun updateEffectiveState(
        entryId: String,
        accountId: String,
        mediaId: String,
        expectedRevision: Long,
        expectedPresent: Boolean,
        expectedStatus: StoredCollectionStatus,
        expectedProgress: Int,
        expectedTotal: Int?,
        expectedUpdatedAtUtcMs: kotlin.time.Instant,
        targetPresent: Boolean,
        targetStatus: StoredCollectionStatus,
        targetProgress: Int,
        targetTotal: Int?,
        targetUpdatedAtUtcMs: kotlin.time.Instant,
        targetRevision: Long,
    ): Int

    @Query("DELETE FROM collection_entry WHERE account_id = :accountId AND collection_entry_id = :entryId")
    public abstract suspend fun deleteEntry(
        accountId: String,
        entryId: String,
    ): Int

    @Query(
        "UPDATE collection_entry SET remote_present = 0 " +
            "WHERE account_id = :accountId AND collection_entry_id = :entryId",
    )
    public abstract suspend fun markRemoteAbsent(
        accountId: String,
        entryId: String,
    ): Int

    @Upsert
    public abstract suspend fun upsertEntries(entries: List<CollectionEntryEntity>)

    @Query("DELETE FROM collection_entry WHERE account_id = :accountId")
    public abstract suspend fun deleteAllForAccount(accountId: String)

    @Query(
        "DELETE FROM collection_entry " +
            "WHERE account_id = :accountId AND collection_entry_id NOT IN (:entryIds)",
    )
    public abstract suspend fun deleteEntriesNotIn(
        accountId: String,
        entryIds: List<String>,
    )
}
