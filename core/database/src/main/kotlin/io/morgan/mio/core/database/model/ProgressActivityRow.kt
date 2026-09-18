package io.morgan.mio.core.database.model

import androidx.room.ColumnInfo
import kotlin.time.Instant

public data class ProgressActivityRow(
    @ColumnInfo(name = "created_at_utc_ms")
    public val createdAtUtcMs: Instant,
    @ColumnInfo(name = "media_type")
    public val mediaType: StoredMediaType,
    @ColumnInfo(name = "direction")
    public val direction: String,
)
