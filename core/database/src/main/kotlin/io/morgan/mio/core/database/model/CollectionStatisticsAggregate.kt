package io.morgan.mio.core.database.model

import androidx.room.Embedded
import androidx.room.Relation
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaGenreEntity

public data class CollectionStatisticsAggregate(
    @Embedded
    public val entry: CollectionEntryEntity,
    @Relation(
        parentColumn = "media_id",
        entityColumn = "media_id",
    )
    public val media: MediaEntity,
    @Relation(
        parentColumn = "media_id",
        entityColumn = "media_id",
    )
    public val genres: List<MediaGenreEntity>,
)
