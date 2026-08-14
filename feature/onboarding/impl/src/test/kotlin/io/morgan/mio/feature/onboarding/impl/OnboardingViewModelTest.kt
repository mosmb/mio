package io.morgan.mio.feature.onboarding.impl

import io.morgan.mio.domain.account.AccountAuthorizationLaunch
import io.morgan.mio.domain.account.AccountSessionFailureReason
import io.morgan.mio.domain.account.AccountSessionOperation
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun providerActionInvokesOnlyTheMatchingUseCaseAndRetainsRedactedLaunch() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeAccountSessionRepository(AccountSessionState.SignedOut)
            repository.startResult = AccountAuthorizationLaunch("https://provider.example/secret-token")
            val viewModel = viewModel(repository)

            viewModel.onAction(OnboardingAction.ContinueWithAniList)
            advanceUntilIdle()

            assertEquals(listOf(ConnectedProvider.ANILIST), repository.startedProviders)
            val pending = viewModel.authorizationLaunch.value
            assertEquals("https://provider.example/secret-token", pending?.authorizationUri)
            assertFalse(pending.toString().contains("secret-token"))
        }

    @Test
    fun launchRemainsPendingUntilTheMatchingInstanceIsAcknowledged() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeAccountSessionRepository(AccountSessionState.SignedOut)
            repository.startResult = AccountAuthorizationLaunch("https://provider.example/authorize")
            val viewModel = viewModel(repository)
            viewModel.onAction(OnboardingAction.ContinueWithAniList)
            advanceUntilIdle()
            val pending = viewModel.authorizationLaunch.value!!

            viewModel.onAuthorizationOpened(OnboardingAuthorizationLaunch("https://stale.example"))
            assertSame(pending, viewModel.authorizationLaunch.value)

            viewModel.onAuthorizationOpened(pending)
            assertNull(viewModel.authorizationLaunch.value)
        }

    @Test
    fun launchFailureClearsPendingStateAndInvokesFailureUseCaseOnce() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository = FakeAccountSessionRepository(AccountSessionState.SignedOut)
            repository.startResult = AccountAuthorizationLaunch("https://provider.example/authorize")
            val viewModel = viewModel(repository)
            viewModel.onAction(OnboardingAction.ContinueWithAniList)
            advanceUntilIdle()
            val pending = viewModel.authorizationLaunch.value!!

            viewModel.onAuthorizationLaunchFailed(pending)
            viewModel.onAuthorizationLaunchFailed(pending)
            advanceUntilIdle()

            assertNull(viewModel.authorizationLaunch.value)
            assertEquals(1, repository.failLaunchCalls)
        }

    @Test
    fun actionsAreIgnoredOutsideWelcomeAndNonRetryableFailuresDoNotRetry() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeAccountSessionRepository(
                    AccountSessionState.Authorizing(ConnectedProvider.ANILIST),
                )
            repository.startResult = AccountAuthorizationLaunch("https://provider.example/authorize")
            val viewModel = viewModel(repository)

            viewModel.onAction(OnboardingAction.ContinueWithMyAnimeList)
            advanceUntilIdle()
            assertTrue(repository.startedProviders.isEmpty())

            repository.setState(
                AccountSessionState.Failed(
                    provider = ConnectedProvider.ANILIST,
                    operation = AccountSessionOperation.AUTHORIZATION,
                    reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                    retryable = false,
                ),
            )
            advanceUntilIdle()
            viewModel.onAction(OnboardingAction.Retry)
            advanceUntilIdle()
            assertTrue(repository.startedProviders.isEmpty())
        }

    @Test
    fun retryUsesRestoreForRestorationAndStartForProviderFailure() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val repository =
                FakeAccountSessionRepository(
                    AccountSessionState.Failed(
                        provider = null,
                        operation = AccountSessionOperation.RESTORATION,
                        reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                        retryable = true,
                    ),
                )
            val viewModel = viewModel(repository)
            viewModel.onAction(OnboardingAction.Retry)
            advanceUntilIdle()
            assertEquals(1, repository.restoreCalls)

            repository.setState(
                AccountSessionState.Failed(
                    provider = ConnectedProvider.MY_ANIME_LIST,
                    operation = AccountSessionOperation.AUTHENTICATION,
                    reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                    retryable = true,
                ),
            )
            advanceUntilIdle()
            viewModel.onAction(OnboardingAction.Retry)
            advanceUntilIdle()
            assertEquals(listOf(ConnectedProvider.MY_ANIME_LIST), repository.startedProviders)
        }

    private fun viewModel(repository: FakeAccountSessionRepository): OnboardingViewModel =
        OnboardingViewModel(accountSessionRepository = repository)
}
