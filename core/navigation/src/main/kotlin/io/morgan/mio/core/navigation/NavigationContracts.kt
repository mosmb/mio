package io.morgan.mio.core.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlin.reflect.KClass

public enum class TopLevelSlot {
    TODAY,
    LIBRARY,
    DISCOVER,
    YOU,
}

public data class TopLevelDestination(
    public val slot: TopLevelSlot,
    public val key: NavKey,
)

public data class OnboardingDestination(
    public val key: NavKey,
)

public interface FocusedDestination : NavKey

public interface MioNavigator {
    public fun navigate(destination: NavKey): Unit

    public fun goBack(): Boolean
}

public interface NavEntryBuilder {
    public val destinationKeyTypes: Set<KClass<out NavKey>>

    public fun EntryProviderScope<NavKey>.build(navigator: MioNavigator): Unit
}
