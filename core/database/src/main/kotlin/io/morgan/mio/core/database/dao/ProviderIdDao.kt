package io.morgan.mio.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity

@Dao
public abstract class ProviderIdDao {
    @Upsert
    public abstract suspend fun upsertMediaProviderIds(mappings: List<MediaProviderIdEntity>)

    @Query("DELETE FROM media_provider_id WHERE media_id = :mediaId")
    public abstract suspend fun deleteMediaProviderIds(mediaId: String)

    @Upsert
    public abstract suspend fun upsertCollectionEntryProviderIds(mappings: List<CollectionEntryProviderIdEntity>)

    @Query("DELETE FROM collection_entry_provider_id WHERE collection_entry_id = :entryId")
    public abstract suspend fun deleteCollectionEntryProviderIds(entryId: String)
}
