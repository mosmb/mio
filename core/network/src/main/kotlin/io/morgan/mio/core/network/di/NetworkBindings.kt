package io.morgan.mio.core.network.di

import android.content.Context
import android.net.ConnectivityManager
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.common.di.ApplicationContext
import io.morgan.mio.core.logging.MioLogger
import io.morgan.mio.core.network.AndroidNetworkMonitor
import io.morgan.mio.core.network.LoggingNetworkEventSink
import io.morgan.mio.core.network.NetworkClientFactory
import io.morgan.mio.core.network.NetworkMonitor

@BindingContainer
@ContributesTo(AppScope::class)
public object NetworkBindings {
    @Provides
    @SingleIn(AppScope::class)
    public fun provideNetworkClientFactory(logger: MioLogger): NetworkClientFactory =
        NetworkClientFactory(eventSink = LoggingNetworkEventSink(logger))

    @Provides
    @SingleIn(AppScope::class)
    public fun provideNetworkMonitor(
        @ApplicationContext context: Context,
    ): NetworkMonitor {
        val connectivityManager =
            requireNotNull(context.getSystemService(ConnectivityManager::class.java))
        return AndroidNetworkMonitor(connectivityManager)
    }
}
