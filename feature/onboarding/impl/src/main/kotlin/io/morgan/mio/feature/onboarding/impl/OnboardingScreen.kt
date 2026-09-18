package io.morgan.mio.feature.onboarding.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.morgan.mio.core.designsystem.accessibility.mioHeading
import io.morgan.mio.core.designsystem.component.MioButton
import io.morgan.mio.core.designsystem.component.MioButtonStyle
import io.morgan.mio.core.designsystem.preview.MioPreviewConfiguration
import io.morgan.mio.core.designsystem.preview.MioPreviewSurface
import io.morgan.mio.core.designsystem.theme.MioTheme
import io.morgan.mio.feature.onboarding.impl.component.OnboardingMosaic

@Composable
internal fun OnboardingRoute(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<OnboardingViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authorizationLaunch by viewModel.authorizationLaunch.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(authorizationLaunch, uriHandler) {
        val launch = authorizationLaunch ?: return@LaunchedEffect
        try {
            uriHandler.openUri(launch.authorizationUri)
            viewModel.onAuthorizationOpened(launch)
        } catch (_: RuntimeException) {
            viewModel.onAuthorizationLaunchFailed(launch)
        }
    }

    OnboardingScreen(
        modifier = modifier,
        state = uiState,
        onContinueWithAniList = { viewModel.onAction(OnboardingAction.ContinueWithAniList) },
        onContinueWithMyAnimeList = { viewModel.onAction(OnboardingAction.ContinueWithMyAnimeList) },
        onRetry = { viewModel.onAction(OnboardingAction.Retry) },
    )
}

@Composable
internal fun OnboardingScreen(
    modifier: Modifier = Modifier,
    state: OnboardingUiState,
    onContinueWithAniList: () -> Unit,
    onContinueWithMyAnimeList: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = MioTheme.colors
    val typography = MioTheme.typography
    val spacing = MioTheme.spacing
    val scrollState = rememberScrollState()
    val providerName =
        state.activeProvider?.let { provider ->
            androidx.compose.ui.res
                .stringResource(provider.displayNameRes)
        }
    val error = state.error
    val errorTitle =
        if (error == null) {
            null
        } else if (providerName == null) {
            androidx.compose.ui.res
                .stringResource(error.titleRes)
        } else {
            androidx.compose.ui.res
                .stringResource(error.titleRes, providerName)
        }
    val errorMessage =
        if (error == null) {
            null
        } else if (providerName == null) {
            androidx.compose.ui.res
                .stringResource(error.messageRes)
        } else {
            androidx.compose.ui.res
                .stringResource(error.messageRes, providerName)
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = 24.dp,
                    vertical = 32.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Text(
            text =
                androidx.compose.ui.res
                    .stringResource(R.string.onboarding_wordmark),
            style = typography.section,
            color = colors.text,
        )
        Text(
            text =
                androidx.compose.ui.res
                    .stringResource(R.string.onboarding_title),
            style = typography.title,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text =
                androidx.compose.ui.res
                    .stringResource(R.string.onboarding_source_of_truth),
            style = typography.body,
            color = colors.textMuted,
        )

        when (state.phase) {
            OnboardingPhase.RESTORING ->
                OnboardingProgressStatus(
                    text =
                        androidx.compose.ui.res
                            .stringResource(R.string.onboarding_status_restoring),
                )
            OnboardingPhase.AUTHORIZING ->
                OnboardingProgressStatus(
                    text =
                        androidx.compose.ui.res.stringResource(
                            R.string.onboarding_status_authorizing,
                            requireNotNull(providerName),
                        ),
                )
            OnboardingPhase.IMPORTING -> {
                OnboardingProgressStatus(
                    text =
                        androidx.compose.ui.res
                            .stringResource(R.string.onboarding_status_importing),
                )
                OnboardingMosaic(
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text =
                        androidx.compose.ui.res.stringResource(
                            R.string.onboarding_import_source_note,
                            requireNotNull(providerName),
                        ),
                    style = typography.body,
                    color = colors.textMuted,
                )
            }
            OnboardingPhase.DISCONNECTING ->
                OnboardingProgressStatus(
                    text =
                        androidx.compose.ui.res
                            .stringResource(R.string.onboarding_status_disconnecting),
                )
            OnboardingPhase.WELCOME -> {
                if (errorTitle != null && errorMessage != null) {
                    OnboardingInlineError(
                        title = errorTitle,
                        message = errorMessage,
                        retryLabel =
                            error?.retryLabelRes?.let { labelRes ->
                                androidx.compose.ui.res
                                    .stringResource(labelRes)
                            },
                        onRetry = onRetry,
                    )
                }
            }
        }

        if (state.phase != OnboardingPhase.IMPORTING) {
            MioButton(
                text =
                    androidx.compose.ui.res
                        .stringResource(R.string.onboarding_continue_anilist),
                onClick = onContinueWithAniList,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.phase == OnboardingPhase.WELCOME && state.connectEnabled,
                style = MioButtonStyle.PRIMARY,
            )
            MioButton(
                text =
                    androidx.compose.ui.res
                        .stringResource(R.string.onboarding_continue_my_anime_list),
                onClick = onContinueWithMyAnimeList,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.phase == OnboardingPhase.WELCOME && state.connectEnabled,
                style = MioButtonStyle.SECONDARY,
            )
            Text(
                text =
                    androidx.compose.ui.res
                        .stringResource(R.string.onboarding_disclaimer),
                style = typography.caption,
                color = colors.textMuted,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun OnboardingProgressStatus(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier =
                Modifier
                    .size(20.dp)
                    .semantics { contentDescription = text },
            strokeWidth = 2.dp,
        )
        Text(
            text = text,
            style = MioTheme.typography.bodyStrong,
            color = MioTheme.colors.text,
        )
    }
}

@Composable
private fun OnboardingInlineError(
    title: String,
    message: String,
    retryLabel: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MioTheme.typography.section,
            color = MioTheme.colors.error,
            modifier = Modifier.mioHeading(),
        )
        Text(
            text = message,
            style = MioTheme.typography.body,
            color = MioTheme.colors.textMuted,
        )
        if (retryLabel != null) {
            MioButton(
                text = retryLabel,
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                style = MioButtonStyle.SECONDARY,
            )
        }
    }
}

