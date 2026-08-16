package io.morgan.mio.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import java.util.Collections

public class OnboardingNavigationState internal constructor(
    private val backStack: NavBackStack<NavKey>,
) {
    private val stateNavigator: MioNavigator = OnboardingNavigator(backStack)

    public val currentKey: NavKey
        get() = backStack.last()

    public val navigator: MioNavigator
        get() = stateNavigator

    public fun stackSnapshot(): List<NavKey> = Collections.unmodifiableList(backStack.toList())

    @Composable
    public fun decoratedEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val saveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
        val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
        return rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(saveableStateDecorator, viewModelStoreDecorator),
            entryProvider = entryProvider,
        )
    }
}

@Composable
public fun rememberOnboardingNavigationState(startDestination: NavKey): OnboardingNavigationState {
    val backStack = rememberNavBackStack(startDestination)
    return remember(backStack) { OnboardingNavigationState(backStack) }
}
