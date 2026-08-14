package io.morgan.mio.feature.onboarding.impl

import io.morgan.mio.domain.account.AccountSessionFailureReason
import io.morgan.mio.domain.account.AccountSessionOperation
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.model.AccountId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUiMapperTest {
    @Test
    fun mapsEveryDomainStateToTheFeatureContract() {
        val states =
            listOf(
                AccountSessionState.Restoring to OnboardingPhase.RESTORING,
                AccountSessionState.SignedOut to OnboardingPhase.WELCOME,
                AccountSessionState.Authorizing(ConnectedProvider.ANILIST) to OnboardingPhase.AUTHORIZING,
                AccountSessionState.Importing(ConnectedProvider.MY_ANIME_LIST) to OnboardingPhase.IMPORTING,
                AccountSessionState.Authenticated(
                    AccountId("account-1"),
                    ConnectedProvider.ANILIST,
                ) to OnboardingPhase.IMPORTING,
                AccountSessionState.Failed(
                    provider = ConnectedProvider.MY_ANIME_LIST,
                    operation = AccountSessionOperation.AUTHENTICATION,
                    reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                    retryable = true,
                ) to OnboardingPhase.WELCOME,
            )

        states.forEach { (domainState, expectedPhase) ->
            assertEquals(expectedPhase, mapOnboardingUiState(domainState).phase)
        }
        assertEquals(
            OnboardingProviderUi.MY_ANIME_LIST,
            mapOnboardingUiState(AccountSessionState.Importing(ConnectedProvider.MY_ANIME_LIST)).activeProvider,
        )
        assertNull(mapOnboardingUiState(AccountSessionState.SignedOut).error)
    }

    @Test
    fun mapsFailureStageAndRetryabilityWithoutRuntimeText() {
        val retryable =
            mapOnboardingUiState(
                AccountSessionState.Failed(
                    provider = ConnectedProvider.ANILIST,
                    operation = AccountSessionOperation.INITIAL_IMPORT,
                    reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                    retryable = true,
                ),
            )
        val nonRetryable =
            mapOnboardingUiState(
                AccountSessionState.Failed(
                    provider = null,
                    operation = AccountSessionOperation.AUTHORIZATION,
                    reason = AccountSessionFailureReason.UNAVAILABLE,
                    retryable = false,
                ),
            )

        assertEquals(R.string.onboarding_retry, retryable.error?.retryLabelRes)
        assertNull(nonRetryable.error?.retryLabelRes)
        assertTrue(nonRetryable.error?.titleRes != 0)
        assertTrue(nonRetryable.error?.messageRes != 0)
    }
}
