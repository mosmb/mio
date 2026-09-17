package io.morgan.mio.data.sync

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderCredentialStorageException
import io.morgan.mio.data.provider.api.ProviderCredentialStore
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.sync.local.RoomSyncAccountLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.SyncInfrastructureException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class VerifiedProviderCredentials(
    val credentials: ProviderCredentials,
    val remoteAccountId: ProviderAccountId,
)

@Inject
internal class ProviderSyncCredentialReader(
    private val database: MioDatabase,
    private val accountLocalDataSource: RoomSyncAccountLocalDataSource,
    private val providerBinding: ProviderBinding,
    private val credentialStore: ProviderCredentialStore,
) {
    private val refreshMutex = Mutex()
    private var verifiedAccessToken: String? = null

    suspend fun <T> execute(
        accountId: AccountId,
        request: suspend (VerifiedProviderCredentials) -> ProviderResult<T>,
    ): ProviderResult<T> {
        val verified = verify(accountId)
        if (verified is ProviderResult.Failure) return verified

        val first = request((verified as ProviderResult.Success).value)
        if (
            first !is ProviderResult.Failure ||
            first.failure != ProviderFailure.AuthenticationRequired
        ) {
            return first
        }

        val credentials = verified.value.credentials
        if (credentials.refreshToken == null) return first
        val refreshed = refresh(credentials)
        if (refreshed is ProviderResult.Failure) return refreshed

        val replayCredentials = verify(accountId)
        if (replayCredentials is ProviderResult.Failure) return replayCredentials
        return request((replayCredentials as ProviderResult.Success).value)
    }

    private suspend fun verify(accountId: AccountId): ProviderResult<VerifiedProviderCredentials> {
        try {
            if (!accountLocalDataSource.isSelected(accountId, providerBinding.provider)) {
                return ProviderResult.Failure(ProviderFailure.AuthenticationRequired)
            }
            val storedProvider = providerBinding.provider.toStoredProvider()
            val accountMapping =
                database.syncDao().getAccountProviderId(accountId.value)
                    ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            if (accountMapping.provider != storedProvider || accountMapping.remoteAccountId.isBlank()) {
                return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            }
            val credentials =
                credentialStore.read(providerBinding.provider)
                    ?: return ProviderResult.Failure(ProviderFailure.AuthenticationRequired)
            if (credentials.provider != providerBinding.provider) {
                return ProviderResult.Failure(ProviderFailure.AuthenticationRequired)
            }

            if (verifiedAccessToken != credentials.accessToken) {
                when (val identity = providerBinding.identityDataSource.fetchIdentity(credentials)) {
                    is ProviderResult.Failure -> return identity
                    is ProviderResult.Success -> {
                        if (
                            identity.value.provider != providerBinding.provider ||
                            identity.value.accountId.value != accountMapping.remoteAccountId
                        ) {
                            return ProviderResult.Failure(ProviderFailure.AuthenticationRequired)
                        }
                    }
                }
                verifiedAccessToken = credentials.accessToken
            }
            if (!accountLocalDataSource.isSelected(accountId, providerBinding.provider)) {
                return ProviderResult.Failure(ProviderFailure.AuthenticationRequired)
            }
            return ProviderResult.Success(
                VerifiedProviderCredentials(
                    credentials = credentials,
                    remoteAccountId = ProviderAccountId(accountMapping.remoteAccountId),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: ProviderCredentialStorageException) {
            throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
        } catch (_: SQLiteException) {
            throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
        }
    }

    private suspend fun refresh(credentials: ProviderCredentials): ProviderResult<ProviderCredentials> =
        refreshMutex.withLock {
            val current = credentialStore.read(providerBinding.provider)
            if (current == null) {
                return@withLock ProviderResult.Failure(ProviderFailure.AuthenticationRequired)
            }
            if (current.accessToken != credentials.accessToken) {
                verifiedAccessToken = null
                return@withLock ProviderResult.Success(current)
            }
            when (val result = providerBinding.authenticator.refresh(current)) {
                is ProviderResult.Failure -> result
                is ProviderResult.Success -> {
                    val refreshed = result.value
                    if (refreshed.provider != providerBinding.provider) {
                        ProviderResult.Failure(ProviderFailure.AuthenticationRequired)
                    } else {
                        credentialStore.write(refreshed)
                        verifiedAccessToken = null
                        ProviderResult.Success(refreshed)
                    }
                }
            }
        }
}

private fun ProviderKey.toStoredProvider(): StoredConnectedProvider =
    when (this) {
        ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST
        ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
    }
