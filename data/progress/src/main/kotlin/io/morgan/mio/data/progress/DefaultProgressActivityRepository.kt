package io.morgan.mio.data.progress

import io.morgan.mio.data.progress.local.RoomProgressLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.progress.ProgressActivityRepository
import io.morgan.mio.domain.progress.RecordedProgressActivity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

public class DefaultProgressActivityRepository(
    private val localDataSource: RoomProgressLocalDataSource,
) : ProgressActivityRepository {
    override fun observeRecordedActivity(
        accountId: AccountId,
        fromInclusive: Instant,
        untilExclusive: Instant,
    ): Flow<List<RecordedProgressActivity>> {
        require(fromInclusive < untilExclusive)
        return localDataSource.observeRecordedActivity(accountId, fromInclusive, untilExclusive)
    }
}
