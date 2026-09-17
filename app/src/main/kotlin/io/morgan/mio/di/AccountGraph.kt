package io.morgan.mio.di

import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import io.morgan.mio.core.common.di.AccountScope
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.domain.account.AccountRepository
import io.morgan.mio.domain.collection.CollectionRepository
import io.morgan.mio.domain.media.MediaRepository
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.preferences.PreferencesRepository
import io.morgan.mio.domain.sync.RetrySyncIssueUseCase
import io.morgan.mio.domain.sync.SyncRecoveryRepository
import io.morgan.mio.domain.sync.SynchronizeAccountUseCase

@GraphExtension(AccountScope::class)
internal interface AccountGraph : ViewModelGraph {
    val accountId: AccountId
    val providerBinding: ProviderBinding
    val accountRepository: AccountRepository
    val mediaRepository: MediaRepository
    val collectionRepository: CollectionRepository
    val preferencesRepository: PreferencesRepository
    val synchronizeAccountUseCase: SynchronizeAccountUseCase
    val syncRecoveryRepository: SyncRecoveryRepository
    val retrySyncIssueUseCase: RetrySyncIssueUseCase

    @GraphExtension.Factory
    fun interface Factory {
        fun createAccountGraph(
            @Provides accountId: AccountId,
            @Provides providerBinding: ProviderBinding,
        ): AccountGraph
    }
}