private val previewWelcomeState =
    OnboardingUiState(
        phase = OnboardingPhase.WELCOME,
        activeProvider = null,
        error = null,
    )

private val previewRestoringState = previewWelcomeState.copy(phase = OnboardingPhase.RESTORING)
private val previewAuthorizingState =
    previewWelcomeState.copy(
        phase = OnboardingPhase.AUTHORIZING,
        activeProvider = OnboardingProviderUi.ANILIST,
    )
private val previewAuthenticationErrorState =
    previewWelcomeState.copy(
        activeProvider = OnboardingProviderUi.ANILIST,
        error =
            OnboardingErrorUi(
                titleRes = R.string.onboarding_error_authentication_title,
                messageRes = R.string.onboarding_error_authentication_message,
                retryLabelRes = R.string.onboarding_retry,
            ),
    )
private val previewImportAniListState =
    previewWelcomeState.copy(
        phase = OnboardingPhase.IMPORTING,
        activeProvider = OnboardingProviderUi.ANILIST,
    )

private val noopOnboardingAction: () -> Unit = {}

@Preview(name = "Welcome light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun OnboardingScreenWelcomeLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        OnboardingScreen(
            state = previewWelcomeState,
            onContinueWithAniList = noopOnboardingAction,
            onContinueWithMyAnimeList = noopOnboardingAction,
            onRetry = noopOnboardingAction,
        )
    }
}

@Preview(
    name = "Welcome dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun OnboardingScreenWelcomeDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) {
        OnboardingScreen(
            state = previewWelcomeState,
            onContinueWithAniList = noopOnboardingAction,
            onContinueWithMyAnimeList = noopOnboardingAction,
            onRetry = noopOnboardingAction,
        )
    }
}

