package io.morgan.mio

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import io.morgan.mio.data.sync.DefaultPendingOperationWriter
import io.morgan.mio.data.sync.local.RoomPendingOperationLocalDataSource
import io.morgan.mio.domain.model.AccountId
import io.morgan.mio.domain.model.CollectionEntryId
import io.morgan.mio.domain.model.OperationGroupId
import io.morgan.mio.domain.model.OperationId
import io.morgan.mio.domain.model.ProgressValue
import io.morgan.mio.domain.sync.OperationValue
import io.morgan.mio.domain.sync.PendingOperation
import io.morgan.mio.domain.sync.PendingOperationGroup
import io.morgan.mio.domain.sync.PendingOperationKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.time.Clock

public class SyncEmulatorFixtureTest {
    @Before
    public fun requireExplicitFixtureOptIn() {
        assumeTrue(
            androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
                .getString("task20Fixture") == "true",
        )
    }

    private val app get() = ApplicationProvider.getApplicationContext<SyncEmulatorApplication>()
    private val database get() = app.appGraph.database
    private val provider get() =
        if (InstrumentationRegistry.getArguments().getString("provider") ==
            "myanimelist"
        ) {
            ProviderKey.MY_ANIME_LIST
        } else {
            ProviderKey.ANILIST
        }
    private val storedProvider get() =
        if (provider ==
            ProviderKey.ANILIST
        ) {
            StoredConnectedProvider.ANILIST
        } else {
            StoredConnectedProvider.MY_ANIME_LIST
        }

    private val initialProgress get() = InstrumentationRegistry.getArguments().getString("progress")?.toInt() ?: 8

    @Test
    public fun seed() =
        runBlocking {
            check(database.syncDao().getSelectedAccount() == null) { "Seed requires empty dedicated app data" }
            database.inTransaction {
                seed(database)
                database.mediaDao().upsertMedia(listOf(media("media-one")))
                database.collectionEntryDao().upsertEntries(listOf(entry("entry-one", "media-one")))
                database.providerIdDao().upsertMediaProviderIds(
                    listOf(
                        MediaProviderIdEntity(
                            storedProvider,
                            if (provider ==
                                ProviderKey.ANILIST
                            ) {
                                "101"
                            } else {
                                "anime/101"
                            },
                            "media-one",
                        ),
                    ),
                )
                database.providerIdDao().upsertCollectionEntryProviderIds(
                    listOf(
                        CollectionEntryProviderIdEntity(
                            storedProvider,
                            if (provider ==
                                ProviderKey.ANILIST
                            ) {
                                "201"
                            } else {
                                "anime/101"
                            },
                            "entry-one",
                        ),
                    ),
                )
            }
            app.appGraph.providerCredentialStore.write(
                ProviderCredentials(provider, "task20-local-fixture-only", null, null, "Bearer"),
            )
            File(app.filesDir, "task20-marker").writeText(provider.name)
            snapshotRows()
        }

    @Test
    public fun snapshot() =
        runBlocking {
            requireFixture()
            snapshotRows()
        }

    @Test
    public fun holdForCli() =
        runBlocking {
            requireFixture()
            val finish = File(app.filesDir, "task20-finish")
            finish.delete()
            ActivityScenario.launch(MainActivity::class.java).use {
                val until = System.currentTimeMillis() + 600_000
                while (!finish.exists() && System.currentTimeMillis() < until) {
                    snapshotRows()
                    kotlinx.coroutines.delay(250)
                }
            }
        }

    @Test
    public fun resumeAfterRestart() = holdForCli()

    @Test
    public fun enqueueAndAwaitDelivery() =
        runBlocking {
            requireFixture()
            val original = checkNotNull(database.syncDao().getEntry("entry-one"))
            val groupId = OperationGroupId("device-${System.currentTimeMillis()}")
            val operation =
                PendingOperation(
                    OperationId("${groupId.value}:progress"),
                    groupId,
                    AccountId("task20-account"),
                    CollectionEntryId("entry-one"),
                    PendingOperationKind.SET_PROGRESS_FORWARD,
                    OperationValue.Progress(ProgressValue(original.effectiveProgress)),
                    OperationValue.Progress(
                        ProgressValue(
                            original.effectiveProgress + 1,
                        ),
                    ),
                    0,
                )
            database.inTransaction {
                database.collectionEntryDao().upsertEntries(
                    listOf(
                        original.copy(
                            effectiveProgress =
                                original.effectiveProgress + 1,
                        ),
                    ),
                )
                DefaultPendingOperationWriter(
                    AccountId("task20-account"),
                    RoomPendingOperationLocalDataSource(database),
                ).enqueue(
                    PendingOperationGroup(groupId, AccountId("task20-account"), Clock.System.now(), listOf(operation)),
                )
            }
            val until = System.currentTimeMillis() + 180_000
            while (System.currentTimeMillis() < until) {
                snapshotRows()
                val current = checkNotNull(database.syncDao().getEntry("entry-one"))
                if (current.remoteProgress == original.effectiveProgress + 1) {
                    assertEquals(current.remoteProgress, current.effectiveProgress)
                    return@runBlocking
                }
                kotlinx.coroutines.delay(250)
            }
            fail("Real WorkManager did not reconcile the pending operation within 180 seconds")
        }

    private fun requireFixture() {
        check(File(app.filesDir, "task20-marker").readText() == provider.name)
    }

    private suspend fun snapshotRows() {
        val entries = database.syncDao().getEntries("task20-account")
        val operations = database.syncDao().getOrderedOperations("task20-account")
        val issues = database.syncDao().getIssues("task20-account")
        val work =
            androidx.work.WorkManager
                .getInstance(
                    app,
                ).getWorkInfosForUniqueWork("mio.pending.task20-account")
                .get()
        File(app.filesDir, "task20-snapshot.txt").writeText(
            buildString {
                appendLine("pid=${android.os.Process.myPid()}")
                appendLine("entries=$entries")
                appendLine("operations=$operations")
                appendLine("issues=$issues")
                appendLine("work=$work")
                appendLine("schedule=${database.syncDao().getSchedule("task20-account")}")
                appendLine("lease=${database.syncDao().getLease("task20-account")}")
            },
        )
    }

    private suspend fun seed(target: MioDatabase) {
        target.selectedAccountDao().replaceSelectedAccount(
            SelectedAccountEntity(
                selectionKey = "selected",
                accountId = "task20-account",
                connectedProvider = storedProvider,
                displayName = "Sync account",
                avatarReference = null,
            ),
            AccountProviderIdEntity(
                accountId = "task20-account",
                provider = storedProvider,
                remoteAccountId = "42",
            ),
        )
    }

    private fun media(mediaId: String): MediaEntity =
        MediaEntity(
            mediaId = mediaId,
            mediaType = StoredMediaType.ANIME,
            title = "Sync media $mediaId",
            format = StoredMediaFormat.TV,
            publicationStatus = StoredMediaPublicationStatus.FINISHED,
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
            accountId = "task20-account",
            mediaId = mediaId,
            progressUnit = StoredProgressUnit.EPISODE,
            remoteStatus = StoredCollectionStatus.CURRENT,
            remoteProgress = initialProgress,
            remoteTotal = 12,
            remoteUpdatedAtUtcMs = NOW,
            effectiveStatus = StoredCollectionStatus.CURRENT,
            effectiveProgress = initialProgress,
            effectiveTotal = 12,
            effectiveUpdatedAtUtcMs = NOW,
        )

    private companion object {
        val NOW = Clock.System.now()
    }
}
