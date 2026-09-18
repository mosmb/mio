package io.morgan.mio.feature.onboarding.impl

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

internal enum class OnboardingPhase {
    RESTORING,
    WELCOME,
    AUTHORIZING,
    IMPORTING,
    DISCONNECTING,
}

internal enum class OnboardingProviderUi(
    @param:StringRes val displayNameRes: Int,
) {
    ANILIST(R.string.onboarding_provider_anilist),
    MY_ANIME_LIST(R.string.onboarding_provider_my_anime_list),
}

@Immutable
internal data class OnboardingErrorUi(
    @param:StringRes val titleRes: Int,
    @param:StringRes val messageRes: Int,
    @param:StringRes val retryLabelRes: Int?,
)

@Immutable
internal data class OnboardingUiState(
    val phase: OnboardingPhase,
    val activeProvider: OnboardingProviderUi?,
    val error: OnboardingErrorUi?,
    val connectEnabled: Boolean = true,
)
