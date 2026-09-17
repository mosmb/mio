package io.morgan.mio.data.sync

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.data.provider.api.CompleteProviderImport
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCredentialStorageException
import io.morgan.mio.data.provider.api.ProviderFailure
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.SyncInfrastructureException
import kotlinx.coroutines.CancellationException

@Inject
public class ProviderRefreshRemoteDataSource internal constructor(
    private val database: MioDatabase,
    private val credentialReader: ProviderSyncCredentialReader,
    private val providerBinding: io.morgan.mio.data.provider.api.ProviderBinding,
) {
    public suspend fun read(accountId: AccountId): ProviderResult<CompleteProviderImport> =
        try {
            credentialReader.execute(accountId) { verified ->
                val initial =
                    providerBinding.initialImportDataSource.fetchCompleteImport(
                        credentials = verified.credentials,
                        accountId = verified.remoteAccountId,
                    )
                when (initial) {
                    is ProviderResult.Failure -> initial
                    is ProviderResult.Success -> normalizeExistingEntries(accountId, verified, initial.value)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: ProviderCredentialStorageException) {
            throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
        } catch (_: SQLiteException) {
            throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
        }

    private suspend fun normalizeExistingEntries(
        accountId: AccountId,
        credentials: VerifiedProviderCredentials,
        imported: CompleteProviderImport,
    ): ProviderResult<CompleteProviderImport> {
        if (
            imported.provider != providerBinding.provider ||
            imported.accountId != credentials.remoteAccountId
        ) {
            return ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }
        val storedProvider = providerBinding.provider.toStoredProvider()
        val entries = mutableListOf<ProviderCollectionEntry>()
        for (entry in imported.entries) {
            val mapping = database.syncDao().findEntryProviderIdByRemote(storedProvider, entry.id.value)
            if (mapping == null) {
                entries += entry
                continue
            }
            val local =
                database.syncDao().getEntry(mapping.collectionEntryId)
                    ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            if (local.accountId != accountId.value) {
                return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            }
            val mediaMapping =
                database.syncDao().getMediaProviderId(local.mediaId, storedProvider)
                    ?: return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            if (mediaMapping.remoteMediaId != entry.mediaId.value) {
                return ProviderResult.Failure(ProviderFailure.MalformedResponse)
            }
            val fixedUnit = local.progressUnit.toProviderUnit()
            val reread =
                providerBinding.syncDataSource.readEntry(
                    credentials = credentials.credentials,
                    accountId = credentials.remoteAccountId,
                    entryId = ProviderCollectionEntryId(mapping.remoteCollectionEntryId),
                    mediaId = ProviderMediaId(mediaMapping.remoteMediaId),
                    unit = fixedUnit,
                )
            when (reread) {
                is ProviderResult.Failure -> return reread
                is ProviderResult.Success -> {
                    val value = reread.value
                    if (
                        value.providerIdentityIsInvalid(
                            accountId = credentials.remoteAccountId,
                            entryId = mapping.remoteCollectionEntryId,
                            mediaId = mediaMapping.remoteMediaId,
                            unit = fixedUnit,
                        )
                    ) {
                        return ProviderResult.Failure(ProviderFailure.MalformedResponse)
                    }
                    entries += value
                }
            }
        }
        return try {
            ProviderResult.Success(
                CompleteProviderImport(
                    provider = imported.provider,
                    accountId = imported.accountId,
                    media = imported.media,
                    entries = entries,
                ),
            )
        } catch (_: IllegalArgumentException) {
            ProviderResult.Failure(ProviderFailure.MalformedResponse)
        }
    }
}

private fun ProviderCollectionEntry.providerIdentityIsInvalid(
    accountId: ProviderAccountId,
    entryId: String,
    mediaId: String,
    unit: ProviderProgressUnit,
): Boolean =
    this.accountId != accountId ||
        this.id.value != entryId ||
        this.mediaId.value != mediaId ||
        this.progressUnit != unit

private fun ProviderKey.toStoredProvider(): StoredConnectedProvider =
    when (this) {
        ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST
        ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
    }

private fun StoredProgressUnit.toProviderUnit(): ProviderProgressUnit =
    when (this) {
        StoredProgressUnit.EPISODE -> ProviderProgressUnit.EPISODE
        StoredProgressUnit.CHAPTER -> ProviderProgressUnit.CHAPTER
        StoredProgressUnit.VOLUME -> ProviderProgressUnit.VOLUME
    }
