package io.morgan.mio.feature.media.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.navigation.MioNavigator
import io.morgan.mio.core.navigation.NavEntryBuilder
import io.morgan.mio.feature.media.api.MediaKey
import kotlin.reflect.KClass

private object MediaNavEntryBuilder : NavEntryBuilder {
    override val destinationKeyTypes: Set<KClass<out NavKey>> = setOf(MediaKey::class)

    override fun EntryProviderScope<NavKey>.build(navigator: MioNavigator) {
        entry<MediaKey> { key ->
            MediaDetailRoute(
                mediaId = key.mediaId,
                onBack = { navigator.goBack() },
                onRelatedTitle = { relatedId ->
                    navigator.navigate(MediaKey(relatedId))
                },
            )
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
public object MediaNavigationBindings {
    @Provides
    @IntoSet
    public fun provideMediaNavEntryBuilder(): NavEntryBuilder = MediaNavEntryBuilder
}
