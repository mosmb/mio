package io.morgan.mio.domain.progress

import io.morgan.mio.domain.collection.CollectionEntry
import io.morgan.mio.domain.collection.CollectionStatus
import io.morgan.mio.domain.model.ProgressValue

public object ProgressRules {
    public fun decide(
        entry: CollectionEntry,
        direction: ProgressDirection,
    ): Decision =
        if (entry.status != CollectionStatus.CURRENT) {
            Decision.Rejected(ProgressWriteResult.Rejection.INELIGIBLE)
        } else {
            when (direction) {
                ProgressDirection.INCREASE -> increase(entry)
                ProgressDirection.DECREASE -> decrease(entry)
            }
        }

    private fun increase(entry: CollectionEntry): Decision {
        if (entry.progress.value == Int.MAX_VALUE) {
            return Decision.Rejected(ProgressWriteResult.Rejection.AT_BOUNDARY)
        }
        val target = entry.progress.value + 1
        if (entry.total?.value?.let { total -> entry.progress.value >= total } == true) {
            return Decision.Rejected(ProgressWriteResult.Rejection.AT_BOUNDARY)
        }
        return Decision.Change(
            progress = ProgressValue(target),
            status = if (entry.total?.value == target) CollectionStatus.COMPLETED else CollectionStatus.CURRENT,
        )
    }

    private fun decrease(entry: CollectionEntry): Decision =
        if (entry.progress.value == 0) {
            Decision.Rejected(ProgressWriteResult.Rejection.AT_BOUNDARY)
        } else {
            Decision.Change(
                progress = ProgressValue(entry.progress.value - 1),
                status = CollectionStatus.CURRENT,
            )
        }

    public sealed interface Decision {
        public data class Change(
            public val progress: ProgressValue,
            public val status: CollectionStatus,
        ) : Decision

        public data class Rejected(
            public val reason: ProgressWriteResult.Rejection,
        ) : Decision
    }
}
