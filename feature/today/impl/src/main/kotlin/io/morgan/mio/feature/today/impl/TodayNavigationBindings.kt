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
import io.morgan.mio.feature.library.api.LibraryKey
import io.morgan.mio.feature.media.api.MediaKey
import io.morgan.mio.feature.media.api.OpenProgressConflictRequest
import io.morgan.mio.feature.today.api.OpenDiscoverRequest
import io.morgan.mio.feature.today.api.TodayKey
import java.util.UUID
import kotlin.reflect.KClass

private object TodayNavEntryBuilder : NavEntryBuilder {
    override val destinationKeyTypes: Set<KClass<out NavKey>> = setOf(TodayKey::class)

    override fun EntryProviderScope<NavKey>.build(navigator: MioNavigator) {
        entry<TodayKey> {
            TodayRoute(
                onMediaDetail = { mediaId -> navigator.navigate(MediaKey(mediaId)) },
                onRemainingReady = {
                    navigator.navigate(LibraryKey(readyRequestId = UUID.randomUUID().toString()))
                },
                onExploreDiscover = { navigator.navigate(OpenDiscoverRequest) },
                onResolveProgressConflict = { operationId, entryId ->
                    navigator.navigate(
                        OpenProgressConflictRequest(
                            operationIdValue = operationId.value,
                            entryIdValue = entryId.value,
                        ),
                    )
                },
            )
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
