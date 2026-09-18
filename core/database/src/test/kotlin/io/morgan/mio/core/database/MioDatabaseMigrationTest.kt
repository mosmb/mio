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
        context.deleteDatabase(TEST_DATABASE_V5_NAME)
        context.deleteDatabase(TEST_DATABASE_V6_NAME)
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
            .runMigrationsAndValidate(TEST_DATABASE_NAME, 7, true, *MioMigrations.ALL)
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
        assertNotNull(current.mediaSearchDao())
        assertNotNull(current.releaseDao())
        assertNotNull(current.collectionEntryDao())
        assertNotNull(current.providerIdDao())
        assertNotNull(current.pendingOperationDao())
        assertNotNull(current.progressDao())
        assertNotNull(current.syncDao())
        current.close()

        assertEquals(7, MioDatabase.VERSION)
        assertEquals(6, MioMigrations.ALL.size)
        assertTrue(MioMigrations.ALL[0] === MioMigrations.MIGRATION_1_2)
        assertTrue(MioMigrations.ALL[1] === MioMigrations.MIGRATION_2_3)
        assertTrue(MioMigrations.ALL[2] === MioMigrations.MIGRATION_3_4)
        assertTrue(MioMigrations.ALL[3] === MioMigrations.MIGRATION_4_5)
        assertTrue(MioMigrations.ALL[4] === MioMigrations.MIGRATION_5_6)
        assertTrue(MioMigrations.ALL[5] === MioMigrations.MIGRATION_6_7)
    }

    @Test
    fun versionSixMediaCollectionAndOutboxSurviveSearchCacheMigration() {
        helper.createDatabase(TEST_DATABASE_V6_NAME, 6).use { database ->
            database.execSQL(
                "INSERT INTO selected_account " +
                    "(selection_key, account_id, connected_provider, display_name, avatar_reference) " +
                    "VALUES ('selected', 'account-v6', 'anilist', 'Migration account', NULL)",
            )
            database.execSQL(
                "INSERT INTO media " +
                    "(media_id, media_type, title, format, publication_status, synopsis, poster_reference, " +
                    "banner_reference, episode_total, chapter_total, volume_total, release_year, score_percent) " +
                    "VALUES ('media-v6', 'anime', 'Migration media', 'tv', 'releasing', 'Full detail', " +
                    "'https://image.invalid/poster', NULL, 12, NULL, NULL, 2024, 91)",
            )
            database.execSQL(
                "INSERT INTO collection_entry " +
                    "(collection_entry_id, account_id, media_id, progress_unit, remote_status, remote_progress, " +
                    "remote_total, remote_updated_at_utc_ms, effective_status, effective_progress, effective_total, " +
                    "effective_updated_at_utc_ms, local_progress_revision) " +
                    "VALUES ('entry-v6', 'account-v6', 'media-v6', 'episode', 'current', 3, 12, " +
                    "1767225600000, 'current', 4, 12, 1767225601000, 2)",
            )
            database.execSQL(
                "INSERT INTO pending_operation_group " +
                    "(operation_group_id, account_id, created_at_utc_ms, compensates_group_id) " +
                    "VALUES ('group-v6', 'account-v6', 1767225602000, NULL)",
            )
            database.execSQL(
                "INSERT INTO pending_operation " +
                    "(operation_id, operation_group_id, account_id, collection_entry_id, position_in_group, kind, " +
                    "base_progress, target_progress, base_status, target_status, lifecycle_state, attempt_count, " +
                    "last_attempt_at_utc_ms, retry_not_before_utc_ms) " +
                    "VALUES ('operation-v6', 'group-v6', 'account-v6', 'entry-v6', 0, 'set_progress_forward', " +
                    "3, 4, NULL, NULL, 'enqueued', 0, NULL, NULL)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DATABASE_V6_NAME, 7, true, MioMigrations.MIGRATION_6_7).use { migrated ->
            assertEquals(1, countRows(migrated, "selected_account"))
            assertEquals(1, countRows(migrated, "media"))
            assertEquals(1, countRows(migrated, "collection_entry"))
            assertEquals(1, countRows(migrated, "pending_operation_group"))
            assertEquals(1, countRows(migrated, "pending_operation"))
            assertEquals(0, countRows(migrated, "media_search_query"))
            assertEquals(0, countRows(migrated, "media_search_result"))
            migrated
                .query(
                    "SELECT title, synopsis, score_percent FROM media WHERE media_id = 'media-v6'",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Migration media", cursor.getString(0))
                    assertEquals("Full detail", cursor.getString(1))
                    assertEquals(91, cursor.getInt(2))
                }
            migrated
                .query(
                    "SELECT effective_progress, local_progress_revision FROM collection_entry " +
                        "WHERE collection_entry_id = 'entry-v6'",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(4, cursor.getInt(0))
                    assertEquals(2L, cursor.getLong(1))
                }
            migrated
                .query("SELECT operation_id FROM pending_operation WHERE operation_id = 'operation-v6'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("operation-v6", cursor.getString(0))
                }
        }
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

    @Test
    fun versionFiveSyncStateSurvivesAccountIssueMigration() {
        helper.createDatabase(TEST_DATABASE_V5_NAME, 5).use { database ->
            database.execSQL(
                "INSERT INTO selected_account " +
                    "(selection_key, account_id, connected_provider, display_name, avatar_reference) " +
                    "VALUES ('selected', 'account-v5', 'anilist', 'Migration account', NULL)",
            )
            database.execSQL(
                "INSERT INTO account_provider_id (account_id, provider, remote_account_id) " +
                    "VALUES ('account-v5', 'anilist', 'remote-42')",
            )
            database.execSQL(
                "INSERT INTO media " +
                    "(media_id, media_type, title, format, publication_status, synopsis, poster_reference, " +
                    "banner_reference, episode_total, chapter_total, volume_total, release_year, score_percent) " +
                    "VALUES ('media-v5', 'anime', 'Migration media', 'tv', 'releasing', NULL, NULL, NULL, " +
                    "12, NULL, NULL, 2024, 87)",
            )
            database.execSQL(
                "INSERT INTO media_provider_id (provider, remote_media_id, media_id) " +
                    "VALUES ('anilist', 'media-101', 'media-v5')",
            )
            database.execSQL(
                "INSERT INTO collection_entry " +
                    "(collection_entry_id, account_id, media_id, progress_unit, remote_status, remote_progress, " +
                    "remote_total, remote_updated_at_utc_ms, effective_status, effective_progress, effective_total, " +
                    "effective_updated_at_utc_ms, local_progress_revision) VALUES " +
                    "('entry-v5', 'account-v5', 'media-v5', 'episode', 'current', 2, 12, 1767225600000, " +
                    "'current', 3, 12, 1767225601000, 1)",
            )
            database.execSQL(
                "INSERT INTO collection_entry_provider_id " +
                    "(provider, remote_collection_entry_id, collection_entry_id) " +
                    "VALUES ('anilist', 'entry-201', 'entry-v5')",
            )
            database.execSQL(
                "INSERT INTO pending_operation_group " +
                    "(group_order, operation_group_id, account_id, created_at_utc_ms, compensates_group_id) VALUES " +
                    "(11, 'group-v5-base', 'account-v5', 1767225600000, NULL), " +
                    "(12, 'group-v5-correction', 'account-v5', 1767225601000, NULL)",
            )
            database.execSQL(
                "INSERT INTO pending_operation " +
                    "(operation_id, operation_group_id, account_id, collection_entry_id, position_in_group, kind, " +
                    "base_progress, target_progress, base_status, target_status, lifecycle_state, attempt_count, " +
                    "last_attempt_at_utc_ms, retry_not_before_utc_ms) VALUES " +
                    "('operation-v5-base', 'group-v5-base', 'account-v5', 'entry-v5', 0, " +
                    "'set_progress_forward', 1, 2, NULL, NULL, 'enqueued', 0, NULL, NULL), " +
                    "('operation-v5-correction', 'group-v5-correction', 'account-v5', 'entry-v5', 0, " +
                    "'set_progress_correction', 2, 3, NULL, NULL, 'terminal_failure', 2, 1767225602000, NULL)",
            )
            database.execSQL(
                "INSERT INTO pending_operation_dependency (operation_id, depends_on_operation_id) " +
                    "VALUES ('operation-v5-correction', 'operation-v5-base')",
            )
            database.execSQL(
                "INSERT INTO local_progress_activity " +
                    "(operation_group_id, account_id, collection_entry_id, media_id, direction, " +
                    "progress_unit, before_progress, after_progress, before_status, after_status, " +
                    "before_updated_at_utc_ms, " +
                    "after_updated_at_utc_ms, after_revision, " +
                    "created_at_utc_ms, undo_until_utc_ms, undone_at_utc_ms) " +
                    "VALUES ('group-v5-correction', 'account-v5', 'entry-v5', 'media-v5', 'increase', 'episode', " +
                    "2, 3, 'current', 'current', 1767225600000, 1767225601000, 1, 1767225601000, " +
                    "1767225607000, NULL)",
            )
            database.execSQL(
                "INSERT INTO sync_lease (account_id, owner_token, expires_at_utc_ms) " +
                    "VALUES ('account-v5', 'owner-v5', 1767225720000)",
            )
            database.execSQL(
                "INSERT INTO sync_schedule (account_id, requested_generation, dispatched_generation) " +
                    "VALUES ('account-v5', 7, 6)",
            )
            database.execSQL(
                "INSERT INTO sync_issue " +
                    "(issue_id, account_id, collection_entry_id, operation_group_id, operation_id, kind, " +
                    "base_progress, base_status, target_progress, target_status, progress_unit, local_progress, " +
                    "local_status, local_total, local_updated_at_utc_ms, local_revision, remote_progress, " +
                    "remote_status, remote_total, remote_updated_at_utc_ms, detected_at_utc_ms) VALUES " +
                    "('issue-v5', 'account-v5', 'entry-v5', 'group-v5-correction', 'operation-v5-correction', " +
                    "'rejected', 2, NULL, 3, NULL, 'episode', 3, 'current', 12, 1767225601000, 1, " +
                    "2, 'current', 12, 1767225602000, 1767225603000)",
            )
        }

        helper
            .runMigrationsAndValidate(TEST_DATABASE_V5_NAME, 6, true, MioMigrations.MIGRATION_5_6)
            .use { migrated ->
                assertEquals(1, countRows(migrated, "selected_account"))
                assertEquals(1, countRows(migrated, "account_provider_id"))
                assertEquals(1, countRows(migrated, "media_provider_id"))
                assertEquals(1, countRows(migrated, "collection_entry_provider_id"))
                assertEquals(1, countRows(migrated, "collection_entry"))
                assertEquals(2, countRows(migrated, "pending_operation_group"))
                assertEquals(2, countRows(migrated, "pending_operation"))
                assertEquals(1, countRows(migrated, "pending_operation_dependency"))
                assertEquals(1, countRows(migrated, "local_progress_activity"))
                assertEquals(1, countRows(migrated, "sync_lease"))
                assertEquals(1, countRows(migrated, "sync_schedule"))
                assertEquals(1, countRows(migrated, "sync_issue"))
                assertEquals(0, countRows(migrated, "sync_account_issue"))
                migrated
                    .query(
                        "SELECT progress_unit, remote_progress, effective_progress, local_progress_revision " +
                            "FROM collection_entry WHERE collection_entry_id = 'entry-v5'",
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("episode", cursor.getString(0))
                        assertEquals(2, cursor.getInt(1))
                        assertEquals(3, cursor.getInt(2))
                        assertEquals(1L, cursor.getLong(3))
                    }
                migrated
                    .query(
                        "SELECT operation_id, lifecycle_state, attempt_count FROM pending_operation " +
                            "WHERE operation_id = 'operation-v5-correction'",
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("operation-v5-correction", cursor.getString(0))
                        assertEquals("terminal_failure", cursor.getString(1))
                        assertEquals(2, cursor.getInt(2))
                    }
                migrated
                    .query(
                        "SELECT operation_id, depends_on_operation_id FROM pending_operation_dependency",
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("operation-v5-correction", cursor.getString(0))
                        assertEquals("operation-v5-base", cursor.getString(1))
                    }
                migrated
                    .query("SELECT owner_token, expires_at_utc_ms FROM sync_lease WHERE account_id = 'account-v5'")
                    .use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("owner-v5", cursor.getString(0))
                        assertEquals(1767225720000L, cursor.getLong(1))
                    }
                migrated
                    .query("SELECT requested_generation, dispatched_generation FROM sync_schedule")
                    .use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(7L, cursor.getLong(0))
                        assertEquals(6L, cursor.getLong(1))
                    }
                migrated
                    .query("SELECT issue_id, operation_id, kind, local_revision FROM sync_issue")
                    .use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("issue-v5", cursor.getString(0))
                        assertEquals("operation-v5-correction", cursor.getString(1))
                        assertEquals("rejected", cursor.getString(2))
                        assertEquals(1L, cursor.getLong(3))
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
        const val TEST_DATABASE_V5_NAME: String = "mio-migration-v5-test.db"
        const val TEST_DATABASE_V6_NAME: String = "mio-migration-v6-test.db"
    }
}
