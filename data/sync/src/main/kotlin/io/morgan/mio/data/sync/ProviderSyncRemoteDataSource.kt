package io.morgan.mio.data.sync

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderCredentialStorageException
import io.morgan.mio.data.provider.api.ProviderCredentialStore
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.sync.local.RoomSyncAccountLocalDataSource
import io.morgan.mio.data.sync.mapper.toSyncEntryState
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.SyncCandidate
import io.morgan.mio.domain.sync.SyncInfrastructureException
import io.morgan.mio.domain.sync.SyncRemoteResult
import kotlinx.coroutines.CancellationException

@Inject
public class ProviderSyncRemoteDataSource(
    private val database: MioDatabase,
    private val accountLocalDataSource: RoomSyncAccountLocalDataSource,
    private val providerBinding: ProviderBinding,
    private val credentialStore: ProviderCredentialStore,
) {
    private var verifiedCredentials: io.morgan.mio.data.provider.api.ProviderCredentials? = null

    public suspend fun read(candidate: SyncCandidate): SyncRemoteResult =
        call(candidate) { credentials, accountId, entryId, mediaId, unit ->
            providerBinding.syncDataSource.readEntry(credentials, accountId, entryId, mediaId, unit)
        }

    public suspend fun write(
        candidate: SyncCandidate,
        value: OperationValue,
    ): SyncRemoteResult =
        call(candidate) { credentials, accountId, entryId, mediaId, unit ->
            when (value) {
                is OperationValue.Progress ->
                    providerBinding.syncDataSource.setProgress(
                        credentials = credentials,
                        accountId = accountId,
                        entryId = entryId,
                        mediaId = mediaId,
                        unit = unit,
                        target = value.value.value,
                    )
                is OperationValue.Status ->
                    providerBinding.syncDataSource.setStatus(
                        credentials = credentials,
                        accountId = accountId,
                        entryId = entryId,
                        mediaId = mediaId,
                        unit = unit,
                        target = value.value.toProviderStatus(),
                    )
            }
        }

    private suspend fun call(
        candidate: SyncCandidate,
        request: suspend (
            io.morgan.mio.data.provider.api.ProviderCredentials,
            io.morgan.mio.data.provider.api.ProviderAccountId,
            io.morgan.mio.data.provider.api.ProviderCollectionEntryId,
            io.morgan.mio.data.provider.api.ProviderMediaId,
            ProviderProgressUnit,
        ) -> ProviderResult<io.morgan.mio.data.provider.api.ProviderCollectionEntry>,
    ): SyncRemoteResult {
        val accountId = candidate.current.accountId
        val provider = providerBinding.provider
        return try {
            if (!accountLocalDataSource.isSelected(accountId, provider)) return SyncRemoteResult.Authentication
            val storedProvider = provider.toStoredProvider()
            val accountMapping =
                database.syncDao().getAccountProviderId(accountId.value)
                    ?: return SyncRemoteResult.Rejected()
            if (accountMapping.provider != storedProvider) return SyncRemoteResult.Rejected()
            val entryMapping =
                database.syncDao().getEntryProviderId(candidate.current.entryId.value, storedProvider)
                    ?: return SyncRemoteResult.Rejected()
            val localEntry =
                database.syncDao().getEntry(candidate.operation.collectionEntryId.value)
                    ?: return SyncRemoteResult.Rejected()
            val mediaMapping =
                database.syncDao().getMediaProviderId(localEntry.mediaId, storedProvider)
                    ?: return SyncRemoteResult.Rejected()
            val credentials = credentialStore.read(provider) ?: return SyncRemoteResult.Authentication
            val first =
                requestWithIds(
                    request,
                    credentials,
                    accountMapping.remoteAccountId,
                    entryMapping.remoteCollectionEntryId,
                    mediaMapping.remoteMediaId,
                    candidate,
                )
            if (first !is ProviderResult.Failure ||
                first.failure != ProviderFailure.AuthenticationRequired ||
                credentials.refreshToken == null
            ) {
                return first.toDomainResult(
                    candidate,
                    accountMapping.remoteAccountId,
                    entryMapping.remoteCollectionEntryId,
                    mediaMapping.remoteMediaId,
                )
            }

            val refreshed = providerBinding.authenticator.refresh(credentials)
            val refreshedCredentials =
                when (refreshed) {
                    is ProviderResult.Success -> refreshed.value
                    is ProviderResult.Failure -> return refreshed.failure.toDomainResult()
                }
            check(refreshedCredentials.provider == provider)
            if (!accountLocalDataSource.isSelected(accountId, provider)) return SyncRemoteResult.Authentication
            credentialStore.write(refreshedCredentials)
            if (!accountLocalDataSource.isSelected(accountId, provider)) return SyncRemoteResult.Authentication
            requestWithIds(
                request,
                refreshedCredentials,
                accountMapping.remoteAccountId,
                entryMapping.remoteCollectionEntryId,
                mediaMapping.remoteMediaId,
                candidate,
            ).toDomainResult(
                candidate,
                accountMapping.remoteAccountId,
                entryMapping.remoteCollectionEntryId,
                mediaMapping.remoteMediaId,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: ProviderCredentialStorageException) {
            throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
        } catch (_: SQLiteException) {
            throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
        }
    }

    private suspend fun requestWithIds(
        request: suspend (
            io.morgan.mio.data.provider.api.ProviderCredentials,
            io.morgan.mio.data.provider.api.ProviderAccountId,
            io.morgan.mio.data.provider.api.ProviderCollectionEntryId,
            io.morgan.mio.data.provider.api.ProviderMediaId,
            ProviderProgressUnit,
        ) -> ProviderResult<io.morgan.mio.data.provider.api.ProviderCollectionEntry>,
        credentials: io.morgan.mio.data.provider.api.ProviderCredentials,
        accountId: String,
        entryId: String,
        mediaId: String,
        candidate: SyncCandidate,
    ): ProviderResult<io.morgan.mio.data.provider.api.ProviderCollectionEntry> {
        if (verifiedCredentials?.accessToken != credentials.accessToken) {
            when (val identity = providerBinding.identityDataSource.fetchIdentity(credentials)) {
                is ProviderResult.Success -> {
                    if (identity.value.provider != providerBinding.provider ||
                        identity.value.accountId.value != accountId
                    ) {
                        return ProviderResult.Failure(ProviderFailure.AuthenticationRequired)
                    }
                }
                is ProviderResult.Failure -> return identity
            }
            verifiedCredentials = credentials
        }
        if (!accountLocalDataSource.isSelected(candidate.current.accountId, providerBinding.provider)) {
            return ProviderResult.Failure(ProviderFailure.AuthenticationRequired)
        }
        return request(
            credentials,
            io.morgan.mio.data.provider.api
                .ProviderAccountId(accountId),
            io.morgan.mio.data.provider.api
                .ProviderCollectionEntryId(entryId),
            io.morgan.mio.data.provider.api
                .ProviderMediaId(mediaId),
            candidate.current.unit.toProviderUnit(),
        )
    }

    private fun ProviderResult<io.morgan.mio.data.provider.api.ProviderCollectionEntry>.toDomainResult(
        candidate: SyncCandidate,
        remoteAccountId: String,
        remoteEntryId: String,
        remoteMediaId: String,
    ): SyncRemoteResult =
        when (this) {
            is ProviderResult.Success -> {
                val value = value
                if (
                    value.accountId.value != remoteAccountId ||
                    value.id.value != remoteEntryId ||
                    value.mediaId.value != remoteMediaId ||
                    value.progressUnit != candidate.current.unit.toProviderUnit()
                ) {
                    SyncRemoteResult.InvalidData
                } else {
                    try {
                        SyncRemoteResult.Success(value.toSyncEntryState(candidate.current))
                    } catch (_: IllegalArgumentException) {
                        SyncRemoteResult.InvalidData
                    }
                }
            }
            is ProviderResult.Failure -> failure.toDomainResult()
        }

    private fun ProviderFailure.toDomainResult(): SyncRemoteResult =
        when (this) {
            ProviderFailure.NetworkUnavailable,
            ProviderFailure.Timeout,
            ProviderFailure.ServiceUnavailable,
            -> SyncRemoteResult.Transient()
            is ProviderFailure.RateLimited -> SyncRemoteResult.Transient(retryAfter)
            ProviderFailure.AuthenticationRequired -> SyncRemoteResult.Authentication
            ProviderFailure.MutationRejected,
            ProviderFailure.AuthorizationDenied,
            ProviderFailure.InvalidAuthorizationCallback,
            ProviderFailure.UnsupportedMandatoryData,
            -> SyncRemoteResult.Rejected()
            ProviderFailure.MutationConflict -> SyncRemoteResult.Conflict()
            ProviderFailure.MalformedResponse,
            ProviderFailure.Unexpected,
            -> SyncRemoteResult.InvalidData
        }

    private fun ProviderKey.toStoredProvider(): StoredConnectedProvider =
        when (this) {
            ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST
            ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
        }

    private fun io.morgan.mio.domain.model.ProgressUnit.toProviderUnit(): ProviderProgressUnit =
        when (this) {
            io.morgan.mio.domain.model.ProgressUnit.EPISODE -> ProviderProgressUnit.EPISODE
            io.morgan.mio.domain.model.ProgressUnit.CHAPTER -> ProviderProgressUnit.CHAPTER
            io.morgan.mio.domain.model.ProgressUnit.VOLUME -> ProviderProgressUnit.VOLUME
        }

    private fun io.morgan.mio.domain.collection.CollectionStatus.toProviderStatus(): ProviderCollectionStatus =
        when (this) {
            io.morgan.mio.domain.collection.CollectionStatus.CURRENT -> ProviderCollectionStatus.CURRENT
            io.morgan.mio.domain.collection.CollectionStatus.PLANNED -> ProviderCollectionStatus.PLANNED
            io.morgan.mio.domain.collection.CollectionStatus.PAUSED -> ProviderCollectionStatus.PAUSED
            io.morgan.mio.domain.collection.CollectionStatus.COMPLETED -> ProviderCollectionStatus.COMPLETED
            io.morgan.mio.domain.collection.CollectionStatus.DROPPED -> ProviderCollectionStatus.DROPPED
            io.morgan.mio.domain.collection.CollectionStatus.REPEATING -> ProviderCollectionStatus.REPEATING
        }
}
