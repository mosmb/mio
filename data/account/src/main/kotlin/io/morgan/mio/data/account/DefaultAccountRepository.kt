package io.morgan.mio.data.account

import io.morgan.mio.data.account.local.RoomAccountLocalDataSource
import io.morgan.mio.domain.account.Account
import io.morgan.mio.domain.account.AccountRepository
import kotlinx.coroutines.flow.Flow

public class DefaultAccountRepository(
    private val localDataSource: RoomAccountLocalDataSource,
    private val refreshRequester: AccountRefreshRequester,
) : AccountRepository {
    override fun observeSelectedAccount(): Flow<Account?> = localDataSource.observeSelectedAccount()

    override suspend fun refreshSelectedAccount() {
        refreshRequester.refreshSelectedAccount()
    }
}
