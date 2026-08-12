package io.morgan.mio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.common.di.DefaultDispatcher
import io.morgan.mio.core.common.di.IoDispatcher
import io.morgan.mio.core.common.di.MainDispatcher
import io.morgan.mio.core.navigation.NavEntryBuilder
import io.morgan.mio.core.navigation.NavigationRegistry
import io.morgan.mio.core.navigation.OnboardingDestination
import io.morgan.mio.core.navigation.TopLevelDestination
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
