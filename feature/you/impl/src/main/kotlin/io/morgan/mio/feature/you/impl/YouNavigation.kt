@file:Suppress("ktlint:standard:filename")

package io.morgan.mio.feature.you.impl

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import io.morgan.mio.feature.you.api.YouKey
import kotlin.reflect.KClass

private object YouNavEntryBuilder : NavEntryBuilder {
    override val destinationKeyTypes: Set<KClass<out NavKey>> = setOf(YouKey::class)

    override fun EntryProviderScope<NavKey>.build(navigator: MioNavigator) {
        entry<YouKey> {
            YouTaskSevenPlaceholder()
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
public object YouNavigationBindings {
    @Provides
    @IntoSet
    public fun provideYouNavEntryBuilder(): NavEntryBuilder = YouNavEntryBuilder

    @Provides
    @IntoSet
    public fun provideYouTopLevelDestination(): TopLevelDestination =
        TopLevelDestination(
            slot = TopLevelSlot.YOU,
            key = YouKey,
        )
}

@Composable
private fun YouTaskSevenPlaceholder() {
    BasicText(
        text = "You",
        modifier = Modifier.fillMaxSize(),
    )
}
