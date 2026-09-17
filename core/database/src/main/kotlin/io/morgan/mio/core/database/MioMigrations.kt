package io.morgan.mio.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

public object MioMigrations {
    public val MIGRATION_1_2: Migration =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_operation_group` (
                        `group_order` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `operation_group_id` TEXT NOT NULL,
                        `account_id` TEXT NOT NULL,
                        `created_at_utc_ms` INTEGER NOT NULL,
                        `compensates_group_id` TEXT,
                        FOREIGN KEY(`account_id`) REFERENCES `selected_account`(`account_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_operation_group_operation_group_id` " +
                        "ON `pending_operation_group` (`operation_group_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_operation_group_account_id` " +
                        "ON `pending_operation_group` (`account_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_operation_group_compensates_group_id` " +
                        "ON `pending_operation_group` (`compensates_group_id`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_operation` (
                        `operation_id` TEXT NOT NULL,
                        `operation_group_id` TEXT NOT NULL,
                        `account_id` TEXT NOT NULL,
                        `collection_entry_id` TEXT NOT NULL,
                        `position_in_group` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `base_progress` INTEGER,
                        `target_progress` INTEGER,
                        `base_status` TEXT,
                        `target_status` TEXT,
                        `lifecycle_state` TEXT NOT NULL,
                        `attempt_count` INTEGER NOT NULL,
                        `last_attempt_at_utc_ms` INTEGER,
                        `retry_not_before_utc_ms` INTEGER,
                        PRIMARY KEY(`operation_id`),
                        FOREIGN KEY(`operation_group_id`) REFERENCES `pending_operation_group`(`operation_group_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`account_id`) REFERENCES `selected_account`(`account_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`collection_entry_id`) REFERENCES `collection_entry`(`collection_entry_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_operation_operation_group_id` " +
                        "ON `pending_operation` (`operation_group_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_operation_account_id` " +
                        "ON `pending_operation` (`account_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_operation_collection_entry_id` " +
                        "ON `pending_operation` (`collection_entry_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_operation_lifecycle_state` " +
                        "ON `pending_operation` (`lifecycle_state`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_operation_operation_group_id_" +
                        "position_in_group` " +
                        "ON `pending_operation` (`operation_group_id`, `position_in_group`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_operation_dependency` (
                        `operation_id` TEXT NOT NULL,
                        `depends_on_operation_id` TEXT NOT NULL,
                        PRIMARY KEY(`operation_id`, `depends_on_operation_id`),
                        FOREIGN KEY(`operation_id`) REFERENCES `pending_operation`(`operation_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`depends_on_operation_id`) REFERENCES `pending_operation`(`operation_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_operation_dependency_depends_on_operation_id` " +
                        "ON `pending_operation_dependency` (`depends_on_operation_id`)",
                )
            }
        }

    public val MIGRATION_2_3: Migration =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media` ADD COLUMN `release_year` INTEGER")
                db.execSQL("ALTER TABLE `media` ADD COLUMN `score_percent` INTEGER")
            }
        }

    public val MIGRATION_3_4: Migration =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `collection_entry` ADD COLUMN `local_progress_revision` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_progress_activity` (
                        `activity_order` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `operation_group_id` TEXT NOT NULL,
                        `account_id` TEXT NOT NULL,
                        `collection_entry_id` TEXT NOT NULL,
                        `media_id` TEXT NOT NULL,
                        `direction` TEXT NOT NULL,
                        `progress_unit` TEXT NOT NULL,
                        `before_progress` INTEGER NOT NULL,
                        `after_progress` INTEGER NOT NULL,
                        `before_status` TEXT NOT NULL,
                        `after_status` TEXT NOT NULL,
                        `before_updated_at_utc_ms` INTEGER NOT NULL,
                        `after_updated_at_utc_ms` INTEGER NOT NULL,
                        `after_revision` INTEGER NOT NULL,
                        `created_at_utc_ms` INTEGER NOT NULL,
                        `undo_until_utc_ms` INTEGER NOT NULL,
                        `undone_at_utc_ms` INTEGER,
                        FOREIGN KEY(`account_id`) REFERENCES `selected_account`(`account_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`collection_entry_id`) REFERENCES `collection_entry`(`collection_entry_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`media_id`) REFERENCES `media`(`media_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_local_progress_activity_operation_group_id` " +
                        "ON `local_progress_activity` (`operation_group_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_progress_activity_account_id_activity_order` " +
                        "ON `local_progress_activity` (`account_id`, `activity_order`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_progress_activity_collection_entry_id` " +
                        "ON `local_progress_activity` (`collection_entry_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_progress_activity_media_id` " +
                        "ON `local_progress_activity` (`media_id`)",
                )
            }
        }

    public val MIGRATION_4_5: Migration =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_lease` (
                        `account_id` TEXT NOT NULL,
                        `owner_token` TEXT NOT NULL,
                        `expires_at_utc_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`account_id`),
                        FOREIGN KEY(`account_id`) REFERENCES `selected_account`(`account_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_schedule` (
                        `account_id` TEXT NOT NULL,
                        `requested_generation` INTEGER NOT NULL,
                        `dispatched_generation` INTEGER NOT NULL,
                        PRIMARY KEY(`account_id`),
                        FOREIGN KEY(`account_id`) REFERENCES `selected_account`(`account_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_issue` (
                        `issue_id` TEXT NOT NULL,
                        `account_id` TEXT NOT NULL,
                        `collection_entry_id` TEXT NOT NULL,
                        `operation_group_id` TEXT NOT NULL,
                        `operation_id` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `base_progress` INTEGER,
                        `base_status` TEXT,
                        `target_progress` INTEGER,
                        `target_status` TEXT,
                        `progress_unit` TEXT NOT NULL,
                        `local_progress` INTEGER NOT NULL,
                        `local_status` TEXT NOT NULL,
                        `local_total` INTEGER,
                        `local_updated_at_utc_ms` INTEGER NOT NULL,
                        `local_revision` INTEGER NOT NULL,
                        `remote_progress` INTEGER,
                        `remote_status` TEXT,
                        `remote_total` INTEGER,
                        `remote_updated_at_utc_ms` INTEGER,
                        `detected_at_utc_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`issue_id`),
                        FOREIGN KEY(`account_id`) REFERENCES `selected_account`(`account_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`collection_entry_id`) REFERENCES `collection_entry`(`collection_entry_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sync_issue_account_id` ON `sync_issue` (`account_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sync_issue_collection_entry_id` " +
                        "ON `sync_issue` (`collection_entry_id`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_issue_account_id_operation_id` " +
                        "ON `sync_issue` (`account_id`, `operation_id`)",
                )
                db.execSQL(
                    """
                    INSERT INTO `sync_schedule` (`account_id`, `requested_generation`, `dispatched_generation`)
                    SELECT DISTINCT `account_id`, 1, 0
                    FROM `pending_operation_group`
                    """.trimIndent(),
                )
            }
        }

    public val MIGRATION_5_6: Migration =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_account_issue` (
                        `issue_id` TEXT NOT NULL,
                        `account_id` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `detected_at_utc_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`issue_id`),
                        FOREIGN KEY(`account_id`) REFERENCES `selected_account`(`account_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_account_issue_account_id` " +
                        "ON `sync_account_issue` (`account_id`)",
                )
            }
        }

    public val ALL: Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
