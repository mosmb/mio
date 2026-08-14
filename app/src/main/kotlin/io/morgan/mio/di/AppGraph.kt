package io.morgan.mio.di

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkerFactory
import coil3.ImageLoader
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.common.di.ApplicationContext
import io.morgan.mio.core.common.di.DefaultDispatcher
import io.morgan.mio.core.common.di.IoDispatcher
import io.morgan.mio.core.common.di.MainDispatcher
import io.morgan.mio.core.common.di.ProcessCoroutineScope
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.logging.MioLogger
import io.morgan.mio.core.navigation.NavigationRegistry
import io.morgan.mio.core.network.NetworkClientFactory
import io.morgan.mio.core.network.NetworkMonitor
import io.morgan.mio.data.provider.api.ProviderCredentialStore
import io.morgan.mio.domain.preferences.PreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock

@DependencyGraph(AppScope::class)
internal interface AppGraph : AccountGraph.Factory {
    val accountGraphManager: AccountGraphManager
    val database: MioDatabase
    val networkClientFactory: NetworkClientFactory
    val networkMonitor: NetworkMonitor
    val clock: Clock
    val preferencesRepository: PreferencesRepository
    val navigationRegistry: NavigationRegistry
    val logger: MioLogger
    val providerCredentialStore: ProviderCredentialStore
    val imageLoader: ImageLoader
    val workerFactory: WorkerFactory
    val workManagerConfiguration: Configuration

    @get:ProcessCoroutineScope
    val processCoroutineScope: CoroutineScope

    @get:DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @get:IoDispatcher
    val ioDispatcher: CoroutineDispatcher

    @get:MainDispatcher
    val mainDispatcher: CoroutineDispatcher

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides
            @ApplicationContext
            applicationContext: Context,
        ): AppGraph
    }
}
