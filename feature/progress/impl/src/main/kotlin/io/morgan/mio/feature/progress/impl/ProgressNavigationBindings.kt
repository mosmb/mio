package io.morgan.mio.feature.progress.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.navigation.MioNavigator
import io.morgan.mio.core.navigation.NavEntryBuilder
import io.morgan.mio.feature.progress.api.ProgressConflictKey
import io.morgan.mio.feature.progress.api.ProgressEditorKey
import kotlin.reflect.KClass

private object ProgressNavEntryBuilder : NavEntryBuilder {
    override val destinationKeyTypes: Set<KClass<out NavKey>> =
        setOf(ProgressEditorKey::class, ProgressConflictKey::class)

    override fun EntryProviderScope<NavKey>.build(navigator: MioNavigator) {
        entry<ProgressEditorKey> { key ->
            ProgressEditorRoute(key = key, onBack = { navigator.goBack() })
        }
        entry<ProgressConflictKey> { key ->
            ProgressConflictRoute(key = key, onBack = { navigator.goBack() })
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
public object ProgressNavigationBindings {
    @Provides
    @IntoSet
    public fun provideProgressNavEntryBuilder(): NavEntryBuilder = ProgressNavEntryBuilder
}
