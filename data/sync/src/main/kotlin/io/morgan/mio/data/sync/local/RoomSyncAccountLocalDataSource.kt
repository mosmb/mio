package io.morgan.mio.data.sync.local

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.SyncInfrastructureException
import kotlinx.coroutines.CancellationException

@Inject
public class RoomSyncAccountLocalDataSource(
    private val database: MioDatabase,
) {
    public suspend fun readSelectedAccount(): PersistedSyncAccount? {
        return storage {
            val selected = database.syncDao().getSelectedAccount() ?: return@storage null
            val accountMapping =
                database.syncDao().getAccountProviderId(selected.accountId)
                    ?: throw SyncInfrastructureException(
                        SyncInfrastructureException.Kind.CONFIGURATION,
                    )
            val provider = selected.connectedProvider.toProviderKey()
            if (
                accountMapping.provider != selected.connectedProvider ||
                accountMapping.remoteAccountId.isBlank()
            ) {
                throw SyncInfrastructureException(SyncInfrastructureException.Kind.CONFIGURATION)
            }
            PersistedSyncAccount(AccountId(selected.accountId), provider)
        }
    }

    public suspend fun isSelected(
        accountId: AccountId,
        provider: ProviderKey,
    ): Boolean {
        return storage {
            val selected = database.syncDao().getSelectedAccount() ?: return@storage false
            selected.accountId == accountId.value && selected.connectedProvider.toProviderKey() == provider
        }
    }

    private suspend fun <T> storage(block: suspend () -> T): T =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SQLiteException) {
            throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
        }
}

private fun StoredConnectedProvider.toProviderKey(): ProviderKey =
    when (this) {
        StoredConnectedProvider.ANILIST -> ProviderKey.ANILIST
        StoredConnectedProvider.MY_ANIME_LIST -> ProviderKey.MY_ANIME_LIST
    }
