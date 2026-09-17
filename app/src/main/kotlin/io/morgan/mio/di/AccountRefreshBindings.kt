package io.morgan.mio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.data.account.AccountRefreshRequester
import io.morgan.mio.data.collection.CollectionRefreshRequester
import io.morgan.mio.domain.sync.SynchronizeAccountUseCase

@BindingContainer
@ContributesTo(AccountScope::class)
internal object AccountRefreshBindings {
    @Provides
    @SingleIn(AccountScope::class)
    fun provideAccountRefreshRequester(synchronize: SynchronizeAccountUseCase): AccountRefreshRequester =
        AccountRefreshRequester { synchronize(refreshContent = true) }

    @Provides
    @SingleIn(AccountScope::class)
    fun provideCollectionRefreshRequester(synchronize: SynchronizeAccountUseCase): CollectionRefreshRequester =
        CollectionRefreshRequester { synchronize(refreshContent = true) }
}
