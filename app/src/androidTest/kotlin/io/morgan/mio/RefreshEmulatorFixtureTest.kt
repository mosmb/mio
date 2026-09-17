package io.morgan.mio

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.morgan.mio.core.common.identity.StableOpaqueIdFactory
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.CollectionEntryProviderIdEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.MediaProviderIdEntity
import io.morgan.mio.core.database.entity.ReleaseEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import io.morgan.mio.core.database.model.StoredReleaseTimeKind
import io.morgan.mio.data.provider.api.ProviderCredentials
import io.morgan.mio.data.provider.api.ProviderKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

public class RefreshEmulatorFixtureTest {
    private val app get() = ApplicationProvider.getApplicationContext<SyncEmulatorApplication>()
    private val database get() = app.appGraph.database
    private val provider: ProviderKey
        get() =
            if (InstrumentationRegistry.getArguments().getString("provider") == "myanimelist") {
                ProviderKey.MY_ANIME_LIST
            } else {
                ProviderKey.ANILIST
            }
    private val storedProvider: StoredConnectedProvider
        get() =
            when (provider) {
                ProviderKey.ANILIST -> StoredConnectedProvider.ANILIST
                ProviderKey.MY_ANIME_LIST -> StoredConnectedProvider.MY_ANIME_LIST
            }
    private val marker get() = File(app.filesDir, MARKER_FILE)

