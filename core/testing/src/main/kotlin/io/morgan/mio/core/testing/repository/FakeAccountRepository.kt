package io.morgan.mio.core.testing.repository

import io.morgan.mio.domain.account.Account
import io.morgan.mio.domain.account.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

public class FakeAccountRepository : AccountRepository {
    private val selectedAccount = MutableStateFlow<Account?>(null)
    private val refreshCountValue = MutableStateFlow(0)

    public val refreshCount: Int
        get() = refreshCountValue.value

    public fun setSelectedAccount(account: Account?) {
        selectedAccount.value = account
    }

    override fun observeSelectedAccount(): Flow<Account?> = selectedAccount.asStateFlow()

    override suspend fun refreshSelectedAccount() {
        refreshCountValue.update { count -> count + 1 }
    }
}
