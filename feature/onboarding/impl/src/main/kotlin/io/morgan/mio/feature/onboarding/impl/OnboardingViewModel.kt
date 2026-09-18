package io.morgan.mio.feature.onboarding.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.domain.account.AccountSessionOperation
import io.morgan.mio.domain.account.AccountSessionRepository
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
internal class OnboardingViewModel(
    private val accountSessionRepository: AccountSessionRepository,
) : ViewModel() {
    private val accountSessionState = accountSessionRepository.state

    internal val uiState: StateFlow<OnboardingUiState> =
        accountSessionState
            .map(::mapOnboardingUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = mapOnboardingUiState(accountSessionState.value),
            )

    private val authorizationLaunchValue = MutableStateFlow<OnboardingAuthorizationLaunch?>(null)
    internal val authorizationLaunch: StateFlow<OnboardingAuthorizationLaunch?> =
        authorizationLaunchValue.asStateFlow()

    internal fun onAction(action: OnboardingAction) {
        if (uiState.value.phase != OnboardingPhase.WELCOME) {
            return
        }

        when (action) {
            OnboardingAction.ContinueWithAniList ->
                if (uiState.value.connectEnabled) connect(ConnectedProvider.ANILIST)
            OnboardingAction.ContinueWithMyAnimeList ->
                if (uiState.value.connectEnabled) connect(ConnectedProvider.MY_ANIME_LIST)
            OnboardingAction.Retry -> retry()
        }
    }

    internal fun onAuthorizationOpened(launch: OnboardingAuthorizationLaunch) {
        authorizationLaunchValue.compareAndSet(launch, null)
    }

    internal fun onAuthorizationLaunchFailed(launch: OnboardingAuthorizationLaunch) {
        if (!authorizationLaunchValue.compareAndSet(launch, null)) {
            return
        }
        viewModelScope.launch {
            accountSessionRepository.failAuthorizationLaunch()
        }
    }

    private fun connect(provider: ConnectedProvider) {
        viewModelScope.launch {
            val launch = accountSessionRepository.startConnection(provider)
            if (launch != null) {
                authorizationLaunchValue.value =
                    OnboardingAuthorizationLaunch(launch.authorizationUri)
            }
        }
    }

    private fun retry() {
        val current = accountSessionState.value as? AccountSessionState.Failed ?: return
        if (!current.retryable) {
            return
        }
        if (current.operation == AccountSessionOperation.RESTORATION) {
            viewModelScope.launch { accountSessionRepository.restore() }
        } else if (current.operation == AccountSessionOperation.DISCONNECTION) {
            viewModelScope.launch { accountSessionRepository.disconnect() }
        } else {
            current.provider?.let(::connect)
        }
    }
}
