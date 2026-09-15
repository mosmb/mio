package io.morgan.mio.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.zacsweers.metro.createDynamicGraphFactory
import io.morgan.mio.core.database.MioDatabase
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
import io.morgan.mio.core.testing.provider.providerBindingFixture
import io.morgan.mio.data.provider.api.ProviderBindingResolver
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.sync.local.RoomSyncAccountLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.sync.SyncRunResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class MetroSyncAccountSessionFactoryTest {
    private lateinit var database: MioDatabase

    @Test
    fun reconstructedGraphPersistsMissingCredentialIssueWithoutAnActiveUiSession() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            TestGraphFixture(context, TestCoroutineScheduler()).use { fixture ->
                val graph = createDynamicGraphFactory<AppGraph.Factory>(fixture.bindings).create(context)
                database = fixture.database
                seed(database)
                insertGroup("group", 1, "entry-one", "operation")
                database.providerIdDao().upsertMediaProviderIds(
                    listOf(MediaProviderIdEntity(StoredConnectedProvider.ANILIST, "101", "media-one")),
                )
                database.providerIdDao().upsertCollectionEntryProviderIds(
                    listOf(CollectionEntryProviderIdEntity(StoredConnectedProvider.ANILIST, "201", "entry-one")),
                )
                val resolver =
                    object : ProviderBindingResolver {
                        override fun bindingFor(provider: ProviderKey) = providerBindingFixture(provider)
                    }
                val factory = MetroSyncAccountSessionFactory(graph, RoomSyncAccountLocalDataSource(database), resolver)
                assertNull(graph.accountGraphLifecycle.activeHandle())
                assertNull(factory.open(AccountId("not-selected")))
                val session = checkNotNull(factory.open(AccountId("account-sync")))
                session.use { assertEquals(SyncRunResult.Complete, it.synchronize()) }
                assertEquals(
                    "authentication",
                    database
                        .syncDao()
                        .getIssues("account-sync")
                        .single()
                        .kind,
                )
                assertEquals(
                    StoredPendingOperationState.TERMINAL_FAILURE,
                    database.syncDao().getOperation("operation")?.lifecycleState,
                )
                assertNull(graph.accountGraphLifecycle.activeHandle())
                assertNull(database.syncDao().getLease("account-sync"))
                assertNotNull(database.syncDao().getSelectedAccount())
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
            database.syncDao().setOperationState("account-sync", operationId, state, null),
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
        val NOW = Instant.parse("2026-08-11T12:00:00Z")
    }
}
