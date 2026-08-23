package io.morgan.mio.data.account

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.data.account.local.RoomAccountLocalDataSource
import io.morgan.mio.data.provider.api.ProviderAuthorizationRedirect
import io.morgan.mio.data.provider.api.ProviderAuthorizationRequest
import io.morgan.mio.data.provider.api.ProviderAuthorizationTransaction
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderBindingResolver
import io.morgan.mio.data.provider.api.ProviderCredentialStorageException
import io.morgan.mio.data.provider.api.ProviderCredentialStore
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val REDIRECT_OUTSIDE_AUTHORIZATION =
    "Authorization redirect cannot be completed in the current session state"
private const val ACTIVE_ACCOUNT_REQUIRES_DISCONNECTION =
    "An account is already connected; disconnect before connecting another provider"

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
internal class ProductionAccountSessionRepository(
    private val bindingResolver: ProviderBindingResolver,
    private val credentialStore: ProviderCredentialStore,
    private val initialImportWriter: InitialImportWriter,
    private val accountLocalDataSource: RoomAccountLocalDataSource,
    private val accountSessionActivator: AuthenticatedAccountSessionActivator,
) : AccountSessionRepository {
    private val transitionMutex = Mutex()
    private val stateValue = MutableStateFlow<AccountSessionState>(AccountSessionState.Restoring)
    private var pendingProvider: ConnectedProvider? = null
    private var pendingBinding: ProviderBinding? = null
    private var pendingTransaction: ProviderAuthorizationTransaction? = null

    override val state: StateFlow<AccountSessionState> = stateValue.asStateFlow()

    override suspend fun restore() {
        transitionMutex.withLock {
            val current = stateValue.value
            val canRestore =
                current is AccountSessionState.Restoring ||
                    current is AccountSessionState.Failed &&
                    current.operation == AccountSessionOperation.RESTORATION
            if (!canRestore) return@withLock

            try {
                val account = accountLocalDataSource.observeSelectedAccount().first()
                if (account == null) {
                    accountSessionActivator.destroy()
                    stateValue.value = AccountSessionState.SignedOut
                    return@withLock
                }
                val providerKey = account.provider.toProviderKey()
                if (bindingResolver.bindingFor(providerKey) == null) {
                    fail(
                        provider = account.provider,
                        operation = AccountSessionOperation.RESTORATION,
                        reason = AccountSessionFailureReason.UNAVAILABLE,
                        retryable = false,
                    )
                    return@withLock
                }
                val credentials = credentialStore.read(providerKey)
                if (credentials == null) {
                    accountSessionActivator.destroy()
                    stateValue.value = AccountSessionState.SignedOut
                    return@withLock
                }
                check(credentials.provider == providerKey)
                accountSessionActivator.activate(account.id, account.provider)
                stateValue.value =
                    AccountSessionState.Authenticated(
                        accountId = account.id,
                        provider = account.provider,
                    )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: ProviderCredentialStorageException) {
                fail(
                    provider = null,
                    operation = AccountSessionOperation.RESTORATION,
                    reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                    retryable = true,
                )
            } catch (_: SQLiteException) {
                fail(
                    provider = null,
                    operation = AccountSessionOperation.RESTORATION,
                    reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                    retryable = true,
                )
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

            val providerKey = provider.toProviderKey()
            val binding = bindingResolver.bindingFor(providerKey)
            if (binding == null) {
                fail(
                    provider = provider,
                    operation = AccountSessionOperation.AUTHORIZATION,
                    reason = AccountSessionFailureReason.UNAVAILABLE,
                    retryable = false,
                )
                return@withLock null
            }
            check(binding.provider == providerKey)

            stateValue.value = AccountSessionState.Authorizing(provider)
            try {
                val request = ProviderAuthorizationRequest(provider.redirectUri)
                when (val result = binding.authenticator.beginAuthorization(request)) {
                    is ProviderResult.Failure -> {
                        failProvider(provider, AccountSessionOperation.AUTHORIZATION, result)
                        null
                    }
                    is ProviderResult.Success -> {
                        pendingProvider = provider
                        pendingBinding = binding
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
            val binding = pendingBinding
            val transaction = pendingTransaction
            check(pendingProvider == authorizing.provider && binding != null && transaction != null)

            var storedCredentialProvider: ProviderKey? = null
            try {
                val credentials =
                    when (
                        val result =
                            binding.authenticator.completeAuthorization(
                                transaction = transaction,
                                redirect = ProviderAuthorizationRedirect(redirect.redirectUri),
                            )
                    ) {
                        is ProviderResult.Failure -> {
                            failProvider(authorizing.provider, AccountSessionOperation.AUTHENTICATION, result)
                            return@withLock
                        }
                        is ProviderResult.Success -> result.value
                    }
                check(credentials.provider == binding.provider)

                val identity =
                    when (val result = binding.identityDataSource.fetchIdentity(credentials)) {
                        is ProviderResult.Failure -> {
                            failProvider(authorizing.provider, AccountSessionOperation.AUTHENTICATION, result)
                            return@withLock
                        }
                        is ProviderResult.Success -> result.value
                    }
                check(identity.provider == binding.provider)

                stateValue.value = AccountSessionState.Importing(authorizing.provider)
                val completeImport =
                    when (
                        val result =
                            binding.initialImportDataSource.fetchCompleteImport(
                                credentials = credentials,
                                accountId = identity.accountId,
                            )
                    ) {
                        is ProviderResult.Failure -> {
                            failProvider(authorizing.provider, AccountSessionOperation.INITIAL_IMPORT, result)
                            return@withLock
                        }
                        is ProviderResult.Success -> result.value
                    }
                check(completeImport.provider == binding.provider)
                check(completeImport.accountId == identity.accountId)

                credentialStore.deleteAll()
                credentialStore.write(credentials)
                storedCredentialProvider = credentials.provider
                val accountId = initialImportWriter.commit(identity, completeImport)
                accountSessionActivator.activate(accountId, authorizing.provider)
                clearPendingAuthorization()
                stateValue.value =
                    AccountSessionState.Authenticated(
                        accountId = accountId,
                        provider = authorizing.provider,
                    )
            } catch (cancellation: CancellationException) {
                storedCredentialProvider?.let { provider -> deleteCredentialBestEffort(provider) }
                accountSessionActivator.destroy()
                clearPendingAuthorization()
                stateValue.value = AccountSessionState.SignedOut
                throw cancellation
            } catch (_: ProviderCredentialStorageException) {
                storedCredentialProvider?.let { provider -> deleteCredentialBestEffort(provider) }
                fail(
                    provider = authorizing.provider,
                    operation = AccountSessionOperation.AUTHENTICATION,
                    reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                    retryable = true,
                )
            } catch (_: SQLiteException) {
                storedCredentialProvider?.let { provider -> deleteCredentialBestEffort(provider) }
                fail(
                    provider = authorizing.provider,
                    operation = AccountSessionOperation.INITIAL_IMPORT,
                    reason = AccountSessionFailureReason.PROVIDER_FAILURE,
                    retryable = true,
                )
            }
        }
    }

    override suspend fun failAuthorizationLaunch() {
        transitionMutex.withLock {
            val authorizing = stateValue.value as? AccountSessionState.Authorizing ?: return@withLock
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

    private suspend fun deleteCredentialBestEffort(provider: ProviderKey) {
        withContext(NonCancellable) {
            try {
                credentialStore.delete(provider)
            } catch (_: ProviderCredentialStorageException) {
                Unit
            }
        }
    }

    private fun failProvider(
        provider: ConnectedProvider,
        operation: AccountSessionOperation,
        result: ProviderResult.Failure,
    ) {
        fail(
            provider = provider,
            operation = operation,
            reason = AccountSessionFailureReason.PROVIDER_FAILURE,
            retryable = result.failure.recovery != ProviderRecovery.DO_NOT_RETRY,
        )
    }

    private fun fail(
        provider: ConnectedProvider?,
        operation: AccountSessionOperation,
        reason: AccountSessionFailureReason,
        retryable: Boolean,
    ) {
        clearPendingAuthorization()
        stateValue.value =
            AccountSessionState.Failed(
                provider = provider,
                operation = operation,
                reason = reason,
                retryable = retryable,
            )
    }

    private fun clearPendingAuthorization() {
        pendingProvider = null
        pendingBinding = null
        pendingTransaction = null
    }
}

private val ConnectedProvider.redirectUri: String
    get() =
        when (this) {
            ConnectedProvider.ANILIST -> "mio://oauth/anilist"
            ConnectedProvider.MY_ANIME_LIST -> "mio://oauth/myanimelist"
        }

private fun ConnectedProvider.toProviderKey(): ProviderKey =
    when (this) {
        ConnectedProvider.ANILIST -> ProviderKey.ANILIST
        ConnectedProvider.MY_ANIME_LIST -> ProviderKey.MY_ANIME_LIST
    }
