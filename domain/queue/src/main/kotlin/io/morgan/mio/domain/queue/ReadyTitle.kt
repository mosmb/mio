package io.morgan.mio.domain.queue

import io.morgan.mio.domain.model.UnitNumber

internal data class ReadyTitle(
    val candidate: TodayQueueCandidate,
    val nextUnit: UnitNumber,
    val availableUnitCount: Int,
)
