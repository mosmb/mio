package io.morgan.mio.domain.media

import io.morgan.mio.domain.model.MediaId
import io.morgan.mio.domain.model.ProgressUnit
import io.morgan.mio.domain.model.UnitNumber
import kotlin.time.Instant

public sealed interface ReleaseTime {
    public data object Unknown : ReleaseTime

    public data class Trusted(
        public val instant: Instant,
    ) : ReleaseTime

    public data class Untrusted(
        public val instant: Instant,
    ) : ReleaseTime
}

public data class Release(
    public val mediaId: MediaId,
    public val unit: ProgressUnit,
    public val number: UnitNumber,
    public val time: ReleaseTime,
)
