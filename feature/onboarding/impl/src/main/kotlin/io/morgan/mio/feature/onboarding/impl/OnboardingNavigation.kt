@file:Suppress("ktlint:standard:filename")

package io.morgan.mio.feature.onboarding.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.navigation.MioNavigator
import io.morgan.mio.core.navigation.NavEntryBuilder
import io.morgan.mio.core.navigation.OnboardingDestination
import io.morgan.mio.feature.onboarding.api.OnboardingKey
import kotlin.reflect.KClass

private object OnboardingNavEntryBuilder : NavEntryBuilder {
    override val destinationKeyTypes: Set<KClass<out NavKey>> = setOf(OnboardingKey::class)

    override fun EntryProviderScope<NavKey>.build(navigator: MioNavigator) {
        entry<OnboardingKey> {
            OnboardingRoute()
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
public object OnboardingNavigationBindings {
    @Provides
    @IntoSet
    public fun provideOnboardingNavEntryBuilder(): NavEntryBuilder = OnboardingNavEntryBuilder

    @Provides
    public fun provideOnboardingDestination(): OnboardingDestination = OnboardingDestination(OnboardingKey)
}
