package io.morgan.mio.domain.progress

import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

public interface ProgressActivityRepository {
    public fun observeRecordedActivity(
        accountId: AccountId,
        fromInclusive: Instant,
        untilExclusive: Instant,
    ): Flow<List<RecordedProgressActivity>>
}

public data class RecordedProgressActivity(
    public val createdAt: Instant,
    public val mediaType: MediaType,
    public val direction: ProgressDirection,
)
