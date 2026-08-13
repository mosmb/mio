@file:Suppress("ktlint:standard:filename")

package io.morgan.mio.feature.discover.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.navigation.MioNavigator
import io.morgan.mio.core.navigation.NavEntryBuilder
import io.morgan.mio.core.navigation.TopLevelDestination
import io.morgan.mio.core.navigation.TopLevelSlot
import io.morgan.mio.feature.discover.api.DiscoverKey
import kotlin.reflect.KClass

private object DiscoverNavEntryBuilder : NavEntryBuilder {
    override val destinationKeyTypes: Set<KClass<out NavKey>> = setOf(DiscoverKey::class)

    override fun EntryProviderScope<NavKey>.build(navigator: MioNavigator) {
        entry<DiscoverKey> {
            DiscoverRoute()
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
public object DiscoverNavigationBindings {
    @Provides
    @IntoSet
    public fun provideDiscoverNavEntryBuilder(): NavEntryBuilder = DiscoverNavEntryBuilder

    @Provides
    @IntoSet
    public fun provideDiscoverTopLevelDestination(): TopLevelDestination =
        TopLevelDestination(
            slot = TopLevelSlot.DISCOVER,
            key = DiscoverKey,
        )
}
