package io.morgan.mio.core.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class MioDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MioDatabase::class.java,
            emptyList(),
        )

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun versionOneRowsSurviveTheExplicitOutboxMigration() {
        helper.createDatabase(TEST_DATABASE_NAME, 1).use { database ->
            database.execSQL(
                "INSERT INTO selected_account " +
                    "(selection_key, account_id, connected_provider, display_name, avatar_reference) " +
                    "VALUES ('selected', 'account-migration', 'anilist', 'Migration account', NULL)",
            )
            database.execSQL(
                "INSERT INTO media " +
                    "(media_id, media_type, title, format, publication_status, synopsis, poster_reference, " +
                    "banner_reference, episode_total, chapter_total, volume_total) " +
                    "VALUES ('media-migration', 'anime', 'Migration media', 'tv', 'releasing', NULL, NULL, NULL, 12, NULL, NULL)",
            )
            database.execSQL(
                "INSERT INTO collection_entry " +
                    "(collection_entry_id, account_id, media_id, progress_unit, remote_status, remote_progress, " +
                    "remote_total, remote_updated_at_utc_ms, effective_status, effective_progress, effective_total, " +
                    "effective_updated_at_utc_ms) VALUES ('entry-migration', 'account-migration', 'media-migration', " +
                    "'episode', 'current', 1, 12, 1767225600000, 'current', 1, 12, 1767225600000)",
            )
        }

        helper
            .runMigrationsAndValidate(TEST_DATABASE_NAME, 2, true, MioMigrations.MIGRATION_1_2)
            .use { migrated ->
                assertEquals(1, countRows(migrated, "selected_account"))
                assertEquals(1, countRows(migrated, "media"))
                assertEquals(1, countRows(migrated, "collection_entry"))
                assertEquals(0, countRows(migrated, "pending_operation_group"))
                assertEquals(0, countRows(migrated, "pending_operation"))
                assertEquals(0, countRows(migrated, "pending_operation_dependency"))
            }

        val current = MioDatabaseFactory.create(context, TEST_DATABASE_NAME)
        assertNotNull(current.selectedAccountDao())
        assertNotNull(current.mediaDao())
        assertNotNull(current.releaseDao())
        assertNotNull(current.collectionEntryDao())
        assertNotNull(current.providerIdDao())
        assertNotNull(current.pendingOperationDao())
        current.close()

        assertEquals(2, MioDatabase.VERSION)
        assertEquals(1, MioMigrations.ALL.size)
        assertTrue(MioMigrations.ALL.single() === MioMigrations.MIGRATION_1_2)
    }

    private fun countRows(
        database: SupportSQLiteDatabase,
        tableName: String,
    ): Int =
        database.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DATABASE_NAME: String = "mio-migration-test.db"
    }
}
