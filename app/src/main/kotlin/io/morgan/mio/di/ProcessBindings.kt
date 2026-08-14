package io.morgan.mio.di

import android.content.Context
import androidx.lifecycle.ViewModel
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.common.di.ApplicationContext
import io.morgan.mio.core.common.di.DefaultDispatcher
import io.morgan.mio.core.common.di.IoDispatcher
import io.morgan.mio.core.common.di.MainDispatcher
import io.morgan.mio.core.common.di.ProcessCoroutineScope
import io.morgan.mio.core.navigation.NavEntryBuilder
import io.morgan.mio.core.navigation.NavigationRegistry
import io.morgan.mio.core.navigation.OnboardingDestination
import io.morgan.mio.core.navigation.TopLevelDestination
import io.morgan.mio.core.network.NetworkClientFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass
import kotlin.time.Clock

@BindingContainer
@ContributesTo(AppScope::class)
internal object ProcessBindings {
    @Provides
    @SingleIn(AppScope::class)
    internal fun provideClock(): Clock = Clock.System

    @Provides
    @SingleIn(AppScope::class)
    @DefaultDispatcher
    internal fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @SingleIn(AppScope::class)
    @IoDispatcher
    internal fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @SingleIn(AppScope::class)
    @MainDispatcher
    internal fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @SingleIn(AppScope::class)
    @ProcessCoroutineScope
    internal fun provideProcessCoroutineScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideImageLoader(
        @ApplicationContext context: Context,
        networkClientFactory: NetworkClientFactory,
    ): ImageLoader {
        val imageHttpClient = networkClientFactory.sharedClient.newBuilder().build()
        return ImageLoader
            .Builder(context.applicationContext)
            .components {
                add(OkHttpNetworkFetcherFactory(imageHttpClient))
            }.build()
    }

    @Provides
    @SingleIn(AppScope::class)
    internal fun provideNavigationRegistry(
        onboardingDestination: OnboardingDestination,
        topLevelDestinations: Set<TopLevelDestination>,
        entryBuilders: Set<NavEntryBuilder>,
    ): NavigationRegistry =
        NavigationRegistry(
            onboardingDestination = onboardingDestination,
            topLevelDestinations = topLevelDestinations,
            entryBuilders = entryBuilders,
        )
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
internal class DefaultMetroViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
) : MetroViewModelFactory()
