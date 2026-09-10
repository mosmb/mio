package io.morgan.mio.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import io.morgan.mio.core.database.entity.AccountProviderIdEntity
import io.morgan.mio.core.database.entity.CollectionEntryEntity
import io.morgan.mio.core.database.entity.LocalProgressActivityEntity
import io.morgan.mio.core.database.entity.MediaEntity
import io.morgan.mio.core.database.entity.SelectedAccountEntity
import io.morgan.mio.core.database.model.StoredCollectionStatus
import io.morgan.mio.core.database.model.StoredConnectedProvider
import io.morgan.mio.core.database.model.StoredMediaFormat
import io.morgan.mio.core.database.model.StoredMediaPublicationStatus
import io.morgan.mio.core.database.model.StoredMediaType
import io.morgan.mio.core.database.model.StoredProgressUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class ProgressDaoTest {
    private lateinit var database: MioDatabase
    private val instant = Instant.parse("2026-08-23T12:00:00Z")

    @Before
    fun setUp() =
        runTest {
            database = MioDatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
            database.selectedAccountDao().replaceSelectedAccount(account(), mapping())
            database.mediaDao().upsertMedia(listOf(media()))
            database.collectionEntryDao().upsertEntries(listOf(entry()))
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun conditionalEffectiveUpdateComparesRevisionAndAllEffectiveFields() =
        runTest {
            val dao = database.progressDao()
            val original = requireNotNull(dao.getEntryForMedia(ACCOUNT_ID, MEDIA_ID))

            assertEquals(
                1,
                dao.updateEffectiveState(
                    entryId = ENTRY_ID,
                    accountId = ACCOUNT_ID,
                    mediaId = MEDIA_ID,
                    expectedRevision = 0,
                    expectedStatus = original.effectiveStatus,
                    expectedProgress = original.effectiveProgress,
                    expectedTotal = original.effectiveTotal,
                    expectedUpdatedAtUtcMs = original.effectiveUpdatedAtUtcMs,
                    targetStatus = StoredCollectionStatus.CURRENT,
                    targetProgress = 2,
                    targetUpdatedAtUtcMs = instant.plus(1.seconds),
                    targetRevision = 1,
                ),
            )
            assertEquals(
                0,
                dao.updateEffectiveState(
                    entryId = ENTRY_ID,
                    accountId = ACCOUNT_ID,
                    mediaId = MEDIA_ID,
                    expectedRevision = 0,
                    expectedStatus = StoredCollectionStatus.CURRENT,
                    expectedProgress = 1,
                    expectedTotal = 12,
                    expectedUpdatedAtUtcMs = instant,
                    targetStatus = StoredCollectionStatus.CURRENT,
                    targetProgress = 3,
                    targetUpdatedAtUtcMs = instant,
                    targetRevision = 2,
                ),
            )
            assertEquals(2, dao.getEntryForMedia(ACCOUNT_ID, MEDIA_ID)?.effectiveProgress)
            assertEquals(1L, dao.getEntryForMedia(ACCOUNT_ID, MEDIA_ID)?.localProgressRevision)
        }

    @Test
    fun activityOrderIsDatabaseOwnedAndActionIdIsUnique() =
        runTest {
            val first = database.progressDao().insertActivity(activity("group-1"))
            val second = database.progressDao().insertActivity(activity("group-2"))

            assertEquals(first + 1, second)
            assertEquals("group-2", database.progressDao().getLatestActivity(ACCOUNT_ID)?.operationGroupId)
            assertEquals(
                "group-2",
                database
                    .progressDao()
                    .observeLatestActivity(ACCOUNT_ID)
                    .first()
                    ?.operationGroupId,
            )
            try {
                database.progressDao().insertActivity(activity("group-1"))
                fail("Expected duplicate activity group to be rejected")
            } catch (_: SQLiteConstraintException) {
                // Expected unique action-id protection.
            }
        }

    @Test
    fun activityCascadesWithItsAccountAndNeverNeedsAnOutboxForeignKey() =
        runTest {
            database.progressDao().insertActivity(activity("group-cascade"))
            assertEquals(1, countRows("local_progress_activity"))
            database.selectedAccountDao().deleteAccount(ACCOUNT_ID)

            assertEquals(0, countRows("local_progress_activity"))
            assertNull(database.progressDao().getLatestActivity(ACCOUNT_ID))
        }

    private fun activity(groupId: String): LocalProgressActivityEntity =
        LocalProgressActivityEntity(
            operationGroupId = groupId,
            accountId = ACCOUNT_ID,
            collectionEntryId = ENTRY_ID,
            mediaId = MEDIA_ID,
            direction = "increase",
            progressUnit = StoredProgressUnit.EPISODE,
            beforeProgress = 1,
            afterProgress = 2,
            beforeStatus = StoredCollectionStatus.CURRENT,
            afterStatus = StoredCollectionStatus.CURRENT,
            beforeUpdatedAtUtcMs = instant,
            afterUpdatedAtUtcMs = instant,
            afterRevision = 1,
            createdAtUtcMs = instant,
            undoUntilUtcMs = instant.plus(6.seconds),
            undoneAtUtcMs = null,
        )

    private fun entry(): CollectionEntryEntity =
        CollectionEntryEntity(
            collectionEntryId = ENTRY_ID,
            accountId = ACCOUNT_ID,
            mediaId = MEDIA_ID,
            progressUnit = StoredProgressUnit.EPISODE,
            remoteStatus = StoredCollectionStatus.CURRENT,
            remoteProgress = 1,
            remoteTotal = 12,
            remoteUpdatedAtUtcMs = instant,
            effectiveStatus = StoredCollectionStatus.CURRENT,
            effectiveProgress = 1,
            effectiveTotal = 12,
            effectiveUpdatedAtUtcMs = instant,
        )

    private fun account() =
        SelectedAccountEntity(
            selectionKey = "selected",
            accountId = ACCOUNT_ID,
            connectedProvider = StoredConnectedProvider.ANILIST,
            displayName = "Acceptance account",
            avatarReference = null,
        )

    private fun mapping() =
        AccountProviderIdEntity(
            accountId = ACCOUNT_ID,
            provider = StoredConnectedProvider.ANILIST,
            remoteAccountId = "remote-$ACCOUNT_ID",
        )

    private fun media() =
        MediaEntity(
            mediaId = MEDIA_ID,
            mediaType = StoredMediaType.ANIME,
            title = "Acceptance title",
            format = StoredMediaFormat.TV,
            publicationStatus = StoredMediaPublicationStatus.RELEASING,
            synopsis = null,
            posterReference = null,
            bannerReference = null,
            episodeTotal = 12,
            chapterTotal = null,
            volumeTotal = null,
        )

    private fun countRows(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val ACCOUNT_ID = "account-progress"
        const val MEDIA_ID = "media-progress"
        const val ENTRY_ID = "entry-progress"
    }
}
