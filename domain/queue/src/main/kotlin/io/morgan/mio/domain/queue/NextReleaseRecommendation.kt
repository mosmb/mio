package io.morgan.mio.domain.queue

import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.UnitNumber
import kotlin.time.Duration

public data class NextReleaseRecommendation(
    public val mediaId: MediaId,
    public val mediaType: MediaType,
    public val title: MediaTitle,
    public val poster: ImageReference?,
    public val progressUnit: ProgressUnit,
    public val unitNumber: UnitNumber,
    public val waitUntilRelease: Duration,
) {
    init {
        require(waitUntilRelease.isFinite())
        require(waitUntilRelease > Duration.ZERO)
    }
}
