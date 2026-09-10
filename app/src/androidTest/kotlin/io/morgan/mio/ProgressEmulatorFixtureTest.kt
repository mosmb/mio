package io.morgan.mio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.morgan.mio.core.database.MioDatabase
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.ReleaseEntity
import io.morgan.mio.core.database.entity.SELECTED_ACCOUNT_KEY
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class ProgressEmulatorFixtureTest {
    @Test
    fun seed() =
        runBlocking {
            requireFixtureFlag()
            val application = targetApplication()
            val database = application.appGraph.database
            assertFixtureDatabaseIsEmpty(database)

            application.appGraph.providerCredentialStore.write(
                ProviderCredentials(
                    provider = ProviderKey.ANILIST,
                    accessToken = "task19-fixture-access",
                    refreshToken = null,
                    expiresAt = null,
                    tokenType = "Bearer",
                ),
            )
            database.selectedAccountDao().replaceSelectedAccount(
                SelectedAccountEntity(
                    selectionKey = SELECTED_ACCOUNT_KEY,
                    accountId = ACCOUNT_ID,
                    connectedProvider = StoredConnectedProvider.ANILIST,
                    displayName = "Task 19 fixture account",
                    avatarReference = null,
                ),
                AccountProviderIdEntity(
                    accountId = ACCOUNT_ID,
                    provider = StoredConnectedProvider.ANILIST,
                    remoteAccountId = "task19-fixture-provider-account",
                ),
            )

            val now = Clock.System.now()
            database.inTransaction {
                database.mediaDao().upsertMedia(MEDIA.map { media -> media.toEntity() })
                database.collectionEntryDao().upsertEntries(ENTRIES.map { entry -> entry.toEntity(now) })
                database.releaseDao().upsertReleases(RELEASES.map { release -> release.toEntity(now) })
            }

            assertFixtureCounts(database)
        }

    @Test
    fun talkBackFocusAndUndo() {
        requireFixtureFlag()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation =
            instrumentation.getUiAutomation(
                android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
            )
        val services =
            android.provider.Settings.Secure.getString(
                instrumentation.targetContext.contentResolver,
                "enabled_accessibility_services",
            )
        assertTrue(services.contains("TalkBackService"))

        fun find(label: String): android.view.accessibility.AccessibilityNodeInfo {
            fun search(
                node: android.view.accessibility.AccessibilityNodeInfo,
            ): android.view.accessibility.AccessibilityNodeInfo? {
                if (node.contentDescription?.toString() == label ||
                    node.text?.toString() == label ||
                    (label.startsWith("Task 19") && node.contentDescription?.contains(label) == true)
                ) {
                    return node
                }
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    search(child)?.let { return it }
                }
                return null
            }
            repeat(50) {
                automation.rootInActiveWindow?.let { root -> search(root)?.let { return it } }
                Thread.sleep(100)
            }
            error("Missing accessibility node: $label")
        }

        fun focus(
            label: String,
            file: String,
        ) {
            assertTrue(
                find(label).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS),
            )
            Thread.sleep(2_000)
            val bitmap = requireNotNull(automation.takeScreenshot())
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null), file).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        automation.executeShellCommand("am start -W -n io.morgan.mio/.MainActivity").close()
        Thread.sleep(3_000)
        focus("Decrease episode to 7", "talkback-decrease.png")
        focus("Increase episode to 9", "talkback-increase.png")
        assertTrue(
            find("Increase episode to 9").performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),
        )
        focus("Undo", "talkback-undo.png")
        var undoButton = find("Undo")
        while (!undoButton.isClickable) undoButton = requireNotNull(undoButton.parent)
        assertTrue(undoButton.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        find("Increase episode to 9")
        var title = find("Task 19 Now")
        while (!title.isClickable) title = requireNotNull(title.parent)
        assertTrue(title.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        find("In library")
        focus("Increase episode to 9", "talkback-detail-increase.png")
    }

    @Test
    fun snapshot() =
        runBlocking {
            val database = targetApplication().appGraph.database
            val snapshot = org.json.JSONObject()
            listOf(
                "collection_entry",
                "local_progress_activity",
                "pending_operation_group",
                "pending_operation",
            ).forEach { table ->
                val rows = org.json.JSONArray()
                database.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = org.json.JSONObject()
                        cursor.columnNames.forEachIndexed { index, name ->
                            row.put(
                                name,
                                if (cursor.isNull(index)) org.json.JSONObject.NULL else cursor.getString(index),
                            )
                        }
                        rows.put(row)
                    }
                }
                snapshot.put(table, rows)
            }
            val result = android.os.Bundle()
            result.putString("stream", "TASK19_SNAPSHOT=" + snapshot.toString() + "\n")
            InstrumentationRegistry.getInstrumentation().sendStatus(0, result)
        }

    private fun requireFixtureFlag() {
        check(
            InstrumentationRegistry
                .getArguments()
                .getString(FIXTURE_ARGUMENT) == "true",
        ) {
            "Task 19 fixture seeding requires -e $FIXTURE_ARGUMENT true"
        }
    }

    private fun targetApplication(): MioApplication =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .applicationContext as MioApplication

    private fun assertFixtureDatabaseIsEmpty(database: MioDatabase) {
        listOf(
            "selected_account",
            "account_provider_id",
            "media",
            "collection_entry",
            "release",
            "local_progress_activity",
            "pending_operation_group",
            "pending_operation",
        ).forEach { table ->
            assertEquals("fixture database must be empty before seeding $table", 0, countRows(database, table))
        }
    }

    private fun assertFixtureCounts(database: MioDatabase) {
        assertEquals(MEDIA.size, countRows(database, "media"))
        assertEquals(ENTRIES.size, countRows(database, "collection_entry"))
        assertEquals(RELEASES.size, countRows(database, "release"))
        assertEquals(0, countRows(database, "local_progress_activity"))
        assertEquals(0, countRows(database, "pending_operation_group"))
        assertEquals(0, countRows(database, "pending_operation"))
    }

    private fun countRows(
        database: MioDatabase,
        table: String,
    ): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private data class FixtureMedia(
        val id: String,
        val title: String,
        val type: StoredMediaType = StoredMediaType.ANIME,
        val episodeTotal: Int? = null,
        val chapterTotal: Int? = null,
        val volumeTotal: Int? = null,
    ) {
        fun toEntity(): MediaEntity =
            MediaEntity(
                mediaId = id,
                mediaType = type,
                title = title,
                format = StoredMediaFormat.TV,
                publicationStatus = StoredMediaPublicationStatus.RELEASING,
                synopsis = null,
                posterReference = null,
                bannerReference = null,
                episodeTotal = episodeTotal,
                chapterTotal = chapterTotal,
                volumeTotal = volumeTotal,
            )
    }

    private data class FixtureEntry(
        val id: String,
        val mediaId: String,
        val unit: StoredProgressUnit,
        val status: StoredCollectionStatus,
        val progress: Int,
        val total: Int?,
        val ageMinutes: Int,
    ) {
        fun toEntity(now: Instant): CollectionEntryEntity {
            val updatedAt = now - ageMinutes.minutes
            return CollectionEntryEntity(
                collectionEntryId = id,
                accountId = ACCOUNT_ID,
                mediaId = mediaId,
                progressUnit = unit,
                remoteStatus = status,
                remoteProgress = progress,
                remoteTotal = total,
                remoteUpdatedAtUtcMs = updatedAt,
                effectiveStatus = status,
                effectiveProgress = progress,
                effectiveTotal = total,
                effectiveUpdatedAtUtcMs = updatedAt,
            )
        }
    }

    private data class FixtureRelease(
        val mediaId: String,
        val unit: StoredProgressUnit,
        val number: Int,
        val ageMinutes: Int,
    ) {
        fun toEntity(now: Instant): ReleaseEntity =
            ReleaseEntity(
                mediaId = mediaId,
                progressUnit = unit,
                unitNumber = number,
                timeKind = StoredReleaseTimeKind.TRUSTED,
                releaseInstantUtcMs = now - ageMinutes.minutes,
            )
    }

    private companion object {
        const val FIXTURE_ARGUMENT = "mioTask19Fixture"
        const val ACCOUNT_ID = "task19-emulator-account"
        const val NOW_MEDIA_ID = "task19-emulator-now"
        const val NOW_ENTRY_ID = "task19-emulator-now-entry"

        val MEDIA =
            listOf(
                FixtureMedia(NOW_MEDIA_ID, "Task 19 Now", episodeTotal = 12),
                FixtureMedia(
                    "task19-emulator-chapter",
                    "Task 19 Chapter",
                    type = StoredMediaType.MANGA,
                    chapterTotal = 50,
                ),
                FixtureMedia("task19-emulator-volume", "Task 19 Volume", type = StoredMediaType.MANGA, volumeTotal = 3),
                FixtureMedia("task19-emulator-zero", "Task 19 Zero Unknown", episodeTotal = null),
                FixtureMedia("task19-emulator-final", "Task 19 Final", episodeTotal = 12),
                FixtureMedia("task19-emulator-planned", "Task 19 Planned", episodeTotal = 12),
                FixtureMedia("task19-emulator-paused", "Task 19 Paused", episodeTotal = 12),
                FixtureMedia("task19-emulator-dropped", "Task 19 Dropped", episodeTotal = 12),
                FixtureMedia("task19-emulator-completed", "Task 19 Completed", episodeTotal = 12),
                FixtureMedia("task19-emulator-repeating", "Task 19 Repeating", episodeTotal = 12),
            )

        val ENTRIES =
            listOf(
                FixtureEntry(
                    NOW_ENTRY_ID,
                    NOW_MEDIA_ID,
                    StoredProgressUnit.EPISODE,
                    StoredCollectionStatus.CURRENT,
                    8,
                    12,
                    0,
                ),
                FixtureEntry(
                    "task19-emulator-chapter-entry",
                    "task19-emulator-chapter",
                    StoredProgressUnit.CHAPTER,
                    StoredCollectionStatus.CURRENT,
                    41,
                    50,
                    1,
                ),
                FixtureEntry(
                    "task19-emulator-volume-entry",
                    "task19-emulator-volume",
                    StoredProgressUnit.VOLUME,
                    StoredCollectionStatus.CURRENT,
                    2,
                    3,
                    2,
                ),
                FixtureEntry(
                    "task19-emulator-zero-entry",
                    "task19-emulator-zero",
                    StoredProgressUnit.EPISODE,
                    StoredCollectionStatus.CURRENT,
                    0,
                    null,
                    3,
                ),
                FixtureEntry(
                    "task19-emulator-final-entry",
                    "task19-emulator-final",
                    StoredProgressUnit.EPISODE,
                    StoredCollectionStatus.CURRENT,
                    11,
                    12,
                    4,
                ),
                FixtureEntry(
                    "task19-emulator-planned-entry",
                    "task19-emulator-planned",
                    StoredProgressUnit.EPISODE,
                    StoredCollectionStatus.PLANNED,
                    0,
                    12,
                    5,
                ),
                FixtureEntry(
                    "task19-emulator-paused-entry",
                    "task19-emulator-paused",
                    StoredProgressUnit.EPISODE,
                    StoredCollectionStatus.PAUSED,
                    3,
                    12,
                    6,
                ),
                FixtureEntry(
                    "task19-emulator-dropped-entry",
                    "task19-emulator-dropped",
                    StoredProgressUnit.EPISODE,
                    StoredCollectionStatus.DROPPED,
                    2,
                    12,
                    7,
                ),
                FixtureEntry(
                    "task19-emulator-completed-entry",
                    "task19-emulator-completed",
                    StoredProgressUnit.EPISODE,
                    StoredCollectionStatus.COMPLETED,
                    12,
                    12,
                    8,
                ),
                FixtureEntry(
                    "task19-emulator-repeating-entry",
                    "task19-emulator-repeating",
                    StoredProgressUnit.EPISODE,
                    StoredCollectionStatus.REPEATING,
                    4,
                    12,
                    9,
                ),
            )

        val RELEASES =
            listOf(
                FixtureRelease(NOW_MEDIA_ID, StoredProgressUnit.EPISODE, 9, 30),
                FixtureRelease("task19-emulator-chapter", StoredProgressUnit.CHAPTER, 42, 20),
                FixtureRelease("task19-emulator-volume", StoredProgressUnit.VOLUME, 3, 10),
                FixtureRelease("task19-emulator-final", StoredProgressUnit.EPISODE, 12, 5),
            )
    }
}
