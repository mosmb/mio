package io.morgan.mio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.data.sync.DefaultSyncRepository
import io.morgan.mio.data.sync.ProviderSyncRemoteDataSource
import io.morgan.mio.data.sync.local.RoomSyncLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.ProjectSyncEntryUseCase
import io.morgan.mio.domain.sync.ReconcilePendingOperationUseCase
import io.morgan.mio.domain.sync.SyncRepository
import io.morgan.mio.domain.sync.SynchronizeAccountUseCase
import java.util.UUID
import kotlin.time.Clock

@BindingContainer
@ContributesTo(AccountScope::class)
public object AccountSyncBindings {
    @Provides
    @SingleIn(AccountScope::class)
    public fun provideProjectSyncEntryUseCase(): ProjectSyncEntryUseCase = ProjectSyncEntryUseCase()

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideReconcilePendingOperationUseCase(): ReconcilePendingOperationUseCase =
        ReconcilePendingOperationUseCase()

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideSyncRepository(
        accountId: AccountId,
        localDataSource: RoomSyncLocalDataSource,
        remoteDataSource: ProviderSyncRemoteDataSource,
    ): SyncRepository = DefaultSyncRepository(accountId, localDataSource, remoteDataSource)

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideSynchronizeAccountUseCase(
        repository: SyncRepository,
        clock: Clock,
        reconcile: ReconcilePendingOperationUseCase,
    ): SynchronizeAccountUseCase =
        SynchronizeAccountUseCase(
            repository = repository,
            clock = clock,
            reconcile = reconcile,
            newLeaseOwner = { UUID.randomUUID().toString() },
        )
}
