package io.morgan.mio.domain.progress

import kotlin.time.Instant

public data class ProgressUndo(
    public val action: ProgressAction,
    public val current: ProgressSnapshot,
    public val requestedAt: Instant,
)
