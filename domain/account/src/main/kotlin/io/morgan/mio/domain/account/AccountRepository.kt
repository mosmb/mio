package io.morgan.mio.domain.account

import kotlinx.coroutines.flow.Flow

public interface AccountRepository {
    public fun observeSelectedAccount(): Flow<Account?>

    public suspend fun refreshSelectedAccount()
}
