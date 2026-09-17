package io.morgan.mio.di

import dev.zacsweers.metro.Inject
import io.morgan.mio.data.provider.api.ProviderBindingResolver
import io.morgan.mio.data.sync.local.RoomSyncAccountLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.SyncAccountSession
import io.morgan.mio.domain.sync.SyncAccountSessionFactory
import io.morgan.mio.domain.sync.SyncInfrastructureException

@Inject
internal class MetroSyncAccountSessionFactory(
    private val accountGraphFactory: AccountGraph.Factory,
    private val accountLocalDataSource: RoomSyncAccountLocalDataSource,
    private val bindingResolver: ProviderBindingResolver,
) : SyncAccountSessionFactory {
    override suspend fun open(accountId: AccountId): SyncAccountSession? {
        val persisted = accountLocalDataSource.readSelectedAccount() ?: return null
        if (persisted.accountId != accountId) return null
        val binding =
            bindingResolver.bindingFor(persisted.provider)
                ?: throw SyncInfrastructureException(SyncInfrastructureException.Kind.CONFIGURATION)
        val graph =
            accountGraphFactory.createAccountGraph(
                accountId = persisted.accountId,
                providerBinding = binding,
            )
        return object : SyncAccountSession {
            override val synchronize = graph.synchronizeAccountUseCase

            override fun close() = Unit
        }
    }
}
