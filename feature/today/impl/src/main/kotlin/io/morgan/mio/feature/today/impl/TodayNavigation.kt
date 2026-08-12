@file:Suppress("ktlint:standard:filename")

package io.morgan.mio.feature.today.impl

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.designsystem.theme.MioTheme
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
            TodayTaskSevenPlaceholder()
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

@Composable
private fun TodayTaskSevenPlaceholder() {
    Text(
        text = "Today",
        style = MioTheme.typography.body,
        modifier = Modifier.fillMaxSize(),
    )
}
