package io.morgan.mio.data.account

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.domain.account.AccountAuthorizationLaunch
import io.morgan.mio.domain.account.AccountAuthorizationRedirect
import io.morgan.mio.domain.account.AccountSessionFailureReason
import io.morgan.mio.domain.account.AccountSessionOperation
import io.morgan.mio.domain.account.AccountSessionRepository
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val REDIRECT_UNAVAILABLE =
    "Account authorization redirect is unavailable before production provider integration"
private const val ACTIVE_ACCOUNT_REQUIRES_DISCONNECTION =
    "An account is already connected; disconnect before connecting another provider"

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
public class UnavailableAccountSessionRepository : AccountSessionRepository {
    private val transitionMutex = Mutex()
    private val stateValue = MutableStateFlow<AccountSessionState>(AccountSessionState.Restoring)

    override val state: StateFlow<AccountSessionState> = stateValue.asStateFlow()

    override suspend fun restore() {
        transitionMutex.withLock {
            val current = stateValue.value
            if (current is AccountSessionState.Restoring ||
                current is AccountSessionState.Failed &&
                current.operation == AccountSessionOperation.RESTORATION
            ) {
                stateValue.value = AccountSessionState.SignedOut
            }
        }
    }

    override suspend fun startConnection(provider: ConnectedProvider): AccountAuthorizationLaunch? =
        transitionMutex.withLock {
            when (stateValue.value) {
                AccountSessionState.Restoring,
                is AccountSessionState.Authorizing,
                is AccountSessionState.Importing,
                -> return@withLock null

                is AccountSessionState.Authenticated -> error(ACTIVE_ACCOUNT_REQUIRES_DISCONNECTION)
                AccountSessionState.SignedOut,
                is AccountSessionState.Failed,
                -> Unit
            }

            stateValue.value =
                AccountSessionState.Failed(
                    provider = provider,
                    operation = AccountSessionOperation.AUTHORIZATION,
                    reason = AccountSessionFailureReason.UNAVAILABLE,
                    retryable = false,
                )
            null
        }

    override suspend fun completeAuthorization(redirect: AccountAuthorizationRedirect): Nothing =
        error(REDIRECT_UNAVAILABLE)

    override suspend fun failAuthorizationLaunch() {
        transitionMutex.withLock {
            val authorizing = stateValue.value as? AccountSessionState.Authorizing ?: return@withLock
            stateValue.value =
                AccountSessionState.Failed(
                    provider = authorizing.provider,
                    operation = AccountSessionOperation.AUTHORIZATION,
                    reason = AccountSessionFailureReason.AUTHORIZATION_LAUNCH_FAILED,
                    retryable = true,
                )
        }
    }
}
