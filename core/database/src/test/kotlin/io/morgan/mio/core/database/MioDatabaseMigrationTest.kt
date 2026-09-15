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
        context.deleteDatabase(TEST_DATABASE_V2_NAME)
        context.deleteDatabase(TEST_DATABASE_V3_NAME)
        context.deleteDatabase(TEST_DATABASE_V4_NAME)
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
            .runMigrationsAndValidate(TEST_DATABASE_NAME, 5, true, *MioMigrations.ALL)
            .use { migrated ->
                assertEquals(1, countRows(migrated, "selected_account"))
                assertEquals(1, countRows(migrated, "media"))
                assertEquals(1, countRows(migrated, "collection_entry"))
                assertEquals(0, countRows(migrated, "pending_operation_group"))
                assertEquals(0, countRows(migrated, "pending_operation"))
                assertEquals(0, countRows(migrated, "pending_operation_dependency"))
                assertEquals(0, countRows(migrated, "local_progress_activity"))
                assertEquals(
                    0L,
                    migrated
                        .query(
                            "SELECT local_progress_revision FROM collection_entry WHERE collection_entry_id = 'entry-migration'",
                        ).use { cursor ->
                            cursor.moveToFirst()
                            cursor.getLong(0)
                        },
                )
                assertTrue(hasColumn(migrated, "media", "release_year"))
                assertTrue(hasColumn(migrated, "media", "score_percent"))
            }

        val current = MioDatabaseFactory.create(context, TEST_DATABASE_NAME)
        assertNotNull(current.selectedAccountDao())
        assertNotNull(current.mediaDao())
        assertNotNull(current.releaseDao())
        assertNotNull(current.collectionEntryDao())
        assertNotNull(current.providerIdDao())
        assertNotNull(current.pendingOperationDao())
        assertNotNull(current.progressDao())
        assertNotNull(current.syncDao())
        current.close()

        assertEquals(5, MioDatabase.VERSION)
        assertEquals(4, MioMigrations.ALL.size)
        assertTrue(MioMigrations.ALL[0] === MioMigrations.MIGRATION_1_2)
        assertTrue(MioMigrations.ALL[1] === MioMigrations.MIGRATION_2_3)
        assertTrue(MioMigrations.ALL[2] === MioMigrations.MIGRATION_3_4)
        assertTrue(MioMigrations.ALL[3] === MioMigrations.MIGRATION_4_5)
    }

    @Test
    fun versionTwoRowsAndRelationsSurviveTheMetadataMigration() {
        helper.createDatabase(TEST_DATABASE_V2_NAME, 1).use { database ->
            database.execSQL(
                "INSERT INTO selected_account " +
                    "(selection_key, account_id, connected_provider, display_name, avatar_reference) " +
                    "VALUES ('selected', 'account-v2', 'anilist', 'Migration account', NULL)",
            )
            database.execSQL(
                "INSERT INTO media " +
                    "(media_id, media_type, title, format, publication_status, synopsis, poster_reference, " +
                    "banner_reference, episode_total, chapter_total, volume_total) " +
                    "VALUES ('media-v2', 'anime', 'Migration media', 'tv', 'releasing', NULL, NULL, NULL, 12, NULL, NULL)",
            )
            database.execSQL(
                "INSERT INTO media_provider_id (provider, remote_media_id, media_id) " +
                    "VALUES ('anilist', 'v2-remote', 'media-v2')",
            )
        }
        helper.runMigrationsAndValidate(TEST_DATABASE_V2_NAME, 2, true, MioMigrations.MIGRATION_1_2).close()

        helper
            .runMigrationsAndValidate(TEST_DATABASE_V2_NAME, 3, true, MioMigrations.MIGRATION_2_3)
            .use { migrated ->
                assertEquals(1, countRows(migrated, "media"))
                assertEquals(1, countRows(migrated, "media_provider_id"))
                migrated
                    .query("SELECT release_year, score_percent FROM media WHERE media_id = 'media-v2'")
                    .use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertTrue(cursor.isNull(0))
                        assertTrue(cursor.isNull(1))
                    }
            }
    }

    @Test
    fun versionThreeRowsSurviveProgressActivityMigrationWithZeroLocalState() {
        helper.createDatabase(TEST_DATABASE_V3_NAME, 3).use { database ->
            database.execSQL(
                "INSERT INTO selected_account " +
                    "(selection_key, account_id, connected_provider, display_name, avatar_reference) " +
                    "VALUES ('selected', 'account-v3', 'anilist', 'Migration account', NULL)",
            )
            database.execSQL(
                "INSERT INTO media " +
                    "(media_id, media_type, title, format, publication_status, synopsis, poster_reference, " +
                    "banner_reference, episode_total, chapter_total, volume_total, release_year, score_percent) " +
                    "VALUES ('media-v3', 'anime', 'Migration media', 'tv', 'releasing', NULL, NULL, NULL, " +
                    "12, NULL, NULL, 2026, 91)",
            )
            database.execSQL(
                "INSERT INTO collection_entry " +
                    "(collection_entry_id, account_id, media_id, progress_unit, remote_status, remote_progress, " +
                    "remote_total, remote_updated_at_utc_ms, effective_status, effective_progress, effective_total, " +
                    "effective_updated_at_utc_ms) VALUES ('entry-v3', 'account-v3', 'media-v3', " +
                    "'episode', 'current', 4, 12, 1767225600000, 'current', 5, 12, 1767225601000)",
            )
        }

        helper
            .runMigrationsAndValidate(TEST_DATABASE_V3_NAME, 4, true, MioMigrations.MIGRATION_3_4)
            .use { migrated ->
                assertEquals(1, countRows(migrated, "media"))
                assertEquals(1, countRows(migrated, "collection_entry"))
                assertEquals(0, countRows(migrated, "local_progress_activity"))
                migrated
                    .query(
                        "SELECT release_year, score_percent, remote_progress, effective_progress, " +
                            "local_progress_revision FROM media " +
                            "INNER JOIN collection_entry ON collection_entry.media_id = media.media_id",
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(2026, cursor.getInt(0))
                        assertEquals(91, cursor.getInt(1))
                        assertEquals(4, cursor.getInt(2))
                        assertEquals(5, cursor.getInt(3))
                        assertEquals(0, cursor.getLong(4))
                    }
            }
    }

    @Test
    fun versionFourPendingGroupsRecoverAnUndispatchedSynchronizationGeneration() {
        helper.createDatabase(TEST_DATABASE_V4_NAME, 4).use { database ->
            database.execSQL(
                "INSERT INTO selected_account " +
                    "(selection_key, account_id, connected_provider, display_name, avatar_reference) " +
                    "VALUES ('selected', 'account-v4', 'anilist', 'Migration account', NULL)",
            )
            database.execSQL(
                "INSERT INTO pending_operation_group " +
                    "(operation_group_id, account_id, created_at_utc_ms, compensates_group_id) " +
                    "VALUES ('group-v4', 'account-v4', 1767225600000, NULL)",
            )
        }

        helper
            .runMigrationsAndValidate(TEST_DATABASE_V4_NAME, 5, true, MioMigrations.MIGRATION_4_5)
            .use { migrated ->
                migrated
                    .query(
                        "SELECT requested_generation, dispatched_generation FROM sync_schedule " +
                            "WHERE account_id = 'account-v4'",
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(1L, cursor.getLong(0))
                        assertEquals(0L, cursor.getLong(1))
                    }
            }
    }

    private fun countRows(
        database: SupportSQLiteDatabase,
        tableName: String,
    ): Int =
        database.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun hasColumn(
        database: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
    ): Boolean =
        database.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            generateSequence {
                if (cursor.moveToNext()) cursor.getString(nameIndex) else null
            }.any { name -> name == columnName }
        }

    private companion object {
        const val TEST_DATABASE_NAME: String = "mio-migration-test.db"
        const val TEST_DATABASE_V2_NAME: String = "mio-migration-v2-test.db"
        const val TEST_DATABASE_V3_NAME: String = "mio-migration-v3-test.db"
        const val TEST_DATABASE_V4_NAME: String = "mio-migration-v4-test.db"
    }
}
