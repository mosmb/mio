package io.morgan.mio.data.sync

import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.MioDatabaseFactory
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.PendingOperationEntity
import io.morgan.mio.core.database.entity.PendingOperationGroupEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredPendingOperationKind
import io.morgan.mio.core.database.model.StoredPendingOperationState
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.testing.provider.FakeProviderCredentialStore
import io.morgan.mio.core.testing.provider.FakeProviderIdentityDataSource
import io.morgan.mio.core.testing.provider.FakeProviderSyncDataSource
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.data.provider.api.ProviderAccountId
import io.morgan.mio.data.provider.api.ProviderCollectionEntry
import io.morgan.mio.data.provider.api.ProviderCollectionEntryId
import io.morgan.mio.data.provider.api.ProviderCollectionStatus
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderIdentity
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.provider.api.ProviderMediaId
import io.morgan.mio.data.provider.api.ProviderProgressUnit
import io.morgan.mio.data.provider.api.ProviderResult
import io.morgan.mio.data.sync.local.RoomSyncAccountLocalDataSource
import io.morgan.mio.data.sync.local.RoomSyncLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.ProjectSyncEntryUseCase
import io.morgan.mio.domain.sync.SyncRemoteResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class ProviderSyncRemoteDataSourceTest {
    private lateinit var database: MioDatabase

    @Before
    fun setUp() {
        database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
        runTest { seed(database) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun remoteAccountIdentityMapsToDistinctOpaqueLocalIdentityForBothProviders() =
        runTest {
            for (provider in ProviderKey.entries) {
                val stored =
                    if (provider ==
                        ProviderKey.ANILIST
                    ) {
                        StoredConnectedProvider.ANILIST
                    } else {
                        StoredConnectedProvider.MY_ANIME_LIST
                    }
                database.selectedAccountDao().replaceSelectedAccount(
                    SelectedAccountEntity("selected", "account-sync", stored, "Fixture", null),
                    AccountProviderIdEntity("account-sync", stored, "42"),
                )
                database.mediaDao().upsertMedia(listOf(media("media-one")))
                database.collectionEntryDao().upsertEntries(listOf(entry("entry-one", "media-one")))
                database.providerIdDao().upsertMediaProviderIds(
                    listOf(MediaProviderIdEntity(stored, "101", "media-one")),
                )
                database.providerIdDao().upsertCollectionEntryProviderIds(
                    listOf(CollectionEntryProviderIdEntity(stored, "201", "entry-one")),
                )
                insertGroup("group-${provider.name}", 0, "entry-one", "operation-${provider.name}")
                val clock =
                    object : Clock {
                        override fun now() = NOW
                    }
                val local = RoomSyncLocalDataSource(database, ProjectSyncEntryUseCase(), clock)
                val lease =
                    checkNotNull(
                        local.acquireLease(AccountId("account-sync"), "owner", NOW, NOW + 120.seconds),
                    )
                val candidate = checkNotNull(local.claimNext(lease, NOW))
                val binding = providerBindingFixture(provider)
                val identity = binding.identityDataSource as FakeProviderIdentityDataSource
                val sync = binding.syncDataSource as FakeProviderSyncDataSource
                val credentials = FakeProviderCredentialStore()
                credentials.write(ProviderCredentials(provider, "fixture-only", null, null, "Bearer"))
                val remote =
                    ProviderSyncRemoteDataSource(
                        database,
                        RoomSyncAccountLocalDataSource(database),
                        binding,
                        credentials,
                    )
                identity.enqueue {
                    ProviderResult.Success(ProviderIdentity(provider, ProviderAccountId("wrong-owner"), "Other", null))
                }
                assertEquals(SyncRemoteResult.Authentication, remote.read(candidate))
                assertEquals(0, sync.calls.size)
                identity.enqueue {
                    ProviderResult.Success(
                        ProviderIdentity(provider, ProviderAccountId("42"), "Fixture", null),
                    )
                }
                repeat(2) {
                    sync.enqueueRead {
                        _,
                        _,
                        _,
                        _,
                        _,
                        ->
                        ProviderResult.Success(
                            ProviderCollectionEntry(
                                ProviderCollectionEntryId("201"),
                                ProviderAccountId("42"),
                                ProviderMediaId("101"),
                                ProviderCollectionStatus.CURRENT,
                                2,
                                ProviderProgressUnit.EPISODE,
                                12,
                                NOW,
                            ),
                        )
                    }
                    val result = remote.read(candidate) as SyncRemoteResult.Success
                    assertEquals(AccountId("account-sync"), result.entry.accountId)
                    assertEquals(2, result.entry.progress.value)
                }
                assertEquals(2, identity.credentials.size)
                local.releaseLease(lease)
                database.selectedAccountDao().deleteAccount("account-sync")
            }
        }

    private suspend fun seed(target: MioDatabase) {
        target.selectedAccountDao().replaceSelectedAccount(
            SelectedAccountEntity(
                selectionKey = "selected",
                accountId = "account-sync",
                connectedProvider = StoredConnectedProvider.ANILIST,
                displayName = "Sync account",
                avatarReference = null,
            ),
            AccountProviderIdEntity(
                accountId = "account-sync",
                provider = StoredConnectedProvider.ANILIST,
                remoteAccountId = "42",
            ),
        )
    }

    private suspend fun insertGroup(
        groupId: String,
        order: Long,
        entryId: String,
        operationId: String,
    ) {
        if (database.collectionEntryDao().getEntry(entryId) == null) {
            val mediaId = if (entryId == "entry-two") "media-two" else "media-one"
            database.mediaDao().upsertMedia(listOf(media(mediaId)))
            database.collectionEntryDao().upsertEntries(listOf(entry(entryId, mediaId)))
        }
        database.pendingOperationDao().insertGroup(
            PendingOperationGroupEntity(
                groupOrder = order,
                operationGroupId = groupId,
                accountId = "account-sync",
                createdAtUtcMs = NOW,
                compensatesGroupId = null,
            ),
        )
        database.pendingOperationDao().insertOperations(
            listOf(
                PendingOperationEntity(
                    operationId = operationId,
                    operationGroupId = groupId,
                    accountId = "account-sync",
                    collectionEntryId = entryId,
                    positionInGroup = 0,
                    kind = StoredPendingOperationKind.SET_PROGRESS_FORWARD,
                    baseProgress = 1,
                    targetProgress = 2,
                    baseStatus = null,
                    targetStatus = null,
                    lifecycleState = StoredPendingOperationState.ENQUEUED,
                    attemptCount = 0,
                    lastAttemptAtUtcMs = null,
                    retryNotBeforeUtcMs = null,
                ),
            ),
        )
    }

    private suspend fun claimAndSet(
        operationId: String,
        state: StoredPendingOperationState,
    ) {
        assertEquals(1, database.syncDao().claimOperation("account-sync", operationId, NOW, NOW - 120.seconds))
        assertEquals(
            1,
            database.syncDao().setOperationState(
                "account-sync",
                operationId,
                state,
                if (state ==
                    StoredPendingOperationState.RETRYABLE_FAILURE
                ) {
                    NOW
                } else {
                    null
                },
            ),
        )
    }

    private fun media(mediaId: String): MediaEntity =
        MediaEntity(
            mediaId = mediaId,
            mediaType = StoredMediaType.ANIME,
            title = "Sync media $mediaId",
            format = StoredMediaFormat.TV,
            publicationStatus = StoredMediaPublicationStatus.RELEASING,
            synopsis = null,
            posterReference = null,
            bannerReference = null,
            episodeTotal = 12,
            chapterTotal = null,
            volumeTotal = null,
        )

    private fun entry(
        entryId: String,
        mediaId: String,
    ): CollectionEntryEntity =
        CollectionEntryEntity(
            collectionEntryId = entryId,
            accountId = "account-sync",
            mediaId = mediaId,
            progressUnit = StoredProgressUnit.EPISODE,
            remoteStatus = StoredCollectionStatus.CURRENT,
            remoteProgress = 1,
            remoteTotal = 12,
            remoteUpdatedAtUtcMs = NOW,
            effectiveStatus = StoredCollectionStatus.CURRENT,
            effectiveProgress = 1,
            effectiveTotal = 12,
            effectiveUpdatedAtUtcMs = NOW,
        )

    private companion object {
        val NOW = Instant.parse("2026-09-11T10:00:00Z")
    }
}
