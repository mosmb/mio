package io.morgan.mio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.database.model.StoredReleaseTimeKind
import kotlin.time.Instant

@Entity(
    tableName = "release",
    primaryKeys = ["media_id", "progress_unit", "unit_number"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["media_id"],
            childColumns = ["media_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "index_release_media_id", value = ["media_id"]),
    ],
)
public data class ReleaseEntity(
    @ColumnInfo(name = "media_id")
    public val mediaId: String,
    @ColumnInfo(name = "progress_unit")
    public val progressUnit: StoredProgressUnit,
    @ColumnInfo(name = "unit_number")
    public val unitNumber: Int,
    @ColumnInfo(name = "time_kind")
    public val timeKind: StoredReleaseTimeKind,
    @ColumnInfo(name = "release_instant_utc_ms")
    public val releaseInstantUtcMs: Instant?,
) {
    init {
        require(mediaId.isNotBlank())
        require(unitNumber > 0)
        when (timeKind) {
            StoredReleaseTimeKind.UNKNOWN -> require(releaseInstantUtcMs == null)
            StoredReleaseTimeKind.TRUSTED,
            StoredReleaseTimeKind.UNTRUSTED,
            -> requireNotNull(releaseInstantUtcMs)
        }
    }
}
