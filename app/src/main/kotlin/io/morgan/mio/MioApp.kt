package io.morgan.mio

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import io.morgan.mio.core.designsystem.component.MioSnackbarHost
import io.morgan.mio.core.designsystem.component.MioSnackbarMessage
import io.morgan.mio.core.designsystem.layout.MioWindowInsets
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.core.navigation.MioNavigator
import io.morgan.mio.core.navigation.NavigationRegistry
import io.morgan.mio.core.navigation.TopLevelNavigationRequest
import io.morgan.mio.core.navigation.TopLevelRootNavigationRequest
import io.morgan.mio.core.navigation.rememberAuthenticatedNavigationState
import io.morgan.mio.core.navigation.rememberOnboardingNavigationState
import io.morgan.mio.core.ui.image.MioImageLoaderProvider

internal enum class AppRoot {
    ONBOARDING,
    AUTHENTICATED,
}

@Composable
internal fun MioApp(
    root: AppRoot,
    navigationRegistry: NavigationRegistry,
    imageLoader: ImageLoader,
    viewModelFactory: MetroViewModelFactory,
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotionOverride: Boolean = false,
    hapticsEnabled: Boolean = true,
    highContrastOverride: Boolean? = null,
    snackbar: MioSnackbarMessage? = null,
    onSnackbarAction: (String) -> Unit = {},
) {
    CompositionLocalProvider(
        LocalMetroViewModelFactory provides viewModelFactory,
    ) {
        MioTheme(
            darkTheme = darkTheme,
            reducedMotionOverride = reducedMotionOverride,
            hapticsEnabled = hapticsEnabled,
            highContrastOverride = highContrastOverride,
        ) {
            MioImageLoaderProvider(imageLoader) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MioTheme.colors.background,
                    contentColor = MioTheme.colors.text,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(MioWindowInsets.safeDrawing),
                    ) {
                        key(root) {
                            when (root) {
                                AppRoot.ONBOARDING -> OnboardingRoot(navigationRegistry)
                                AppRoot.AUTHENTICATED ->
                                    AuthenticatedRoot(
                                        navigationRegistry = navigationRegistry,
                                        snackbar = snackbar,
                                        onSnackbarAction = onSnackbarAction,
                                    )
                            }
                        }
                    }
                }
            }
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
private fun AuthenticatedRoot(
    navigationRegistry: NavigationRegistry,
    snackbar: MioSnackbarMessage?,
    onSnackbarAction: (String) -> Unit,
) {
    val state = rememberAuthenticatedNavigationState(navigationRegistry.topLevelDestinations)
    val retainedStackNavigator = state.navigator
    val navigator =
        remember(state, retainedStackNavigator) {
            object : MioNavigator {
                override fun navigate(destination: androidx.navigation3.runtime.NavKey) {
                    when {
                        destination is TopLevelRootNavigationRequest -> state.openTopLevel(destination.targetSlot)
                        destination is TopLevelNavigationRequest && destination.replacesTargetStack -> {
                            state.openTopLevel(destination.targetSlot, destination)
                        }
                        else -> retainedStackNavigator.navigate(destination)
                    }
                }

                override fun goBack(): Boolean = retainedStackNavigator.goBack()
            }
        }
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
        MioSnackbarHost(
            snackbar = snackbar,
            bottomNavigationVisible = state.isRootNavigationVisible,
            onAction = onSnackbarAction,
        )
    }
}
