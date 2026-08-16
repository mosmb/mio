package io.morgan.mio.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import java.util.Collections

public class AuthenticatedNavigationState internal constructor(
    topLevelDestinations: List<TopLevelDestination>,
    private val selectedSlot: MutableState<Int>,
    stacks: Map<TopLevelSlot, NavBackStack<NavKey>>,
) {
    private val destinations: List<TopLevelDestination> =
        Collections.unmodifiableList(topLevelDestinations.toList())
    private val backStacks: Map<TopLevelSlot, NavBackStack<NavKey>> =
        Collections.unmodifiableMap(stacks.toMap())
    private val stateNavigator: MioNavigator =
        AuthenticatedNavigator(
            topLevelDestinations = destinations,
            selectedSlot = selectedSlot,
            backStacks = backStacks,
        )

    public val selectedDestination: TopLevelDestination
        get() = destinations[selectedSlot.value]

    public val currentKey: NavKey
        get() = backStacks.getValue(selectedDestination.slot).last()

    public val isRootNavigationVisible: Boolean
        get() = currentKey !is FocusedDestination

    public val navigator: MioNavigator
        get() = stateNavigator

    public fun stackSnapshot(slot: TopLevelSlot): List<NavKey> =
        Collections.unmodifiableList(backStacks.getValue(slot).toList())

    @Composable
    public fun decoratedEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val todaySaveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
        val todayViewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
        val librarySaveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
        val libraryViewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
        val discoverSaveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
        val discoverViewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
        val youSaveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
        val youViewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()

        val todayEntries =
            rememberDecoratedNavEntries(
                backStack = backStacks.getValue(TopLevelSlot.TODAY),
                entryDecorators = listOf(todaySaveableStateDecorator, todayViewModelStoreDecorator),
                entryProvider = entryProvider,
            )
        val libraryEntries =
            rememberDecoratedNavEntries(
                backStack = backStacks.getValue(TopLevelSlot.LIBRARY),
                entryDecorators = listOf(librarySaveableStateDecorator, libraryViewModelStoreDecorator),
                entryProvider = entryProvider,
            )
        val discoverEntries =
            rememberDecoratedNavEntries(
                backStack = backStacks.getValue(TopLevelSlot.DISCOVER),
                entryDecorators = listOf(discoverSaveableStateDecorator, discoverViewModelStoreDecorator),
                entryProvider = entryProvider,
            )
        val youEntries =
            rememberDecoratedNavEntries(
                backStack = backStacks.getValue(TopLevelSlot.YOU),
                entryDecorators = listOf(youSaveableStateDecorator, youViewModelStoreDecorator),
                entryProvider = entryProvider,
            )

        return when (selectedDestination.slot) {
            TopLevelSlot.TODAY -> todayEntries
            TopLevelSlot.LIBRARY -> todayEntries + libraryEntries
            TopLevelSlot.DISCOVER -> todayEntries + discoverEntries
            TopLevelSlot.YOU -> todayEntries + youEntries
        }
    }
}

@Composable
public fun rememberAuthenticatedNavigationState(
    topLevelDestinations: List<TopLevelDestination>,
): AuthenticatedNavigationState {
    requireTaskSevenTopLevelOrder(topLevelDestinations)

    val destinations = remember(topLevelDestinations) { topLevelDestinations.toList() }
    val todayBackStack = rememberNavBackStack(destinations[TopLevelSlot.TODAY.ordinal].key)
    val libraryBackStack = rememberNavBackStack(destinations[TopLevelSlot.LIBRARY.ordinal].key)
    val discoverBackStack = rememberNavBackStack(destinations[TopLevelSlot.DISCOVER.ordinal].key)
    val youBackStack = rememberNavBackStack(destinations[TopLevelSlot.YOU.ordinal].key)
    val selectedSlot = rememberSaveable { mutableIntStateOf(TopLevelSlot.TODAY.ordinal) }

    return remember(destinations) {
        AuthenticatedNavigationState(
            topLevelDestinations = destinations,
            selectedSlot = selectedSlot,
            stacks =
                mapOf(
                    TopLevelSlot.TODAY to todayBackStack,
                    TopLevelSlot.LIBRARY to libraryBackStack,
                    TopLevelSlot.DISCOVER to discoverBackStack,
                    TopLevelSlot.YOU to youBackStack,
                ),
        )
    }
}

private fun requireTaskSevenTopLevelOrder(destinations: List<TopLevelDestination>) {
    require(destinations.size == TopLevelSlot.entries.size) {
        "Authenticated navigation requires exactly ${TopLevelSlot.entries.size} top-level destinations"
    }
    TopLevelSlot.entries.forEachIndexed { index, slot ->
        require(destinations[index].slot == slot) {
            "Authenticated top-level destination at index $index must be $slot"
        }
    }
}
