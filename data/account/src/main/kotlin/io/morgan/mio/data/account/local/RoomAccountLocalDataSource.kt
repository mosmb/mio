package io.morgan.mio.data.account.local

import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.data.account.mapper.AccountProviderIdInput
import io.morgan.mio.data.account.mapper.toDomain
import io.morgan.mio.data.account.mapper.toPersistence
import io.morgan.mio.domain.account.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Inject
public class RoomAccountLocalDataSource(
    private val database: MioDatabase,
) {
    public fun observeSelectedAccount(): Flow<Account?> =
        database.selectedAccountDao().observeSelectedAccount().map { entity -> entity?.toDomain() }

    internal suspend fun replaceSelectedAccount(
        account: Account,
        providerId: AccountProviderIdInput,
    ) {
        val (accountEntity, providerIdEntity) = account.toPersistence(providerId)
        database.inTransaction {
            database.selectedAccountDao().replaceSelectedAccount(accountEntity, providerIdEntity)
        }
    }

    internal suspend fun deleteAccount(accountId: String) {
        require(accountId.isNotBlank())
        database.selectedAccountDao().deleteAccount(accountId)
    }
}
