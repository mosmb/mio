package io.morgan.mio.data.collection

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.domain.collection.CollectionStatisticsRepository
import io.morgan.mio.domain.collection.CollectionStatisticsSnapshot
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.flow.Flow

@ContributesBinding(AccountScope::class)
@SingleIn(AccountScope::class)
@Inject
public class DefaultCollectionStatisticsRepository(
    private val localDataSource: RoomCollectionStatisticsLocalDataSource,
) : CollectionStatisticsRepository {
    override fun observeStatistics(accountId: AccountId): Flow<CollectionStatisticsSnapshot> =
        localDataSource.observeStatistics(accountId)
}