    @Before
    public fun requireExplicitTask21Composition() {
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString("task20Fixture") == "true") {
            "Task 21 fixture requires -e task20Fixture true"
        }
        check(arguments.getString("task21Fixture") == "true") {
            "Task 21 fixture requires -e task21Fixture true"
        }
        check(arguments.getString("provider") in setOf("anilist", "myanimelist")) {
            "Task 21 fixture requires an explicit provider"
        }
    }

    @Test
    public fun seed() =
        runBlocking {
            check(!marker.exists()) { "Task 21 fixture is already marked; refusing to reseed" }
            assertFixtureDatabaseIsEmpty(database)
            val now = Clock.System.now()
            val media = SEEDED_ROWS.map { row -> row.mediaEntity() }
            val entries = SEEDED_ROWS.map { row -> row.entryEntity(now) }
            val mediaIds =
                SEEDED_ROWS.map { row ->
                    MediaProviderIdEntity(
                        provider = storedProvider,
                        remoteMediaId = row.remoteMediaId(),
                        mediaId = row.localMediaId(),
                    )
                }
            val entryIds =
                SEEDED_ROWS.map { row ->
                    CollectionEntryProviderIdEntity(
                        provider = storedProvider,
                        remoteCollectionEntryId = row.remoteEntryId(),
                        collectionEntryId = row.localEntryId(),
                    )
                }

            database.inTransaction {
                database.selectedAccountDao().replaceSelectedAccount(
                    SelectedAccountEntity(
                        selectionKey = "selected",
                        accountId = ACCOUNT_ID,
                        connectedProvider = storedProvider,
                        displayName = "Task 21 fixture account",
                        avatarReference = null,
                    ),
                    AccountProviderIdEntity(
                        accountId = ACCOUNT_ID,
                        provider = storedProvider,
                        remoteAccountId = REMOTE_ACCOUNT_ID,
                    ),
                )
                database.mediaDao().upsertMedia(media)
                database.collectionEntryDao().upsertEntries(entries)
                database.providerIdDao().upsertMediaProviderIds(mediaIds)
                database.providerIdDao().upsertCollectionEntryProviderIds(entryIds)
                database.releaseDao().upsertReleases(
                    listOf(
                        ReleaseEntity(
                            mediaId = SEEDED_ROWS.first().localMediaId(),
                            progressUnit = StoredProgressUnit.EPISODE,
                            unitNumber = 9,
                            timeKind = StoredReleaseTimeKind.TRUSTED,
                            releaseInstantUtcMs = now - 60.seconds,
                        ),
                    ),
                )
            }
            app.appGraph.providerCredentialStore.write(
                ProviderCredentials(
                    provider = provider,
                    accessToken = "task21-fictional-seed-access",
                    refreshToken = null,
                    expiresAt = null,
                    tokenType = "Bearer",
                ),
            )
            marker.writeText("provider=${provider.name}\naccount=$ACCOUNT_ID\nremote=$REMOTE_ACCOUNT_ID\n")
            assertEquals(SEEDED_ROWS.size, database.syncDao().getEntries(ACCOUNT_ID).size)
            snapshotRows()
        }

    @Test
    public fun snapshot() =
        runBlocking {
            requireFixture()
            snapshotRows()
        }

    @Test
    public fun holdForCli() = holdActivityUntilReleased()

    @Test
    public fun resumeAfterRestart() = holdActivityUntilReleased()

    private fun holdActivityUntilReleased() =
        runBlocking {
            requireFixture()
            val finish = File(app.filesDir, FINISH_FILE)
            finish.delete()
            ActivityScenario.launch(MainActivity::class.java).use {
                val deadline = System.currentTimeMillis() + HOLD_TIMEOUT_MILLIS
                while (!finish.exists() && System.currentTimeMillis() < deadline) {
                    snapshotRows()
                    delay(SNAPSHOT_INTERVAL_MILLIS)
                }
                check(finish.exists()) { "Task 21 CLI hold was not released before timeout" }
            }
        }

    private suspend fun requireFixture() {
        check(marker.isFile) { "Task 21 fixture marker is missing; seed this disposable app first" }
        check(marker.readText().contains("provider=${provider.name}")) {
            "Task 21 fixture provider does not match the requested provider"
        }
        check(database.syncDao().getSelectedAccount()?.accountId == ACCOUNT_ID) {
            "Task 21 marker exists but its selected account is missing"
        }
    }

    private suspend fun snapshotRows() {
        val entries = database.syncDao().getEntries(ACCOUNT_ID)
        val operations = database.syncDao().getOrderedOperations(ACCOUNT_ID)
        val entryIssues = database.syncDao().getIssues(ACCOUNT_ID)
        val accountIssue = database.syncDao().getAccountIssue(ACCOUNT_ID)
        val schedule = database.syncDao().getSchedule(ACCOUNT_ID)
        val lease = database.syncDao().getLease(ACCOUNT_ID)
        val work =
            androidx.work.WorkManager
                .getInstance(app)
                .getWorkInfosForUniqueWork("mio.pending.$ACCOUNT_ID")
                .get()
                .map { info -> "${info.id}:${info.state}:${info.runAttemptCount}:${info.tags.sorted()}" }
        val snapshot =
            buildString {
                appendLine("pid=${android.os.Process.myPid()}")
                appendLine("provider=${provider.name}")
                appendLine("capturedAt=${Clock.System.now()}")
                appendLine("entries=$entries")
                appendLine("operations=$operations")
                appendLine("entryIssues=$entryIssues")
                appendLine("accountIssue=$accountIssue")
                appendLine("activities=${tableRows("local_progress_activity")}")
                appendLine("pendingGroups=${tableRows("pending_operation_group")}")
                appendLine("pendingDependencies=${tableRows("pending_operation_dependency")}")
                appendLine("schedule=$schedule")
                appendLine("lease=$lease")
                appendLine("work=$work")
            }
        val temporary = File(app.filesDir, "$SNAPSHOT_FILE.tmp")
        temporary.writeText(snapshot)
        check(temporary.renameTo(File(app.filesDir, SNAPSHOT_FILE))) {
            "Could not publish the Task 21 read-only snapshot"
        }
    }

    private fun tableRows(table: String): List<String> =
        database.openHelper.readableDatabase
            .query("SELECT * FROM $table ORDER BY rowid")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            cursor.columnNames
                                .mapIndexed { index, name ->
                                    "$name=${if (cursor.isNull(index)) "null" else cursor.getString(index)}"
                                }.joinToString(prefix = "{", postfix = "}"),
                        )
                    }
                }
            }

    private fun assertFixtureDatabaseIsEmpty(target: MioDatabase) {
        FIXTURE_TABLES.forEach { table ->
            val count =
                target.openHelper.readableDatabase
                    .query("SELECT COUNT(*) FROM $table")
                    .use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        cursor.getInt(0)
                    }
            assertEquals("fixture database must be empty before seeding $table", 0, count)
        }
    }

    private fun SeedRow.remoteMediaId(): String =
        when (provider) {
            ProviderKey.ANILIST -> remoteMediaNumericId
            ProviderKey.MY_ANIME_LIST -> "$providerPath/$remoteMediaNumericId"
        }

    private fun SeedRow.remoteEntryId(): String =
        when (provider) {
            ProviderKey.ANILIST -> remoteEntryNumericId
            ProviderKey.MY_ANIME_LIST -> "$providerPath/$remoteMediaNumericId"
        }

    private fun SeedRow.localMediaId(): String =
        StableOpaqueIdFactory.create(storedProvider.serializedValue, "media", remoteMediaId())

    private fun SeedRow.localEntryId(): String =
        StableOpaqueIdFactory.create(storedProvider.serializedValue, "collection-entry", remoteEntryId())

    private fun SeedRow.mediaEntity(): MediaEntity =
        MediaEntity(
            mediaId = localMediaId(),
            mediaType = mediaType,
            title = title,
            format = format,
            publicationStatus = publicationStatus,
            synopsis = null,
            posterReference = null,
            bannerReference = null,
            episodeTotal = episodeTotal,
            chapterTotal = chapterTotal,
            volumeTotal = volumeTotal,
            releaseYear = 2026,
            scorePercent = 75,
        )

    private fun SeedRow.entryEntity(now: kotlin.time.Instant): CollectionEntryEntity =
        CollectionEntryEntity(
            collectionEntryId = localEntryId(),
            accountId = ACCOUNT_ID,
            mediaId = localMediaId(),
            progressUnit = progressUnit,
            remoteStatus = StoredCollectionStatus.CURRENT,
            remoteProgress = progress,
            remoteTotal = total,
            remoteUpdatedAtUtcMs = now,
            effectiveStatus = StoredCollectionStatus.CURRENT,
            effectiveProgress = progress,
            effectiveTotal = total,
            effectiveUpdatedAtUtcMs = now,
        )

    private data class SeedRow(
        val remoteMediaNumericId: String,
        val remoteEntryNumericId: String,
        val providerPath: String,
        val title: String,
        val mediaType: StoredMediaType,
        val format: StoredMediaFormat,
        val publicationStatus: StoredMediaPublicationStatus,
        val progressUnit: StoredProgressUnit,
        val progress: Int,
        val total: Int,
        val episodeTotal: Int? = null,
        val chapterTotal: Int? = null,
        val volumeTotal: Int? = null,
    )

    private companion object {
        const val ACCOUNT_ID = "task21-account"
        const val REMOTE_ACCOUNT_ID = "42"
        const val MARKER_FILE = "task21-fixture-marker"
        const val FINISH_FILE = "task21-finish"
        const val SNAPSHOT_FILE = "task21-snapshot.txt"
        const val HOLD_TIMEOUT_MILLIS = 600_000L
        const val SNAPSHOT_INTERVAL_MILLIS = 250L

        val FIXTURE_TABLES =
            listOf(
                "selected_account",
                "account_provider_id",
                "media",
                "release",
                "media_provider_id",
                "collection_entry",
                "collection_entry_provider_id",
                "local_progress_activity",
                "pending_operation_group",
                "pending_operation",
                "pending_operation_dependency",
                "sync_issue",
                "sync_account_issue",
                "sync_lease",
                "sync_schedule",
            )

        val SEEDED_ROWS =
            listOf(
                SeedRow(
                    remoteMediaNumericId = "101",
                    remoteEntryNumericId = "501",
                    providerPath = "anime",
                    title = "Task 21 Now Fixture",
                    mediaType = StoredMediaType.ANIME,
                    format = StoredMediaFormat.TV,
                    publicationStatus = StoredMediaPublicationStatus.RELEASING,
                    progressUnit = StoredProgressUnit.EPISODE,
                    progress = 8,
                    total = 12,
                    episodeTotal = 12,
                ),
                SeedRow(
                    remoteMediaNumericId = "202",
                    remoteEntryNumericId = "602",
                    providerPath = "manga",
                    title = "Task 21 Chapter Fixture",
                    mediaType = StoredMediaType.MANGA,
                    format = StoredMediaFormat.MANGA,
                    publicationStatus = StoredMediaPublicationStatus.FINISHED,
                    progressUnit = StoredProgressUnit.CHAPTER,
                    progress = 20,
                    total = 60,
                    chapterTotal = 60,
                    volumeTotal = 5,
                ),
                SeedRow(
                    remoteMediaNumericId = "303",
                    remoteEntryNumericId = "603",
                    providerPath = "manga",
                    title = "Task 21 Volume Fixture",
                    mediaType = StoredMediaType.MANGA,
                    format = StoredMediaFormat.MANGA,
                    publicationStatus = StoredMediaPublicationStatus.FINISHED,
                    progressUnit = StoredProgressUnit.VOLUME,
                    progress = 2,
                    total = 7,
                    chapterTotal = 100,
                    volumeTotal = 7,
                ),
            )
    }
}
