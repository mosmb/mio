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

    public val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
