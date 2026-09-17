package io.morgan.mio.data.sync.local

import android.database.sqlite.SQLiteException
import dev.zacsweers.metro.Inject
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.SyncAccountIssueEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.data.sync.mapper.RefreshPersistenceSnapshot
import io.morgan.mio.data.sync.mapper.toDomain
import io.morgan.mio.data.sync.mapper.toRefreshPersistence
import io.morgan.mio.data.sync.mapper.toRemoteSyncEntryState
import io.morgan.mio.data.sync.mapper.toSyncEntryState
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.sync.PendingOperation
import io.morgan.mio.domain.sync.ProjectSyncEntryUseCase
import io.morgan.mio.domain.sync.SyncAccountIssueKind
import io.morgan.mio.domain.sync.SyncCommitResult
import io.morgan.mio.domain.sync.SyncInfrastructureException
import io.morgan.mio.domain.sync.SyncLease
import kotlinx.coroutines.CancellationException
import java.util.UUID
import kotlin.time.Clock

@Inject
public class RoomRefreshLocalDataSource(
    private val database: MioDatabase,
    private val project: ProjectSyncEntryUseCase,
    private val clock: Clock,
) {
    public suspend fun apply(
        lease: SyncLease,
        snapshot: io.morgan.mio.data.provider.api.CompleteProviderImport,
    ): SyncCommitResult =
        fencedCommit {
            val storedProvider = snapshot.provider.toStoredProvider()
            database.inTransaction {
                val now = clock.now()
                if (!ownsLease(lease, now)) return@inTransaction SyncCommitResult.LeaseLost
                val selected = database.syncDao().getSelectedAccount()
                if (
                    selected?.accountId != lease.accountId.value ||
                    selected.connectedProvider != storedProvider
                ) {
                    return@inTransaction SyncCommitResult.AccountGone
                }
                val accountMapping = database.syncDao().getAccountProviderId(lease.accountId.value)
                if (
                    accountMapping == null ||
                    accountMapping.provider != storedProvider ||
                    accountMapping.remoteAccountId != snapshot.accountId.value
                ) {
                    return@inTransaction SyncCommitResult.AccountGone
                }

                val existingMediaLocalIds =
                    snapshot.media
                        .mapNotNull { item ->
                            database
                                .syncDao()
                                .findMediaProviderIdByRemote(storedProvider, item.id.value)
                                ?.let { mapping -> item.id.value to mapping.mediaId }
                        }.toMap()
                val existingEntryLocalIds =
                    snapshot.entries
                        .mapNotNull { item ->
                            val mapping =
                                database.syncDao().findEntryProviderIdByRemote(storedProvider, item.id.value)
                                    ?: return@mapNotNull null
                            val entry = database.syncDao().getEntry(mapping.collectionEntryId)
                            val mediaMapping =
                                entry?.let { localEntry ->
                                    database.syncDao().getMediaProviderId(localEntry.mediaId, storedProvider)
                                }
                            if (
                                entry == null ||
                                entry.accountId != lease.accountId.value ||
                                mediaMapping?.remoteMediaId != item.mediaId.value
                            ) {
                                throw SyncInfrastructureException(SyncInfrastructureException.Kind.CONFIGURATION)
                            }
                            item.id.value to mapping.collectionEntryId
                        }.toMap()
                val persistence =
                    snapshot.toRefreshPersistence(
                        localAccountId = lease.accountId,
                        existingMediaLocalIds = existingMediaLocalIds,
                        existingEntryLocalIds = existingEntryLocalIds,
                    )
                upsertMediaSnapshot(persistence)
                upsertCollectionSnapshot(lease.accountId, persistence)
                database.syncDao().deleteAccountIssueIfKind(
                    accountId = lease.accountId.value,
                    kind = SyncAccountIssueKind.INVALID_REMOTE_DATA.serializedValue(),
                )
                if (!ownsLease(lease, clock.now())) throw LeaseExpiredDuringCommit()
                SyncCommitResult.Applied
            }
        }

    public suspend fun recordFailure(
        lease: SyncLease,
        kind: SyncAccountIssueKind,
    ): SyncCommitResult =
        fencedCommit {
            database.inTransaction {
                val now = clock.now()
                if (!ownsLease(lease, now)) return@inTransaction SyncCommitResult.LeaseLost
                if (database.syncDao().getSelectedAccount()?.accountId != lease.accountId.value) {
                    return@inTransaction SyncCommitResult.AccountGone
                }
                val storedKind = kind.serializedValue()
                val current = database.syncDao().getAccountIssue(lease.accountId.value)
                if (current == null || current.kind != storedKind) {
                    current?.let { issue ->
                        database.syncDao().deleteAccountIssue(lease.accountId.value, issue.issueId)
                    }
                    database.syncDao().upsertAccountIssue(
                        SyncAccountIssueEntity(
                            issueId = "${lease.accountId.value}:refresh:${UUID.randomUUID()}",
                            accountId = lease.accountId.value,
                            kind = storedKind,
                            detectedAtUtcMs = now,
                        ),
                    )
                }
                if (!ownsLease(lease, clock.now())) throw LeaseExpiredDuringCommit()
                SyncCommitResult.Applied
            }
        }

    public suspend fun isAuthenticationBlocked(accountId: AccountId): Boolean =
        storage {
            database.syncDao().getAccountIssue(accountId.value)?.kind ==
                SyncAccountIssueKind.AUTHENTICATION.serializedValue() ||
                database
                    .syncDao()
                    .getIssues(accountId.value)
                    .any { issue -> issue.kind == SyncAccountIssueKind.AUTHENTICATION.serializedValue() }
        }

    private suspend fun upsertMediaSnapshot(snapshot: RefreshPersistenceSnapshot) {
        val mergedMedia =
            snapshot.media.map { incoming ->
                val existing = database.mediaDao().getMedia(incoming.mediaId)
                incoming.mergeMetadata(existing)
            }
        if (mergedMedia.isNotEmpty()) database.mediaDao().upsertMedia(mergedMedia)

        snapshot.genres.groupBy { it.mediaId }.forEach { (mediaId, rows) ->
            database.mediaDao().deleteGenres(mediaId)
            database.mediaDao().upsertGenres(rows)
        }
        snapshot.relatedMedia.groupBy { it.mediaId }.forEach { (mediaId, rows) ->
            database.mediaDao().deleteRelatedMedia(mediaId)
            database.mediaDao().upsertRelatedMedia(rows)
        }
        snapshot.releases.groupBy { it.mediaId }.forEach { (mediaId, rows) ->
            database.releaseDao().deleteReleases(mediaId)
            database.releaseDao().upsertReleases(rows)
        }
        if (snapshot.mediaProviderIds.isNotEmpty()) {
            database.providerIdDao().upsertMediaProviderIds(snapshot.mediaProviderIds)
        }
    }

    private suspend fun upsertCollectionSnapshot(
        accountId: AccountId,
        snapshot: RefreshPersistenceSnapshot,
    ) {
        val pendingByEntry = pendingForAccount(accountId.value).groupBy { it.collectionEntryId.value }
        snapshot.entries.forEach { incoming ->
            require(incoming.accountId == accountId.value)
            val current = database.syncDao().getEntry(incoming.collectionEntryId)
            if (current == null) {
                database.collectionEntryDao().upsertEntries(listOf(incoming))
                return@forEach
            }
            require(current.accountId == accountId.value)
            require(current.progressUnit == incoming.progressUnit)
            val remote =
                if (incoming.remoteUpdatedAtUtcMs >= current.remoteUpdatedAtUtcMs) {
                    incoming.toRemoteSyncEntryState()
                } else {
                    current.toRemoteSyncEntryState()
                }
            val currentState = current.toSyncEntryState()
            val effective =
                project(
                    remote = remote,
                    current = currentState,
                    pending = pendingByEntry[current.collectionEntryId].orEmpty(),
                )
            check(
                database.syncDao().updateEntrySnapshot(
                    entryId = current.collectionEntryId,
                    accountId = accountId.value,
                    mediaId = current.mediaId,
                    remoteStatus = remote.status.toStoredStatus(),
                    remoteProgress = remote.progress.value,
                    remoteTotal = remote.total?.value,
                    remoteUpdatedAtUtcMs = remote.updatedAt,
                    effectiveStatus = effective.status.toStoredStatus(),
                    effectiveProgress = effective.progress.value,
                    effectiveTotal = effective.total?.value,
                    effectiveUpdatedAtUtcMs = effective.updatedAt,
                    localProgressRevision = effective.revision,
                ) == 1,
            )
        }
        if (snapshot.entryProviderIds.isNotEmpty()) {
            database.providerIdDao().upsertCollectionEntryProviderIds(snapshot.entryProviderIds)
        }
    }

    private suspend fun pendingForAccount(accountId: String): List<PendingOperation> =
        database
            .syncDao()
            .getOrderedOperations(accountId)
            .filter { operation ->
                operation.lifecycleState !in
                    setOf(
                        StoredPendingOperationState.DELIVERED,
                        StoredPendingOperationState.CANCELLED,
                    )
            }.map { operation ->
                operation.toDomain(
                    database
                        .syncDao()
                        .getDependenciesForOperation(operation.operationId)
                        .mapTo(linkedSetOf()) { dependency -> OperationId(dependency.dependsOnOperationId) },
                )
            }

    private suspend fun ownsLease(
        lease: SyncLease,
        now: kotlin.time.Instant,
    ): Boolean {
        val selected = database.syncDao().getSelectedAccount()
        val stored = database.syncDao().getLease(lease.accountId.value)
        return selected?.accountId == lease.accountId.value &&
            stored?.ownerToken == lease.ownerToken &&
            stored.expiresAtUtcMs > now
    }

    private suspend fun fencedCommit(block: suspend () -> SyncCommitResult): SyncCommitResult =
        try {
            storage(block)
        } catch (_: LeaseExpiredDuringCommit) {
            SyncCommitResult.LeaseLost
        }

    private suspend fun <T> storage(block: suspend () -> T): T =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SQLiteException) {
            throw SyncInfrastructureException(SyncInfrastructureException.Kind.STORAGE)
        }

    private class LeaseExpiredDuringCommit : RuntimeException()
}

