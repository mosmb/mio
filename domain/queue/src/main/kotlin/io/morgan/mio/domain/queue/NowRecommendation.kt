package io.morgan.mio.domain.queue

import io.morgan.mio.domain.model.ImageReference
import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.MediaTitle
import io.morgan.mio.domain.model.MediaType
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.model.UnitNumber
import io.morgan.mio.domain.model.UnitTotal

public data class NowRecommendation(
    public val mediaId: MediaId,
    public val mediaType: MediaType,
    public val title: MediaTitle,
    public val poster: ImageReference?,
    public val banner: ImageReference?,
    public val progress: ProgressValue,
    public val progressUnit: ProgressUnit,
    public val total: UnitTotal?,
    public val nextUnit: UnitNumber,
    public val availableUnitCount: Int,
) {
    init {
        require(progress.value < Int.MAX_VALUE)
        require(nextUnit.value == progress.value + 1)
        require(availableUnitCount > 0)
        require(total == null || progress.value < total.value)
    }
}
