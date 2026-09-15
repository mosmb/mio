package io.morgan.mio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.data.provider.api.ProviderBindingResolver
import io.morgan.mio.data.sync.PendingSyncWorkDispatcher
import io.morgan.mio.data.sync.local.RoomSyncAccountLocalDataSource
import io.morgan.mio.domain.sync.SyncAccountSessionFactory

@BindingContainer
@ContributesTo(AppScope::class)
internal object SyncProcessBindings {
    @Provides
    @SingleIn(AppScope::class)
    internal fun provideRoomSyncAccountLocalDataSource(database: MioDatabase): RoomSyncAccountLocalDataSource =
        RoomSyncAccountLocalDataSource(database)

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideSyncAccountSessionFactory(
        accountGraphFactory: AccountGraph.Factory,
        accountLocalDataSource: RoomSyncAccountLocalDataSource,
        bindingResolver: ProviderBindingResolver,
    ): SyncAccountSessionFactory =
        MetroSyncAccountSessionFactory(accountGraphFactory, accountLocalDataSource, bindingResolver)

    @Provides
    @SingleIn(AppScope::class)
    internal fun providePendingSyncWorkDispatcher(
        database: MioDatabase,
        scheduler: io.morgan.mio.domain.sync.SyncWorkScheduler,
    ): PendingSyncWorkDispatcher = PendingSyncWorkDispatcher(database, scheduler)
}
