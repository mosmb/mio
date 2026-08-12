package io.morgan.mio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.NavDisplay
import io.morgan.mio.core.navigation.NavigationRegistry
import io.morgan.mio.core.navigation.rememberAuthenticatedNavigationState
import io.morgan.mio.core.navigation.rememberOnboardingNavigationState

internal enum class AppRoot {
    ONBOARDING,
    AUTHENTICATED,
}

@Composable
internal fun MioApp(
    root: AppRoot,
    navigationRegistry: NavigationRegistry,
) {
    key(root) {
        when (root) {
            AppRoot.ONBOARDING -> OnboardingRoot(navigationRegistry)
            AppRoot.AUTHENTICATED -> AuthenticatedRoot(navigationRegistry)
        }
    }
}

@Composable
private fun OnboardingRoot(navigationRegistry: NavigationRegistry) {
    val state =
        rememberOnboardingNavigationState(
            startDestination = navigationRegistry.onboardingDestination.key,
        )
    val navigator = state.navigator
    val entryProvider = remember(navigator) { navigationRegistry.entryProvider(navigator) }

    NavDisplay(
        modifier = Modifier.fillMaxSize(),
        entries = state.decoratedEntries(entryProvider),
        onBack = { navigator.goBack() },
    )
}

@Composable
private fun AuthenticatedRoot(navigationRegistry: NavigationRegistry) {
    val state = rememberAuthenticatedNavigationState(navigationRegistry.topLevelDestinations)
    val navigator = state.navigator
    val entryProvider = remember(navigator) { navigationRegistry.entryProvider(navigator) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            modifier = Modifier.fillMaxSize(),
            entries = state.decoratedEntries(entryProvider),
            onBack = { navigator.goBack() },
        )
        if (state.isRootNavigationVisible) {
            RootNavigationBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                destinations = navigationRegistry.topLevelDestinations,
                selectedSlot = state.selectedDestination.slot,
                onSelected = { destination -> navigator.navigate(destination.key) },
            )
        }
    }
}
