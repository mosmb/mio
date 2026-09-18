package io.morgan.mio.session

import io.morgan.mio.core.testing.provider.FakeProviderCredentialStore
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.data.provider.api.ProviderAuthorizationRedirect
import io.morgan.mio.data.provider.api.ProviderAuthorizationRequest
import io.morgan.mio.data.provider.api.ProviderAuthorizationTransaction
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderCredentialStorageException
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderRecovery
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.domain.account.AccountAuthorizationLaunch
import io.morgan.mio.domain.account.AccountAuthorizationRedirect
import io.morgan.mio.domain.account.AccountSessionFailureReason
import io.morgan.mio.domain.account.AccountSessionOperation
import io.morgan.mio.domain.account.AccountSessionRepository
import io.morgan.mio.domain.account.AccountSessionState
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val REDIRECT_OUTSIDE_AUTHORIZATION =
    "Authorization redirect cannot be completed in the current session state"
private const val ACTIVE_ACCOUNT_REQUIRES_DISCONNECTION =
    "An account is already connected; disconnect before connecting another provider"

internal class FakeProviderAccountSessionRepository(
    val providerBinding: ProviderBinding = providerBindingFixture(),
    val providerCredentialStore: FakeProviderCredentialStore = FakeProviderCredentialStore(),
    val sessionStore: FakeAccountSessionStore = FakeAccountSessionStore(),
    private val redirectUri: String = "mio://task12/callback",
) : AccountSessionRepository {
    internal sealed interface Operation {
        data object Restore : Operation

        data class Start(
            val provider: ConnectedProvider,
        ) : Operation

        data object Complete : Operation

        data object Import : Operation

        data object FailLaunch : Operation

        data object Disconnect : Operation
    }

    private val transitionMutex = Mutex()
    private val stateValue = MutableStateFlow<AccountSessionState>(AccountSessionState.Restoring)
    private val exposedState = stateValue.asStateFlow()
    var stateReads: Int = 0
        private set
    override val state: StateFlow<AccountSessionState>
        get() {
            stateReads += 1
            return exposedState
        }

    val operations = mutableListOf<Operation>()
    var lastAuthorizationRequest: ProviderAuthorizationRequest? = null
        private set
    var lastRedirect: AccountAuthorizationRedirect? = null
        private set
    var restoreOverride: (suspend () -> AccountSessionState?)? = null

    private var pendingProvider: ProviderKey? = null
    private var pendingTransaction: ProviderAuthorizationTransaction? = null

    fun setStateForTest(state: AccountSessionState) {
        stateValue.value = state
    }

    override suspend fun restore() {
        transitionMutex.withLock {
            val current = stateValue.value
            val canRestore =
                current is AccountSessionState.Restoring ||
                    current is AccountSessionState.Failed &&
                    current.operation == AccountSessionOperation.RESTORATION
            if (!canRestore) return@withLock

            operations += Operation.Restore
            stateValue.value =
                restoreOverride?.invoke() ?: sessionStore.authenticatedSession ?: AccountSessionState.SignedOut
        }
    }

    override suspend fun startConnection(provider: ConnectedProvider): AccountAuthorizationLaunch? =
        transitionMutex.withLock {
            when (stateValue.value) {
                AccountSessionState.Restoring,
                is AccountSessionState.Authorizing,
                is AccountSessionState.Importing,
                is AccountSessionState.Disconnecting,
                -> return@withLock null

                is AccountSessionState.Authenticated -> error(ACTIVE_ACCOUNT_REQUIRES_DISCONNECTION)
                AccountSessionState.SignedOut,
                is AccountSessionState.Failed,
                -> Unit
            }

            operations += Operation.Start(provider)
            stateValue.value = AccountSessionState.Authorizing(provider)
            try {
                val providerKey = provider.toProviderKey()
                if (providerKey != providerBinding.provider) {
                    fail(
                        provider = provider,
                        operation = AccountSessionOperation.AUTHORIZATION,
                        retryable = false,
                    )
                    return@withLock null
                }

                val request = ProviderAuthorizationRequest(redirectUri)
                when (val result = providerBinding.authenticator.beginAuthorization(request)) {
                    is ProviderResult.Failure -> {
                        fail(provider, AccountSessionOperation.AUTHORIZATION, result.isRetryable)
                        null
                    }
                    is ProviderResult.Success -> {
                        lastAuthorizationRequest = request
                        pendingProvider = providerKey
                        pendingTransaction = result.value.transaction
                        AccountAuthorizationLaunch(result.value.authorizationUrl)
                    }
                }
            } catch (cancellation: CancellationException) {
                clearPendingAuthorization()
                stateValue.value = AccountSessionState.SignedOut
                throw cancellation
            }
        }

    override suspend fun completeAuthorization(redirect: AccountAuthorizationRedirect) {
        transitionMutex.withLock {
            val authorizing =
                stateValue.value as? AccountSessionState.Authorizing
                    ?: error(REDIRECT_OUTSIDE_AUTHORIZATION)
            operations += Operation.Complete
            lastRedirect = redirect
            try {
                val providerKey = pendingProvider
                val transaction = pendingTransaction
                if (providerKey == null || transaction == null || providerKey != providerBinding.provider) {
                    fail(authorizing.provider, AccountSessionOperation.AUTHENTICATION, retryable = false)
                    return@withLock
                }

                val credentials =
                    completeProviderAuthorization(transaction, redirect, authorizing.provider)
                        ?: return@withLock
                val providerAccountId = fetchProviderIdentity(credentials, authorizing.provider) ?: return@withLock

                stateValue.value = AccountSessionState.Importing(authorizing.provider)
                operations += Operation.Import
                when (
                    val result =
                        providerBinding.initialImportDataSource.fetchCompleteImport(
                            credentials,
                            providerAccountId,
                        )
                ) {
                    is ProviderResult.Failure ->
                        fail(authorizing.provider, AccountSessionOperation.INITIAL_IMPORT, result.isRetryable)
                    is ProviderResult.Success -> {
                        check(result.value.provider == providerBinding.provider)
                        check(result.value.accountId == providerAccountId)
                        try {
                            providerCredentialStore.write(credentials)
                        } catch (_: ProviderCredentialStorageException) {
                            fail(authorizing.provider, AccountSessionOperation.AUTHENTICATION, retryable = true)
                            return@withLock
                        }
                        val authenticated =
                            AccountSessionState.Authenticated(
                                accountId = AccountId("fixture-${providerKey.name.lowercase()}"),
                                provider = authorizing.provider,
                            )
                        sessionStore.authenticatedSession = authenticated
                        clearPendingAuthorization()
                        stateValue.value = authenticated
                    }
                }
            } catch (cancellation: CancellationException) {
                clearPendingAuthorization()
                stateValue.value = AccountSessionState.SignedOut
                throw cancellation
            }
        }
    }

    override suspend fun failAuthorizationLaunch() {
        transitionMutex.withLock {
            val authorizing = stateValue.value as? AccountSessionState.Authorizing ?: return@withLock
            operations += Operation.FailLaunch
            clearPendingAuthorization()
            stateValue.value =
                AccountSessionState.Failed(
                    provider = authorizing.provider,
                    operation = AccountSessionOperation.AUTHORIZATION,
                    reason = AccountSessionFailureReason.AUTHORIZATION_LAUNCH_FAILED,
                    retryable = true,
                )
        }
    }

    override suspend fun disconnect() {
        transitionMutex.withLock {
            operations += Operation.Disconnect
            stateValue.value = AccountSessionState.SignedOut
        }
    }

    private suspend fun completeProviderAuthorization(
        transaction: ProviderAuthorizationTransaction,
        redirect: AccountAuthorizationRedirect,
        provider: ConnectedProvider,
    ): ProviderCredentials? {
        val credentials =
            when (
                val result =
                    providerBinding.authenticator.completeAuthorization(
                        transaction = transaction,
                        redirect = ProviderAuthorizationRedirect(redirect.redirectUri),
                    )
            ) {
                is ProviderResult.Failure -> {
                    fail(provider, AccountSessionOperation.AUTHENTICATION, result.isRetryable)
                    return null
                }
                is ProviderResult.Success -> result.value
            }

        return credentials
    }

    private suspend fun fetchProviderIdentity(
        credentials: ProviderCredentials,
        provider: ConnectedProvider,
    ): io.morgan.mio.data.provider.api.ProviderAccountId? =
        when (val result = providerBinding.identityDataSource.fetchIdentity(credentials)) {
            is ProviderResult.Failure -> {
                fail(provider, AccountSessionOperation.AUTHENTICATION, result.isRetryable)
                null
            }
            is ProviderResult.Success -> {
                if (result.value.provider != providerBinding.provider) {
                    fail(provider, AccountSessionOperation.AUTHENTICATION, retryable = false)
                    null
                } else {
                    result.value.accountId
                }
            }
        }

    private fun fail(
        provider: ConnectedProvider,
        operation: AccountSessionOperation,
        retryable: Boolean,
    ) {
        clearPendingAuthorization()
        stateValue.value =
            AccountSessionState.Failed(
                provider = provider,
                operation = operation,
                reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                retryable = retryable,
            )
    }

    private fun clearPendingAuthorization() {
        pendingProvider = null
        pendingTransaction = null
    }
}

internal class FakeAccountSessionStore {
    var authenticatedSession: AccountSessionState.Authenticated? = null
}

private val ProviderResult.Failure.isRetryable: Boolean
    get() = failure.recovery != ProviderRecovery.DO_NOT_RETRY

private fun ConnectedProvider.toProviderKey(): ProviderKey =
    when (this) {
        ConnectedProvider.ANILIST -> ProviderKey.ANILIST
        ConnectedProvider.MY_ANIME_LIST -> ProviderKey.MY_ANIME_LIST
    }
