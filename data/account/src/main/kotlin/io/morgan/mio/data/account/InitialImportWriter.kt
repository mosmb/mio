package io.morgan.mio.data.account

import dev.zacsweers.metro.Inject
import io.morgan.mio.data.account.local.RoomInitialImportLocalDataSource
import io.morgan.mio.data.account.mapper.toInitialImportPersistence
import io.morgan.mio.data.provider.api.CompleteProviderImport
import io.morgan.mio.data.provider.api.ProviderIdentity
import io.morgan.mio.domain.model.AccountId

@Inject
public class InitialImportWriter(
    private val localDataSource: RoomInitialImportLocalDataSource,
) {
    internal suspend fun commit(
        identity: ProviderIdentity,
        import: CompleteProviderImport,
    ): AccountId {
        val snapshot = import.toInitialImportPersistence(identity)
        localDataSource.commit(snapshot)
        return snapshot.accountId
    }
}
