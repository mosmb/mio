package io.morgan.mio.core.database.model

import androidx.room.Embedded
import androidx.room.Relation
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaGenreEntity
import io.morgan.mio.core.database.entity.RelatedMediaEntity

public data class MediaAggregate(
    @Embedded
    public val media: MediaEntity,
    @Relation(
        parentColumn = "media_id",
        entityColumn = "media_id",
    )
    public val genres: List<MediaGenreEntity>,
    @Relation(
        parentColumn = "media_id",
        entityColumn = "media_id",
    )
    public val relatedMedia: List<RelatedMediaEntity>,
)
