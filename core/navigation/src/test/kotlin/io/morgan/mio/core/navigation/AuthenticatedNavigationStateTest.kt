package io.morgan.mio.core.navigation

import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedNavigationStateTest {
    @Test
    fun everyStackBeginsWithItsOwnRoot() {
        val state = state()

        assertEquals(listOf(AuthTodayTestKey), state.stackSnapshot(TopLevelSlot.TODAY))
        assertEquals(listOf(AuthLibraryTestKey), state.stackSnapshot(TopLevelSlot.LIBRARY))
        assertEquals(listOf(AuthDiscoverTestKey), state.stackSnapshot(TopLevelSlot.DISCOVER))
        assertEquals(listOf(AuthYouTestKey), state.stackSnapshot(TopLevelSlot.YOU))
    }

    @Test
    fun switchingTabsPreservesEachStackIndependently() {
        val state = state()
        val navigator = state.navigator

        navigator.navigate(AuthTodayNestedKey)
        navigator.navigate(AuthLibraryTestKey)
        navigator.navigate(AuthLibraryNestedKey)
        navigator.navigate(AuthTodayTestKey)

        assertEquals(listOf(AuthTodayTestKey, AuthTodayNestedKey), state.stackSnapshot(TopLevelSlot.TODAY))
        assertEquals(listOf(AuthLibraryTestKey, AuthLibraryNestedKey), state.stackSnapshot(TopLevelSlot.LIBRARY))
        assertEquals(TopLevelSlot.TODAY, state.selectedDestination.slot)
    }

    @Test
    fun explicitTopLevelEntryReplacesOnlyTheTargetStack() {
        val state = state()
        val navigator = state.navigator
        navigator.navigate(AuthTodayNestedKey)
        navigator.navigate(AuthLibraryTestKey)
        navigator.navigate(AuthLibraryNestedKey)
        navigator.navigate(AuthDiscoverTestKey)
        navigator.navigate(AuthDiscoverNestedKey)

        state.openTopLevel(TopLevelSlot.LIBRARY, AuthReadyLibraryKey("request-1"))

        assertEquals(TopLevelSlot.LIBRARY, state.selectedDestination.slot)
        assertEquals(listOf(AuthReadyLibraryKey("request-1")), state.stackSnapshot(TopLevelSlot.LIBRARY))
        assertEquals(listOf(AuthTodayTestKey, AuthTodayNestedKey), state.stackSnapshot(TopLevelSlot.TODAY))
        assertEquals(
            listOf(AuthDiscoverTestKey, AuthDiscoverNestedKey),
            state.stackSnapshot(TopLevelSlot.DISCOVER),
        )
        assertEquals(listOf(AuthYouTestKey), state.stackSnapshot(TopLevelSlot.YOU))
    }

    @Test
    fun topLevelRootRequestSelectsTheRegisteredRootAndResetsOnlyThatStack() {
        val state = state()
        val navigator = state.navigator
        navigator.navigate(AuthTodayNestedKey)
        navigator.navigate(AuthDiscoverTestKey)
        navigator.navigate(AuthDiscoverNestedKey)

        state.openTopLevel(TopLevelSlot.DISCOVER)

        assertEquals(TopLevelSlot.DISCOVER, state.selectedDestination.slot)
        assertEquals(listOf(AuthDiscoverTestKey), state.stackSnapshot(TopLevelSlot.DISCOVER))
        assertEquals(listOf(AuthTodayTestKey, AuthTodayNestedKey), state.stackSnapshot(TopLevelSlot.TODAY))
    }

    @Test
    fun selectingARootNeverPushesIntoEitherStack() {
        val state = state()
        val navigator = state.navigator

        navigator.navigate(AuthLibraryTestKey)
        navigator.navigate(AuthTodayTestKey)

        assertEquals(listOf(AuthTodayTestKey), state.stackSnapshot(TopLevelSlot.TODAY))
        assertEquals(listOf(AuthLibraryTestKey), state.stackSnapshot(TopLevelSlot.LIBRARY))
    }

    @Test
    fun backPopsOnlyTheSelectedNestedStack() {
        val state = state()
        val navigator = state.navigator

        navigator.navigate(AuthTodayNestedKey)
        navigator.navigate(AuthTodayNestedKeyTwo)
        assertTrue(navigator.goBack())

        assertEquals(listOf(AuthTodayTestKey, AuthTodayNestedKey), state.stackSnapshot(TopLevelSlot.TODAY))
        assertEquals(listOf(AuthLibraryTestKey), state.stackSnapshot(TopLevelSlot.LIBRARY))
    }

    @Test
    fun backAtANonTodayRootSelectsTodayAndPreservesAllStacks() {
        val state = state()
        val navigator = state.navigator

        navigator.navigate(AuthLibraryTestKey)
        navigator.navigate(AuthLibraryNestedKey)
        assertTrue(navigator.goBack())
        assertEquals(TopLevelSlot.LIBRARY, state.selectedDestination.slot)
        assertEquals(listOf(AuthLibraryTestKey), state.stackSnapshot(TopLevelSlot.LIBRARY))

        assertTrue(navigator.goBack())
        assertEquals(TopLevelSlot.TODAY, state.selectedDestination.slot)
        assertEquals(listOf(AuthLibraryTestKey), state.stackSnapshot(TopLevelSlot.LIBRARY))
    }

    @Test
    fun backAtTodayRootReturnsFalseAndKeepsTodayPresent() {
        val state = state()

        assertFalse(state.navigator.goBack())
        assertEquals(AuthTodayTestKey, state.currentKey)
    }

    @Test
    fun ordinaryNestedDestinationKeepsRootNavigationVisible() {
        val state = state()

        state.navigator.navigate(AuthTodayNestedKey)

        assertTrue(state.isRootNavigationVisible)
    }

    @Test
    fun focusedDestinationHidesRootNavigationAndBackRevealsIt() {
        val state = state()

        state.navigator.navigate(AuthFocusedTestKey)
        assertFalse(state.isRootNavigationVisible)
        assertTrue(state.navigator.goBack())
        assertTrue(state.isRootNavigationVisible)
    }

    @Test
    fun stackSnapshotCannotBeMutatedByCallers() {
        val state = state()
        val snapshot = state.stackSnapshot(TopLevelSlot.TODAY)

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (snapshot as MutableList<NavKey>).add(AuthTodayNestedKey)
        }
        assertEquals(listOf(AuthTodayTestKey), state.stackSnapshot(TopLevelSlot.TODAY))
    }

    private fun state(): AuthenticatedNavigationState {
        val destinations =
            listOf(
                TopLevelDestination(TopLevelSlot.TODAY, AuthTodayTestKey),
                TopLevelDestination(TopLevelSlot.LIBRARY, AuthLibraryTestKey),
                TopLevelDestination(TopLevelSlot.DISCOVER, AuthDiscoverTestKey),
                TopLevelDestination(TopLevelSlot.YOU, AuthYouTestKey),
            )
        val stacks =
            destinations.associate { destination ->
                destination.slot to NavBackStack<NavKey>(destination.key)
            }
        return AuthenticatedNavigationState(
            topLevelDestinations = destinations,
            selectedSlot = mutableIntStateOf(TopLevelSlot.TODAY.ordinal),
            stacks = stacks,
        )
    }
}

@Serializable
private data object AuthTodayTestKey : NavKey

@Serializable
private data object AuthLibraryTestKey : NavKey

@Serializable
private data object AuthDiscoverTestKey : NavKey

@Serializable
private data object AuthYouTestKey : NavKey

@Serializable
private data object AuthTodayNestedKey : NavKey

@Serializable
private data object AuthTodayNestedKeyTwo : NavKey

@Serializable
private data object AuthLibraryNestedKey : NavKey

@Serializable
private data object AuthDiscoverNestedKey : NavKey

@Serializable
private data class AuthReadyLibraryKey(
    val requestId: String,
) : NavKey

@Serializable
private data object AuthFocusedTestKey : FocusedDestination
