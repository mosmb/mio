package io.morgan.mio.data.account

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.morgan.mio.core.common.di.AppScope
import io.morgan.mio.core.database.MioDatabase
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
import io.morgan.mio.domain.account.AccountReconnectionRepository
import io.morgan.mio.domain.account.AccountReconnectionState
import io.morgan.mio.domain.account.AccountSessionFailureReason
import io.morgan.mio.domain.account.ConnectedProvider
import io.morgan.mio.domain.model.AccountId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
internal class ProductionAccountReconnectionRepository(
    private val bindingResolver: ProviderBindingResolver,
    private val credentialStore: ProviderCredentialStore,
    private val accountLocalDataSource: RoomAccountLocalDataSource,
    private val database: MioDatabase,
) : AccountReconnectionRepository {
    private val transitionMutex = Mutex()
    private val stateValue = MutableStateFlow<AccountReconnectionState>(AccountReconnectionState.Idle)
    private var pendingAccountId: AccountId? = null
    private var pendingProvider: ConnectedProvider? = null
    private var pendingBinding: ProviderBinding? = null
    private var pendingTransaction: ProviderAuthorizationTransaction? = null

    override val state: StateFlow<AccountReconnectionState> = stateValue.asStateFlow()

    override suspend fun start(): AccountAuthorizationLaunch? =
        transitionMutex.withLock {
            val account = accountLocalDataSource.observeSelectedAccount().first() ?: return@withLock null
            when (stateValue.value) {
                is AccountReconnectionState.Authorizing -> return@withLock null
                else -> Unit
            }
            val binding = bindingResolver.bindingFor(account.provider.toProviderKey())
            if (binding == null) {
                fail(account.id, AccountSessionFailureReason.UNAVAILABLE)
                return@withLock null
            }
            pendingAccountId = account.id
            pendingProvider = account.provider
            pendingBinding = binding
            stateValue.value = AccountReconnectionState.Authorizing(account.id)
            try {
                when (
                    val result =
                        binding.authenticator.beginAuthorization(
                            ProviderAuthorizationRequest(account.provider.redirectUri),
                        )
                ) {
                    is ProviderResult.Failure -> {
                        fail(
                            account.id,
                            result.failure.toFailureReason(),
                        )
                        null
                    }
                    is ProviderResult.Success -> {
                        pendingTransaction = result.value.transaction
                        AccountAuthorizationLaunch(result.value.authorizationUrl)
                    }
                }
            } catch (cancellation: CancellationException) {
                clearPending()
                stateValue.value = AccountReconnectionState.Idle
                throw cancellation
            }
        }

    override suspend fun complete(redirect: AccountAuthorizationRedirect) {
        transitionMutex.withLock {
            val authorizing = stateValue.value as? AccountReconnectionState.Authorizing ?: return@withLock
            val accountId = pendingAccountId
            val provider = pendingProvider
            val binding = pendingBinding
            val transaction = pendingTransaction
            if (accountId == null || provider == null || binding == null || transaction == null) {
                fail(authorizing.accountId, AccountSessionFailureReason.PROVIDER_FAILURE)
                return@withLock
            }
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
                            fail(accountId, result.failure.toFailureReason())
                            return@withLock
                        }
                        is ProviderResult.Success -> result.value
                    }
                if (credentials.provider != binding.provider) {
                    fail(accountId, AccountSessionFailureReason.PROVIDER_FAILURE)
                    return@withLock
                }
                val identity =
                    when (val result = binding.identityDataSource.fetchIdentity(credentials)) {
                        is ProviderResult.Failure -> {
                            fail(accountId, result.failure.toFailureReason())
                            return@withLock
                        }
                        is ProviderResult.Success -> result.value
                    }
                val selected = database.syncDao().getSelectedAccount()
                val mapping = database.syncDao().getAccountProviderId(accountId.value)
                if (
                    selected?.accountId != accountId.value ||
                    selected.connectedProvider != provider.toStoredProvider() ||
                    mapping?.accountId != accountId.value ||
                    mapping.provider != provider.toStoredProvider() ||
                    identity.provider != binding.provider ||
                    identity.accountId.value != mapping.remoteAccountId
                ) {
                    fail(accountId, AccountSessionFailureReason.PROVIDER_FAILURE)
                    return@withLock
                }

                // This is deliberately a single-provider overwrite. The existing account and its
                // cache/outbox remain intact if the secure write throws or the coroutine is cancelled.
                credentialStore.write(credentials)
                clearPending()
                stateValue.value = AccountReconnectionState.Succeeded(accountId)
            } catch (cancellation: CancellationException) {
                clearPending()
                stateValue.value = AccountReconnectionState.Idle
                throw cancellation
            } catch (_: ProviderCredentialStorageException) {
                fail(accountId, AccountSessionFailureReason.PROVIDER_FAILURE)
            } catch (_: SQLiteException) {
                fail(accountId, AccountSessionFailureReason.PROVIDER_FAILURE)
            } catch (_: IllegalArgumentException) {
                fail(accountId, AccountSessionFailureReason.PROVIDER_FAILURE)
            }
        }
    }

    override suspend fun cancel() {
        transitionMutex.withLock {
            val accountId = pendingAccountId
            clearPending()
            if (accountId != null) stateValue.value = AccountReconnectionState.Idle
        }
    }

    private fun fail(
        accountId: AccountId,
        reason: AccountSessionFailureReason,
    ) {
        clearPending()
        stateValue.value = AccountReconnectionState.Failed(accountId, reason)
    }

    private fun clearPending() {
        pendingAccountId = null
        pendingProvider = null
        pendingBinding = null
        pendingTransaction = null
    }
}

private fun ProviderKey.toStoredProvider(): io.morgan.mio.core.database.model.StoredConnectedProvider =
    when (this) {
        ProviderKey.ANILIST -> io.morgan.mio.core.database.model.StoredConnectedProvider.ANILIST
        ProviderKey.MY_ANIME_LIST -> io.morgan.mio.core.database.model.StoredConnectedProvider.MY_ANIME_LIST
    }

private fun ConnectedProvider.toProviderKey(): ProviderKey =
    when (this) {
        ConnectedProvider.ANILIST -> ProviderKey.ANILIST
        ConnectedProvider.MY_ANIME_LIST -> ProviderKey.MY_ANIME_LIST
    }

private fun ConnectedProvider.toStoredProvider(): io.morgan.mio.core.database.model.StoredConnectedProvider =
    toProviderKey().toStoredProvider()

private val ConnectedProvider.redirectUri: String
    get() =
        when (this) {
            ConnectedProvider.ANILIST -> "mio://oauth/anilist"
            ConnectedProvider.MY_ANIME_LIST -> "mio://oauth/myanimelist"
        }

private fun io.morgan.mio.data.provider.api.ProviderFailure.toFailureReason(): AccountSessionFailureReason =
    when (recovery) {
        ProviderRecovery.RETRY,
        ProviderRecovery.REAUTHENTICATE,
        -> AccountSessionFailureReason.PROVIDER_FAILURE
        ProviderRecovery.DO_NOT_RETRY -> AccountSessionFailureReason.PROVIDER_FAILURE
    }
