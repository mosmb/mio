package io.morgan.mio.domain.collection

import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.flow.Flow

public interface CollectionStatisticsRepository {
    public fun observeStatistics(accountId: AccountId): Flow<CollectionStatisticsSnapshot>
}
