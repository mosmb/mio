package io.morgan.mio.data.sync

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderBinding
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.sync.mapper.toSyncEntryState
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.SyncCandidate
import io.morgan.mio.domain.sync.SyncInfrastructureException
import io.morgan.mio.domain.sync.SyncRemoteResult
import kotlinx.coroutines.CancellationException

@Inject
public class ProviderSyncRemoteDataSource internal constructor(
    private val database: MioDatabase,
    private val providerBinding: ProviderBinding,
    private val credentialReader: ProviderSyncCredentialReader,
) {
    internal constructor(
        database: MioDatabase,
        accountLocalDataSource: io.morgan.mio.data.sync.local.RoomSyncAccountLocalDataSource,
        providerBinding: ProviderBinding,
        credentialStore: io.morgan.mio.data.provider.api.ProviderCredentialStore,
    ) : this(
        database = database,
        providerBinding = providerBinding,
        credentialReader =
            ProviderSyncCredentialReader(
                database = database,
                accountLocalDataSource = accountLocalDataSource,
                providerBinding = providerBinding,
                credentialStore = credentialStore,
            ),
    )

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
            ProviderAccountId,
            ProviderCollectionEntryId,
            ProviderMediaId,
            ProviderProgressUnit,
        ) -> ProviderResult<ProviderCollectionEntry>,
    ): SyncRemoteResult {
        val accountId = candidate.current.accountId
        return try {
            val storedProvider = providerBinding.provider.toStoredProvider()
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
            if (localEntry.accountId != accountId.value) return SyncRemoteResult.Rejected()
            val mediaMapping =
                database.syncDao().getMediaProviderId(localEntry.mediaId, storedProvider)
                    ?: return SyncRemoteResult.Rejected()
            val result =
                credentialReader.execute(accountId) { verified ->
                    request(
                        verified.credentials,
                        verified.remoteAccountId,
                        ProviderCollectionEntryId(entryMapping.remoteCollectionEntryId),
                        ProviderMediaId(mediaMapping.remoteMediaId),
                        candidate.current.unit.toProviderUnit(),
                    )
                }
            result.toDomainResult(
                candidate = candidate,
                remoteAccountId = accountMapping.remoteAccountId,
                remoteEntryId = entryMapping.remoteCollectionEntryId,
                remoteMediaId = mediaMapping.remoteMediaId,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SQLiteException) {
            throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
        }
    }

    private fun ProviderResult<ProviderCollectionEntry>.toDomainResult(
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

    private fun io.morgan.mio.data.provider.api.ProviderKey.toStoredProvider(): StoredConnectedProvider =
        when (this) {
            io.morgan.mio.data.provider.api.ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST
            io.morgan.mio.data.provider.api.ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
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
