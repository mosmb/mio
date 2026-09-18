package io.morgan.mio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.data.progress.DefaultProgressActivityRepository
import io.morgan.mio.data.progress.DefaultProgressRepository
import io.morgan.mio.data.progress.local.RoomProgressLocalDataSource
import io.morgan.mio.data.sync.PendingOperationWriter
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.progress.CorrectProgressUseCase
import io.morgan.mio.domain.progress.ObserveProgressUndoUseCase
import io.morgan.mio.domain.progress.ProgressActivityRepository
import io.morgan.mio.domain.progress.ProgressRepository
import io.morgan.mio.domain.progress.StepProgressUseCase
import io.morgan.mio.domain.progress.UndoProgressUseCase
import kotlin.time.Clock

@BindingContainer
@ContributesTo(AccountScope::class)
public object ProgressBindings {
    @Provides
    @SingleIn(AccountScope::class)
    public fun provideRoomProgressLocalDataSource(database: MioDatabase): RoomProgressLocalDataSource =
        RoomProgressLocalDataSource(database)

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideProgressRepository(
        accountId: AccountId,
        database: MioDatabase,
        localDataSource: RoomProgressLocalDataSource,
        pendingOperationWriter: PendingOperationWriter,
        clock: Clock,
    ): ProgressRepository =
        DefaultProgressRepository(
            accountId = accountId,
            database = database,
            localDataSource = localDataSource,
            pendingOperationWriter = pendingOperationWriter,
            clock = clock,
        )

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideProgressActivityRepository(
        localDataSource: RoomProgressLocalDataSource,
    ): ProgressActivityRepository = DefaultProgressActivityRepository(localDataSource)

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideStepProgressUseCase(
        repository: ProgressRepository,
        clock: Clock,
    ): StepProgressUseCase = StepProgressUseCase(repository, clock)

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideCorrectProgressUseCase(
        repository: ProgressRepository,
        clock: Clock,
    ): CorrectProgressUseCase = CorrectProgressUseCase(repository, clock)

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideUndoProgressUseCase(
        repository: ProgressRepository,
        clock: Clock,
    ): UndoProgressUseCase = UndoProgressUseCase(repository, clock)

    @Provides
    @SingleIn(AccountScope::class)
    public fun provideObserveProgressUndoUseCase(
        repository: ProgressRepository,
        clock: Clock,
    ): ObserveProgressUndoUseCase = ObserveProgressUndoUseCase(repository, clock)
}
