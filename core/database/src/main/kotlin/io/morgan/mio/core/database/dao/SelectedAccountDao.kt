package io.morgan.mio.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.SELECTED_ACCOUNT_KEY
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredConnectedProvider
import kotlinx.coroutines.flow.Flow

@Dao
public abstract class SelectedAccountDao {
    @Query("SELECT * FROM selected_account WHERE selection_key = 'selected'")
    public abstract fun observeSelectedAccount(): Flow<SelectedAccountEntity?>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM selected_account " +
            "WHERE account_id = :accountId AND connected_provider = :provider)",
    )
    public abstract suspend fun isSelectedAccount(
        accountId: String,
        provider: StoredConnectedProvider,
    ): Boolean

    @Query("DELETE FROM selected_account")
    protected abstract suspend fun deleteAllSelectedAccounts()

    @Query("DELETE FROM selected_account WHERE account_id = :accountId")
    public abstract suspend fun deleteAccount(accountId: String)

    @Upsert
    protected abstract suspend fun upsertSelectedAccount(account: SelectedAccountEntity)

    @Upsert
    protected abstract suspend fun upsertAccountProviderId(mapping: AccountProviderIdEntity)

    @Transaction
    public open suspend fun replaceSelectedAccount(
        account: SelectedAccountEntity,
        providerId: AccountProviderIdEntity,
    ) {
        require(account.selectionKey == SELECTED_ACCOUNT_KEY)
        require(providerId.accountId == account.accountId)
        require(providerId.provider == account.connectedProvider)
        deleteAllSelectedAccounts()
        upsertSelectedAccount(account)
        upsertAccountProviderId(providerId)
    }
}
