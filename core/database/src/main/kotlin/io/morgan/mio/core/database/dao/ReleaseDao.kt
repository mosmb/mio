package io.morgan.mio.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.morgan.mio.core.database.entity.ReleaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
public abstract class ReleaseDao {
    @Query(
        "SELECT * FROM release " +
            "WHERE media_id = :mediaId " +
            "ORDER BY progress_unit, unit_number",
    )
    public abstract fun observeReleases(mediaId: String): Flow<List<ReleaseEntity>>

    @Query(
        "SELECT * FROM release " +
            "WHERE media_id = :mediaId ORDER BY progress_unit, unit_number",
    )
    public abstract suspend fun getReleases(mediaId: String): List<ReleaseEntity>

    @Upsert
    public abstract suspend fun upsertReleases(releases: List<ReleaseEntity>)

    @Query("DELETE FROM release WHERE media_id = :mediaId")
    public abstract suspend fun deleteReleases(mediaId: String)
}
