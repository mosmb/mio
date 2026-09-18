package io.morgan.mio.feature.library.impl

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
import io.morgan.mio.feature.library.api.LibraryKey
import io.morgan.mio.feature.media.api.MediaKey
import kotlin.reflect.KClass

private object LibraryNavEntryBuilder : NavEntryBuilder {
    override val destinationKeyTypes: Set<KClass<out NavKey>> = setOf(LibraryKey::class)

    override fun EntryProviderScope<NavKey>.build(navigator: MioNavigator) {
        entry<LibraryKey> { key ->
            LibraryRoute(
                readyRequestId = key.readyRequestId,
                onMediaDetail = { mediaId -> navigator.navigate(MediaKey(mediaId)) },
            )
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
public object LibraryNavigationBindings {
    @Provides
    @IntoSet
    public fun provideLibraryNavEntryBuilder(): NavEntryBuilder = LibraryNavEntryBuilder

    @Provides
    @IntoSet
    public fun provideLibraryTopLevelDestination(): TopLevelDestination =
        TopLevelDestination(
            slot = TopLevelSlot.LIBRARY,
            key = LibraryKey(),
        )
}
