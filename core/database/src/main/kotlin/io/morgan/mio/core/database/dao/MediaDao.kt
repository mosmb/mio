package io.morgan.mio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaGenreEntity
import io.morgan.mio.core.database.entity.RelatedMediaEntity
import io.morgan.mio.core.database.model.MediaAggregate
import kotlinx.coroutines.flow.Flow

@Dao
public abstract class MediaDao {
    @Transaction
    @Query("SELECT * FROM media WHERE media_id = :mediaId")
    public abstract fun observeMedia(mediaId: String): Flow<MediaAggregate?>

    @Transaction
    @Query(
        "SELECT media.* FROM related_media " +
            "JOIN media ON media.media_id = related_media.related_media_id " +
            "WHERE related_media.media_id = :mediaId " +
            "ORDER BY related_media.position",
    )
    public abstract fun observeRelatedMedia(mediaId: String): Flow<List<MediaAggregate>>

    @Query("SELECT * FROM media WHERE media_id = :mediaId")
    public abstract suspend fun getMedia(mediaId: String): MediaEntity?

    @Query("SELECT media_id FROM media WHERE media_id IN (:mediaIds)")
    public abstract suspend fun getMediaIds(mediaIds: List<String>): List<String>

    @Query("SELECT * FROM media_genre WHERE media_id = :mediaId ORDER BY position")
    public abstract suspend fun getGenres(mediaId: String): List<MediaGenreEntity>

    @Query("SELECT * FROM related_media WHERE media_id = :mediaId ORDER BY position")
    public abstract suspend fun getRelatedMedia(mediaId: String): List<RelatedMediaEntity>

    @Upsert
    public abstract suspend fun upsertMedia(media: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract suspend fun insertMediaIfAbsent(media: List<MediaEntity>)

    @Upsert
    public abstract suspend fun upsertGenres(genres: List<MediaGenreEntity>)

    @Upsert
    public abstract suspend fun upsertRelatedMedia(relatedMedia: List<RelatedMediaEntity>)

    @Query("DELETE FROM media_genre WHERE media_id = :mediaId")
    public abstract suspend fun deleteGenres(mediaId: String)

    @Query("DELETE FROM related_media WHERE media_id = :mediaId")
    public abstract suspend fun deleteRelatedMedia(mediaId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract suspend fun insertGenresIfAbsent(genres: List<MediaGenreEntity>)
}
