package io.morgan.mio.feature.onboarding.impl

import io.morgan.mio.domain.account.AccountSessionFailureReason
import io.morgan.mio.domain.account.AccountSessionOperation
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider

internal fun mapOnboardingUiState(state: AccountSessionState): OnboardingUiState =
    when (state) {
        AccountSessionState.Restoring ->
            OnboardingUiState(
                phase = OnboardingPhase.RESTORING,
                activeProvider = null,
                error = null,
            )
        AccountSessionState.SignedOut ->
            OnboardingUiState(
                phase = OnboardingPhase.WELCOME,
                activeProvider = null,
                error = null,
            )
        is AccountSessionState.Authorizing ->
            OnboardingUiState(
                phase = OnboardingPhase.AUTHORIZING,
                activeProvider = state.provider.toUiProvider(),
                error = null,
            )
        is AccountSessionState.Importing ->
            OnboardingUiState(
                phase = OnboardingPhase.IMPORTING,
                activeProvider = state.provider.toUiProvider(),
                error = null,
            )
        is AccountSessionState.Authenticated ->
            OnboardingUiState(
                phase = OnboardingPhase.IMPORTING,
                activeProvider = state.provider.toUiProvider(),
                error = null,
            )
        is AccountSessionState.Disconnecting ->
            OnboardingUiState(
                phase = OnboardingPhase.DISCONNECTING,
                activeProvider = state.provider.toUiProvider(),
                error = null,
                connectEnabled = false,
            )
        is AccountSessionState.Failed ->
            OnboardingUiState(
                phase = OnboardingPhase.WELCOME,
                activeProvider = state.provider?.toUiProvider(),
                error = state.toErrorUi(),
                connectEnabled = state.operation != AccountSessionOperation.DISCONNECTION,
            )
    }

private fun AccountSessionState.Failed.toErrorUi(): OnboardingErrorUi =
    OnboardingErrorUi(
        titleRes = errorTitleRes,
        messageRes = errorMessageRes,
        retryLabelRes = if (retryable) R.string.onboarding_retry else null,
    )

private val AccountSessionState.Failed.errorTitleRes: Int
    get() =
        if (reason == AccountSessionFailureReason.UNAVAILABLE) {
            R.string.onboarding_error_unavailable_title
        } else {
            when (operation) {
                AccountSessionOperation.RESTORATION -> R.string.onboarding_error_restoration_title
                AccountSessionOperation.AUTHORIZATION -> R.string.onboarding_error_authorization_title
                AccountSessionOperation.AUTHENTICATION -> R.string.onboarding_error_authentication_title
                AccountSessionOperation.INITIAL_IMPORT -> R.string.onboarding_error_initial_import_title
                AccountSessionOperation.DISCONNECTION -> R.string.onboarding_error_disconnect_title
            }
        }

private val AccountSessionState.Failed.errorMessageRes: Int
    get() =
        if (reason == AccountSessionFailureReason.UNAVAILABLE) {
            R.string.onboarding_error_unavailable_message
        } else {
            when (operation) {
                AccountSessionOperation.RESTORATION -> R.string.onboarding_error_restoration_message
                AccountSessionOperation.AUTHORIZATION -> R.string.onboarding_error_authorization_message
                AccountSessionOperation.AUTHENTICATION -> R.string.onboarding_error_authentication_message
                AccountSessionOperation.INITIAL_IMPORT -> R.string.onboarding_error_initial_import_message
                AccountSessionOperation.DISCONNECTION -> R.string.onboarding_error_disconnect_message
            }
        }

private fun ConnectedProvider.toUiProvider(): OnboardingProviderUi =
    when (this) {
        ConnectedProvider.ANILIST -> OnboardingProviderUi.ANILIST
        ConnectedProvider.MY_ANIME_LIST -> OnboardingProviderUi.MY_ANIME_LIST
    }