private fun MediaEntity.mergeMetadata(existing: MediaEntity?): MediaEntity {
    if (existing == null) return this
    return copy(
        format = format ?: existing.format,
        publicationStatus =
            if (publicationStatus == io.morgan.mio.core.database.model.StoredMediaPublicationStatus.UNKNOWN) {
                existing.publicationStatus
            } else {
                publicationStatus
            },
        synopsis = synopsis ?: existing.synopsis,
        posterReference = posterReference ?: existing.posterReference,
        bannerReference = bannerReference ?: existing.bannerReference,
        episodeTotal = episodeTotal ?: existing.episodeTotal,
        chapterTotal = chapterTotal ?: existing.chapterTotal,
        volumeTotal = volumeTotal ?: existing.volumeTotal,
        releaseYear = releaseYear ?: existing.releaseYear,
        scorePercent = scorePercent ?: existing.scorePercent,
    )
}

private fun SyncAccountIssueKind.serializedValue(): String =
    when (this) {
        SyncAccountIssueKind.AUTHENTICATION -> "authentication"
        SyncAccountIssueKind.INVALID_REMOTE_DATA -> "invalid_remote_data"
    }

private fun io.morgan.mio.data.provider.api.ProviderKey.toStoredProvider(): StoredConnectedProvider =
    when (this) {
        io.morgan.mio.data.provider.api.ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST
        io.morgan.mio.data.provider.api.ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
    }

private fun io.morgan.mio.domain.collection.CollectionStatus.toStoredStatus(): StoredCollectionStatus =
    when (this) {
        io.morgan.mio.domain.collection.CollectionStatus.CURRENT -> StoredCollectionStatus.CURRENT
        io.morgan.mio.domain.collection.CollectionStatus.PLANNED -> StoredCollectionStatus.PLANNED
        io.morgan.mio.domain.collection.CollectionStatus.PAUSED -> StoredCollectionStatus.PAUSED
        io.morgan.mio.domain.collection.CollectionStatus.COMPLETED -> StoredCollectionStatus.COMPLETED
        io.morgan.mio.domain.collection.CollectionStatus.DROPPED -> StoredCollectionStatus.DROPPED
        io.morgan.mio.domain.collection.CollectionStatus.REPEATING -> StoredCollectionStatus.REPEATING
    }