@Preview(name = "Welcome compact", widthDp = 320, heightDp = 693, showBackground = true)
@Composable
internal fun OnboardingScreenWelcomeCompactPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        OnboardingScreen(
            state = previewWelcomeState,
            onContinueWithAniList = noopOnboardingAction,
            onContinueWithMyAnimeList = noopOnboardingAction,
            onRetry = noopOnboardingAction,
        )
    }
}

@Preview(name = "Welcome large text", widthDp = 393, heightDp = 852, fontScale = 2f, showBackground = true)
@Composable
internal fun OnboardingScreenWelcomeLargeTextPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        OnboardingScreen(
            state = previewWelcomeState,
            onContinueWithAniList = noopOnboardingAction,
            onContinueWithMyAnimeList = noopOnboardingAction,
            onRetry = noopOnboardingAction,
        )
    }
}

@Preview(name = "Restoring", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun OnboardingScreenRestoringPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        OnboardingScreen(
            state = previewRestoringState,
            onContinueWithAniList = noopOnboardingAction,
            onContinueWithMyAnimeList = noopOnboardingAction,
            onRetry = noopOnboardingAction,
        )
    }
}

@Preview(name = "Authorizing AniList", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun OnboardingScreenAuthorizingAniListPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        OnboardingScreen(
            state = previewAuthorizingState,
            onContinueWithAniList = noopOnboardingAction,
            onContinueWithMyAnimeList = noopOnboardingAction,
            onRetry = noopOnboardingAction,
        )
    }
}

@Preview(name = "Authentication error", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun OnboardingScreenAuthenticationErrorPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        OnboardingScreen(
            state = previewAuthenticationErrorState,
            onContinueWithAniList = noopOnboardingAction,
            onContinueWithMyAnimeList = noopOnboardingAction,
            onRetry = noopOnboardingAction,
        )
    }
}

@Preview(name = "Import normal light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun OnboardingScreenImportNormalLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        OnboardingScreen(
            state = previewImportAniListState,
            onContinueWithAniList = noopOnboardingAction,
            onContinueWithMyAnimeList = noopOnboardingAction,
            onRetry = noopOnboardingAction,
        )
    }
}

@Preview(
    name = "Import normal dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun OnboardingScreenImportNormalDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Dark) {
        OnboardingScreen(
            state = previewImportAniListState,
            onContinueWithAniList = noopOnboardingAction,
            onContinueWithMyAnimeList = noopOnboardingAction,
            onRetry = noopOnboardingAction,
        )
    }
}

@Preview(name = "Import reduced light", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
internal fun OnboardingScreenImportReducedLightPreview() {
    MioPreviewSurface(MioPreviewConfiguration.ReducedMotionLight) {
        OnboardingScreen(
            state = previewImportAniListState,
            onContinueWithAniList = noopOnboardingAction,
            onContinueWithMyAnimeList = noopOnboardingAction,
            onRetry = noopOnboardingAction,
        )
    }
}

@Preview(
    name = "Import reduced dark",
    widthDp = 393,
    heightDp = 852,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
internal fun OnboardingScreenImportReducedDarkPreview() {
    MioPreviewSurface(MioPreviewConfiguration.ReducedMotionDark) {
        OnboardingScreen(
            state = previewImportAniListState,
            onContinueWithAniList = noopOnboardingAction,
            onContinueWithMyAnimeList = noopOnboardingAction,
            onRetry = noopOnboardingAction,
        )
    }
}

@Preview(name = "Import large text", widthDp = 393, heightDp = 852, fontScale = 2f, showBackground = true)
@Composable
internal fun OnboardingScreenImportLargeTextPreview() {
    MioPreviewSurface(MioPreviewConfiguration.Light) {
        OnboardingScreen(
            state = previewImportAniListState,
            onContinueWithAniList = noopOnboardingAction,
            onContinueWithMyAnimeList = noopOnboardingAction,
            onRetry = noopOnboardingAction,
        )
    }
}
