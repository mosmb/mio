package io.morgan.mio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.data.sync.DefaultRefreshRepository
import io.morgan.mio.data.sync.DefaultSyncRecoveryRepository
import io.morgan.mio.data.sync.DefaultSyncRepository
import io.morgan.mio.data.sync.ProviderSyncRemoteDataSource
import io.morgan.mio.data.sync.local.RoomRefreshLocalDataSource
import io.morgan.mio.data.sync.local.RoomSyncLocalDataSource
import io.morgan.mio.data.sync.local.RoomSyncRecoveryLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.ProjectSyncEntryUseCase
import io.morgan.mio.domain.sync.ReconcilePendingOperationUseCase
import io.morgan.mio.domain.sync.RefreshRepository
import io.morgan.mio.domain.sync.ResolveProgressConflictUseCase
import io.morgan.mio.domain.sync.RetrySyncIssueUseCase
import io.morgan.mio.domain.sync.SyncRecoveryRepository
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
    public fun provideRefreshRepository(
        accountId: AccountId,
        remoteDataSource: io.morgan.mio.data.sync.ProviderRefreshRemoteDataSource,
        localDataSource: RoomRefreshLocalDataSource,
    ): RefreshRepository = DefaultRefreshRepository(accountId, remoteDataSource, localDataSource)

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideSyncRecoveryRepository(
        accountId: AccountId,
        localDataSource: RoomSyncRecoveryLocalDataSource,
    ): SyncRecoveryRepository = DefaultSyncRecoveryRepository(accountId, localDataSource)

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideRetrySyncIssueUseCase(repository: SyncRecoveryRepository): RetrySyncIssueUseCase =
        RetrySyncIssueUseCase(repository)

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideResolveProgressConflictUseCase(
        repository: SyncRecoveryRepository,
        clock: kotlin.time.Clock,
    ): ResolveProgressConflictUseCase = ResolveProgressConflictUseCase(repository, clock)

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideSynchronizeAccountUseCase(
        repository: SyncRepository,
        clock: Clock,
        reconcile: ReconcilePendingOperationUseCase,
        refreshRepository: RefreshRepository,
    ): SynchronizeAccountUseCase =
        SynchronizeAccountUseCase(
            repository = repository,
            clock = clock,
            reconcile = reconcile,
            refreshRepository = refreshRepository,
            newLeaseOwner = { UUID.randomUUID().toString() },
        )
}
