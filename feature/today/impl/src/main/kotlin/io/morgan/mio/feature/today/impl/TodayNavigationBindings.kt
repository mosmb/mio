package io.morgan.mio.feature.today.impl

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
import io.morgan.mio.feature.today.api.TodayKey
import kotlin.reflect.KClass

private object TodayNavEntryBuilder : NavEntryBuilder {
    override val destinationKeyTypes: Set<KClass<out NavKey>> = setOf(TodayKey::class)

    override fun EntryProviderScope<NavKey>.build(navigator: MioNavigator) {
        entry<TodayKey> {
            TodayRoute()
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
public object TodayNavigationBindings {
    @Provides
    @IntoSet
    public fun provideTodayNavEntryBuilder(): NavEntryBuilder = TodayNavEntryBuilder

    @Provides
    @IntoSet
    public fun provideTodayTopLevelDestination(): TopLevelDestination =
        TopLevelDestination(
            slot = TopLevelSlot.TODAY,
            key = TodayKey,
        )
}
