package io.morgan.mio.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
public abstract class CollectionEntryDao {
    @Query(
        "SELECT * FROM collection_entry " +
            "WHERE account_id = :accountId " +
            "ORDER BY collection_entry_id",
    )
    public abstract fun observeCollection(accountId: String): Flow<List<CollectionEntryEntity>>

    @Query("SELECT * FROM collection_entry WHERE collection_entry_id = :entryId")
    public abstract fun observeEntry(entryId: String): Flow<CollectionEntryEntity?>

    @Query(
        "SELECT * FROM collection_entry " +
            "WHERE account_id = :accountId AND media_id = :mediaId",
    )
    public abstract fun observeEntryForMedia(
        accountId: String,
        mediaId: String,
    ): Flow<CollectionEntryEntity?>

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
