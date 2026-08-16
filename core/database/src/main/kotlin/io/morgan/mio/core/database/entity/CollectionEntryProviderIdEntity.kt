package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import io.morgan.mio.core.database.model.StoredConnectedProvider

@Entity(
    tableName = "collection_entry_provider_id",
    primaryKeys = ["provider", "remote_collection_entry_id"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntryEntity::class,
            parentColumns = ["collection_entry_id"],
            childColumns = ["collection_entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            name = "index_collection_entry_provider_id_collection_entry_id",
            value = ["collection_entry_id"],
        ),
        Index(
            name = "index_collection_entry_provider_id_provider_collection_entry_id",
            value = ["provider", "collection_entry_id"],
            unique = true,
        ),
    ],
)
public data class CollectionEntryProviderIdEntity(
    @ColumnInfo(name = "provider")
    public val provider: StoredConnectedProvider,
    @ColumnInfo(name = "remote_collection_entry_id")
    public val remoteCollectionEntryId: String,
    @ColumnInfo(name = "collection_entry_id")
    public val collectionEntryId: String,
) {
    init {
        require(remoteCollectionEntryId.isNotBlank())
        require(collectionEntryId.isNotBlank())
    }
}
