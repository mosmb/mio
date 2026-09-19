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
        if (candidate.operation.kind == io.morgan.mio.domain.sync.PendingOperationKind.CREATE_ENTRY) {
            create(candidate)
        } else if (candidate.operation.kind == io.morgan.mio.domain.sync.PendingOperationKind.REMOVE_ENTRY) {
            remove(candidate)
        } else {
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
                    is OperationValue.Membership ->
                        error("Membership operations are handled by their dedicated provider path")
                }
            }
        }

    private suspend fun create(candidate: SyncCandidate): SyncRemoteResult =
        callCreate(candidate) { credentials, accountId, mediaId, unit ->
            providerBinding.syncDataSource.createEntry(
                credentials = credentials,
                accountId = accountId,
                mediaId = mediaId,
                unit = unit,
                status =
                    database
                        .collectionActivityDao()
                        .get(
                            candidate.current.accountId.value,
                            candidate.operation.groupId.value,
                        )?.afterStatus
                        ?.let { ProviderCollectionStatus.valueOf(it.name) }
                        ?: candidate.current.status.toProviderStatus(),
                progress =
                    database
                        .collectionActivityDao()
                        .get(
                            candidate.current.accountId.value,
                            candidate.operation.groupId.value,
                        )?.afterProgress ?: candidate.current.progress.value,
            )
        }

    private suspend fun remove(candidate: SyncCandidate): SyncRemoteResult =
        callRemove(candidate) { credentials, accountId, entryId, mediaId, unit ->
            providerBinding.syncDataSource.removeEntry(credentials, accountId, entryId, mediaId, unit)
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

    private suspend fun callCreate(
        candidate: SyncCandidate,
        request: suspend (
            io.morgan.mio.data.provider.api.ProviderCredentials,
            ProviderAccountId,
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
            val localEntry =
                database.syncDao().getEntry(candidate.current.entryId.value)
                    ?: return SyncRemoteResult.Rejected()
            val mediaMapping =
                database.syncDao().getMediaProviderId(localEntry.mediaId, storedProvider)
                    ?: return SyncRemoteResult.Rejected()
            var alreadyPresent = false
            val result =
                credentialReader.execute(accountId) { verified ->
                    when (
                        val found =
                            providerBinding.syncDataSource.findEntry(
                                verified.credentials,
                                verified.remoteAccountId,
                                ProviderMediaId(mediaMapping.remoteMediaId),
                                candidate.current.unit.toProviderUnit(),
                            )
                    ) {
                        is ProviderResult.Failure -> found
                        is ProviderResult.Success -> {
                            val existing = found.value
                            if (existing != null) {
                                alreadyPresent = true
                                ProviderResult.Success(existing)
                            } else {
                                request(
                                    verified.credentials,
                                    verified.remoteAccountId,
                                    ProviderMediaId(mediaMapping.remoteMediaId),
                                    candidate.current.unit.toProviderUnit(),
                                )
                            }
                        }
                    }
                }
            when (result) {
                is ProviderResult.Success -> {
                    val value = result.value
                    if (value.accountId.value != accountMapping.remoteAccountId ||
                        value.mediaId.value != mediaMapping.remoteMediaId ||
                        value.progressUnit != candidate.current.unit.toProviderUnit()
                    ) {
                        SyncRemoteResult.InvalidData
                    } else {
                        val localMapping =
                            database.providerIdDao().findCollectionEntryProviderId(
                                entryId = candidate.current.entryId.value,
                                provider = storedProvider,
                            )
                        val remoteMapping =
                            database.syncDao().findEntryProviderIdByRemote(storedProvider, value.id.value)
                        if (
                            localMapping?.remoteCollectionEntryId?.takeIf { id -> id != value.id.value } != null ||
                            remoteMapping?.collectionEntryId?.takeIf { id ->
                                id != candidate.current.entryId.value
                            } !=
                            null
                        ) {
                            SyncRemoteResult.Conflict()
                        } else {
                            val entryMapping =
                                io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity(
                                    provider = storedProvider,
                                    remoteCollectionEntryId = value.id.value,
                                    collectionEntryId = candidate.current.entryId.value,
                                )
                            if (localMapping == null) {
                                database.providerIdDao().upsertCollectionEntryProviderIds(listOf(entryMapping))
                            }
                            try {
                                if (alreadyPresent) {
                                    SyncRemoteResult.Conflict(value.toSyncEntryState(candidate.current))
                                } else {
                                    SyncRemoteResult.Success(value.toSyncEntryState(candidate.current))
                                }
                            } catch (_: IllegalArgumentException) {
                                SyncRemoteResult.InvalidData
                            }
                        }
                    }
                }
                is ProviderResult.Failure -> result.failure.toDomainResult()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SQLiteException) {
            throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
        }
    }

    private suspend fun callRemove(
        candidate: SyncCandidate,
        request: suspend (
            io.morgan.mio.data.provider.api.ProviderCredentials,
            ProviderAccountId,
            ProviderCollectionEntryId,
            ProviderMediaId,
            ProviderProgressUnit,
        ) -> ProviderResult<Unit>,
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
            val localEntry =
                database.syncDao().getEntry(candidate.current.entryId.value)
                    ?: return SyncRemoteResult.Rejected()
            val mediaMapping =
                database.syncDao().getMediaProviderId(localEntry.mediaId, storedProvider)
                    ?: return SyncRemoteResult.Rejected()
            val found =
                credentialReader.execute(accountId) { verified ->
                    providerBinding.syncDataSource.findEntry(
                        verified.credentials,
                        verified.remoteAccountId,
                        ProviderMediaId(mediaMapping.remoteMediaId),
                        candidate.current.unit.toProviderUnit(),
                    )
                }
            when (found) {
                is ProviderResult.Failure -> return found.failure.toDomainResult()
                is ProviderResult.Success -> {
                    val value = found.value ?: return SyncRemoteResult.Removed
                    if (value.accountId.value != accountMapping.remoteAccountId ||
                        value.mediaId.value != mediaMapping.remoteMediaId ||
                        value.progressUnit != candidate.current.unit.toProviderUnit()
                    ) {
                        return SyncRemoteResult.InvalidData
                    }
                    if (value.id.value != entryMapping?.remoteCollectionEntryId ||
                        value.status.name != localEntry.remoteStatus.name ||
                        value.progress != localEntry.remoteProgress
                    ) {
                        val owner = database.syncDao().findEntryProviderIdByRemote(storedProvider, value.id.value)
                        if (owner != null &&
                            owner.collectionEntryId != localEntry.collectionEntryId
                        ) {
                            return SyncRemoteResult.InvalidData
                        }
                        database.inTransaction {
                            database.providerIdDao().deleteCollectionEntryProviderIds(localEntry.collectionEntryId)
                            database.providerIdDao().upsertCollectionEntryProviderIds(
                                listOf(
                                    io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity(
                                        storedProvider,
                                        value.id.value,
                                        localEntry.collectionEntryId,
                                    ),
                                ),
                            )
                        }
                        return SyncRemoteResult.Conflict(value.toSyncEntryState(candidate.current))
                    }
                }
            }
            val result =
                credentialReader.execute(accountId) { verified ->
                    request(
                        verified.credentials,
                        verified.remoteAccountId,
                        ProviderCollectionEntryId(requireNotNull(entryMapping).remoteCollectionEntryId),
                        ProviderMediaId(mediaMapping.remoteMediaId),
                        candidate.current.unit.toProviderUnit(),
                    )
                }
            when (result) {
                is ProviderResult.Success -> SyncRemoteResult.Removed
                is ProviderResult.Failure -> result.failure.toDomainResult()
            }
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
