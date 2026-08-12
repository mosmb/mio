package io.morgan.mio.core.navigation

import androidx.compose.runtime.MutableState
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

internal class OnboardingNavigator(
    private val backStack: NavBackStack<NavKey>,
) : MioNavigator {
    override fun navigate(destination: NavKey) {
        backStack.add(destination)
    }

    override fun goBack(): Boolean {
        if (backStack.size <= 1) {
            return false
        }
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}

internal class AuthenticatedNavigator(
    private val topLevelDestinations: List<TopLevelDestination>,
    private val selectedSlot: MutableState<Int>,
    private val backStacks: Map<TopLevelSlot, NavBackStack<NavKey>>,
) : MioNavigator {
    override fun navigate(destination: NavKey) {
        val rootDestination = topLevelDestinations.firstOrNull { it.key == destination }
        if (rootDestination != null) {
            selectedSlot.value = rootDestination.slot.ordinal
        } else {
            backStacks.getValue(selectedSlotSlot()).add(destination)
        }
    }

    override fun goBack(): Boolean {
        val currentStack = backStacks.getValue(selectedSlotSlot())
        if (currentStack.size > 1) {
            currentStack.removeAt(currentStack.lastIndex)
            return true
        }
        if (selectedSlot.value != TopLevelSlot.TODAY.ordinal) {
            selectedSlot.value = TopLevelSlot.TODAY.ordinal
            return true
        }
        return false
    }

    private fun selectedSlotSlot(): TopLevelSlot = TopLevelSlot.entries[selectedSlot.value]
}
