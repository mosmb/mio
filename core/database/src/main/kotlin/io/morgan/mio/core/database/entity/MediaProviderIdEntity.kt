package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import io.morgan.mio.core.database.model.StoredConnectedProvider

@Entity(
    tableName = "media_provider_id",
    primaryKeys = ["provider", "remote_media_id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["media_id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "index_media_provider_id_media_id", value = ["media_id"]),
        Index(
            name = "index_media_provider_id_provider_media_id",
            value = ["provider", "media_id"],
            unique = true,
        ),
    ],
)
public data class MediaProviderIdEntity(
    @ColumnInfo(name = "provider")
    public val provider: StoredConnectedProvider,
    @ColumnInfo(name = "remote_media_id")
    public val remoteMediaId: String,
    @ColumnInfo(name = "media_id")
    public val mediaId: String,
) {
    init {
        require(remoteMediaId.isNotBlank())
        require(mediaId.isNotBlank())
    }
}
