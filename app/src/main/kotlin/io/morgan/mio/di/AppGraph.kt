package io.morgan.mio.di

import android.content.Context
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.common.di.ApplicationContext
import io.morgan.mio.core.common.di.DefaultDispatcher
import io.morgan.mio.core.common.di.IoDispatcher
import io.morgan.mio.core.common.di.MainDispatcher
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.network.NetworkClientFactory
import io.morgan.mio.core.network.NetworkMonitor
import io.morgan.mio.domain.preferences.PreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.time.Clock

@DependencyGraph(AppScope::class)
internal interface AppGraph : AccountGraph.Factory {
    val accountGraphManager: AccountGraphManager
    val database: MioDatabase
    val networkClientFactory: NetworkClientFactory
    val networkMonitor: NetworkMonitor
    val clock: Clock
    val preferencesRepository: PreferencesRepository

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
