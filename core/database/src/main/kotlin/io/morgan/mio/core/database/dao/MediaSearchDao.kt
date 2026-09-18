package io.morgan.mio.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.morgan.mio.core.database.entity.MediaSearchQueryEntity
import io.morgan.mio.core.database.entity.MediaSearchResultEntity
import io.morgan.mio.core.database.model.MediaAggregate

@Dao
public abstract class MediaSearchDao {
    @Query("SELECT * FROM media_search_query WHERE account_id = :accountId AND media_filter = :filter")
    public abstract suspend fun getQuery(
        accountId: String,
        filter: String,
    ): MediaSearchQueryEntity?

    @Transaction
    @Query(
        "SELECT media.* FROM media_search_result AS result " +
            "INNER JOIN media_search_query AS search " +
            "ON search.account_id = result.account_id AND search.media_filter = result.media_filter " +
            "INNER JOIN media ON media.media_id = result.media_id " +
            "WHERE result.account_id = :accountId AND result.media_filter = :filter " +
            "AND search.normalized_query = :query ORDER BY result.position",
    )
    public abstract suspend fun getResults(
        accountId: String,
        filter: String,
        query: String,
    ): List<MediaAggregate>

    @Query(
        "SELECT media_id FROM media_search_result " +
            "WHERE account_id = :accountId AND media_filter = :filter ORDER BY position",
    )
    public abstract suspend fun getResultMediaIds(
        accountId: String,
        filter: String,
    ): List<String>

    @Upsert
    public abstract suspend fun upsertQuery(query: MediaSearchQueryEntity)

    @Query("DELETE FROM media_search_result WHERE account_id = :accountId AND media_filter = :filter")
    public abstract suspend fun deleteResults(
        accountId: String,
        filter: String,
    )

    @Upsert
    public abstract suspend fun upsertResults(results: List<MediaSearchResultEntity>)
}
