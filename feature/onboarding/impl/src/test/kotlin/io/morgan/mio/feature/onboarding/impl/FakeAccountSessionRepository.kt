package io.morgan.mio.feature.onboarding.impl

import io.morgan.mio.domain.account.AccountAuthorizationLaunch
import io.morgan.mio.domain.account.AccountAuthorizationRedirect
import io.morgan.mio.domain.account.AccountSessionRepository
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FakeAccountSessionRepository(
    initialState: AccountSessionState,
) : AccountSessionRepository {
    private val stateValue = MutableStateFlow(initialState)

    override val state: StateFlow<AccountSessionState> = stateValue.asStateFlow()
    val startedProviders = mutableListOf<ConnectedProvider>()
    var startResult: AccountAuthorizationLaunch? = null
    var restoreCalls: Int = 0
        private set
    var failLaunchCalls: Int = 0
        private set
    var completedRedirect: AccountAuthorizationRedirect? = null
        private set

    fun setState(state: AccountSessionState) {
        stateValue.value = state
    }

    override suspend fun restore() {
        restoreCalls += 1
    }

    override suspend fun startConnection(provider: ConnectedProvider): AccountAuthorizationLaunch? {
        startedProviders += provider
        return startResult
    }

    override suspend fun completeAuthorization(redirect: AccountAuthorizationRedirect) {
        completedRedirect = redirect
    }

    override suspend fun failAuthorizationLaunch() {
        failLaunchCalls += 1
    }
}
