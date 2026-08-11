package io.morgan.mio.data.account

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.data.account.local.RoomAccountLocalDataSource
import io.morgan.mio.domain.account.Account
import io.morgan.mio.domain.account.AccountRepository
import kotlinx.coroutines.flow.Flow

@ContributesBinding(AccountScope::class)
@SingleIn(AccountScope::class)
@Inject
public class DefaultAccountRepository(
    private val localDataSource: RoomAccountLocalDataSource,
    private val refreshRequester: AccountRefreshRequester,
) : AccountRepository {
    override fun observeSelectedAccount(): Flow<Account?> = localDataSource.observeSelectedAccount()

    override suspend fun refreshSelectedAccount() {
        refreshRequester.refreshSelectedAccount()
    }
}
