package com.pasich.mynotes.data.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.pasich.mynotes.data.database.dao.NoteDao;
import com.pasich.mynotes.data.database.dao.SyncConflictDao;
import com.pasich.mynotes.data.database.dao.SyncMetadataDao;
import com.pasich.mynotes.data.database.dao.SyncPendingPreferencesDao;
import com.pasich.mynotes.data.database.dao.SyncStateDao;
import com.pasich.mynotes.data.database.dao.TagsDao;
import com.pasich.mynotes.data.database.dao.TaskCategoryDao;
import com.pasich.mynotes.data.database.dao.TaskDao;
import com.pasich.mynotes.data.database.dao.Transactions;
import com.pasich.mynotes.data.database.entities.SyncConflictEntity;
import com.pasich.mynotes.data.database.entities.SyncMetadataEntity;
import com.pasich.mynotes.data.database.entities.SyncPendingPreferencesEntity;
import com.pasich.mynotes.data.database.entities.SyncStateEntity;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.data.preferences.SafePreferences;
import com.pasich.mynotes.utils.constants.DatabaseConstants;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import javax.inject.Singleton;

/** Room database definition with all migrations. */
@Database(
        version = DatabaseConstants.DB_VERSION,
        entities = {
            Tag.class,
            Note.class,
            Task.class,
            TaskCategory.class,
            SyncMetadataEntity.class,
            SyncPendingPreferencesEntity.class,
            SyncConflictEntity.class,
            SyncStateEntity.class
        },
        autoMigrations = {@AutoMigration(from = 1, to = 2)})
@Singleton
public abstract class AppDatabase extends RoomDatabase {

    /**
     * Adds a durable, cross-device identity mapping for existing records.
     *
     * <p>Existing Room IDs are intentionally preserved as local primary keys. Every note, task, and
     * tag instead receives an independent UUID and the same migration timestamp as its sync
     * baseline. There are no tombstones for records that existed before sync metadata was added.
     */
    public static final Migration MIGRATION_13_14 =
            new Migration(13, 14) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    long migrationTimestamp = System.currentTimeMillis();
                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS `sync_metadata` ("
                                    + "`recordType` TEXT NOT NULL, "
                                    + "`localId` INTEGER NOT NULL, "
                                    + "`stableId` TEXT NOT NULL, "
                                    + "`updatedAt` INTEGER NOT NULL, "
                                    + "`deletedAt` INTEGER, "
                                    + "PRIMARY KEY(`recordType`, `localId`))");
                    database.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_metadata_recordType_stableId` "
                                    + "ON `sync_metadata` (`recordType`, `stableId`)");
                    database.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_sync_metadata_updatedAt` "
                                    + "ON `sync_metadata` (`updatedAt`)");
                    database.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_sync_metadata_deletedAt` "
                                    + "ON `sync_metadata` (`deletedAt`)");

                    insertMetadataForExistingRecords(database, "note", "notes", migrationTimestamp);
                    insertMetadataForExistingRecords(database, "task", "tasks", migrationTimestamp);
                    insertMetadataForExistingRecords(database, "tag", "tags", migrationTimestamp);
                }
            };

    public static final Migration MIGRATION_14_15 =
            new Migration(14, 15) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    insertMetadataForExistingRecords(
                            database, "category", "task_categories", System.currentTimeMillis());
                }
            };

    public static final Migration MIGRATION_15_16 =
            new Migration(15, 16) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS `sync_conflicts` ("
                                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                                    + "`recordType` TEXT NOT NULL, "
                                    + "`stableId` TEXT NOT NULL, "
                                    + "`winnerSource` TEXT NOT NULL, "
                                    + "`winnerJson` TEXT NOT NULL, "
                                    + "`loserJson` TEXT NOT NULL, "
                                    + "`winnerUpdatedAt` INTEGER NOT NULL, "
                                    + "`loserUpdatedAt` INTEGER NOT NULL, "
                                    + "`winnerTombstone` INTEGER NOT NULL, "
                                    + "`loserTombstone` INTEGER NOT NULL, "
                                    + "`resolution` TEXT NOT NULL, "
                                    + "`resolved` INTEGER NOT NULL, "
                                    + "`createdAt` INTEGER NOT NULL, "
                                    + "`resolvedAt` INTEGER NOT NULL)");
                    database.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_conflicts_recordType_stableId` "
                                    + "ON `sync_conflicts` (`recordType`, `stableId`)");
                    database.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_sync_conflicts_resolved` "
                                    + "ON `sync_conflicts` (`resolved`)");
                    database.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_sync_conflicts_createdAt` "
                                    + "ON `sync_conflicts` (`createdAt`)");
                }
            };

    public static final Migration MIGRATION_16_17 =
            new Migration(16, 17) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS `sync_state` ("
                                    + "`id` INTEGER NOT NULL, "
                                    + "`status` TEXT NOT NULL, "
                                    + "`backendIdentifier` TEXT, "
                                    + "`lastSuccessfulSyncAt` INTEGER, "
                                    + "`attemptStartedAt` INTEGER, "
                                    + "`errorMessage` TEXT, "
                                    + "`conflictCount` INTEGER NOT NULL, "
                                    + "PRIMARY KEY(`id`))");
                }
            };

    /** Preserves every unresolved version pair instead of replacing conflicts by logical record. */
    public static final Migration MIGRATION_17_18 =
            new Migration(17, 18) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL(
                            "ALTER TABLE `sync_conflicts` ADD COLUMN `versionPairHash` TEXT NOT NULL DEFAULT ''");
                    // Version 17 could contain at most one row per logical record. Give each
                    // legacy row a durable unique identity without trying to hash untrusted JSON
                    // in SQLite during a migration.
                    database.execSQL(
                            "UPDATE `sync_conflicts` SET `versionPairHash` = 'legacy-' || `id` "
                                    + "WHERE `versionPairHash` = ''");
                    database.execSQL(
                            "DROP INDEX IF EXISTS `index_sync_conflicts_recordType_stableId`");
                    database.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS "
                                    + "`index_sync_conflicts_recordType_stableId_versionPairHash` "
                                    + "ON `sync_conflicts` (`recordType`, `stableId`, `versionPairHash`)");
                }
            };

    /** Adds the Room journal that bridges snapshot transactions to SharedPreferences. */
    public static final Migration MIGRATION_18_19 =
            new Migration(18, 19) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS `sync_pending_preferences` ("
                                    + "`id` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, "
                                    + "PRIMARY KEY(`id`))");
                }
            };

    /**
     * Gives the pending-preferences journal enough identity to decide whether replay is still valid
     * and a quarantine flag so an unreadable payload cannot disable sync forever, and gives each
     * conflict side its own origin plus a deterministic version identity.
     */
    public static final Migration MIGRATION_19_20 =
            new Migration(19, 20) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL(
                            "ALTER TABLE `sync_pending_preferences` "
                                    + "ADD COLUMN `targetHash` TEXT NOT NULL DEFAULT ''");
                    database.execSQL(
                            "ALTER TABLE `sync_pending_preferences` "
                                    + "ADD COLUMN `baselineHash` TEXT NOT NULL DEFAULT ''");
                    database.execSQL(
                            "ALTER TABLE `sync_pending_preferences` "
                                    + "ADD COLUMN `recordUpdatedAt` INTEGER NOT NULL DEFAULT 0");
                    database.execSQL(
                            "ALTER TABLE `sync_pending_preferences` "
                                    + "ADD COLUMN `quarantined` INTEGER NOT NULL DEFAULT 0");

                    // Conflict provenance is per side, and each version carries a deterministic
                    // identity, so a resolution can name a version instead of an endpoint.
                    database.execSQL(
                            "ALTER TABLE `sync_conflicts` "
                                    + "ADD COLUMN `loserSource` TEXT NOT NULL DEFAULT 'REMOTE'");
                    database.execSQL(
                            "ALTER TABLE `sync_conflicts` "
                                    + "ADD COLUMN `winnerVersionId` TEXT NOT NULL DEFAULT ''");
                    database.execSQL(
                            "ALTER TABLE `sync_conflicts` "
                                    + "ADD COLUMN `loserVersionId` TEXT NOT NULL DEFAULT ''");
                    // Rows written before this column existed always had a local winner or a
                    // local loser, never two remote sides.
                    database.execSQL(
                            "UPDATE `sync_conflicts` SET `loserSource` = "
                                    + "CASE WHEN `winnerSource` = 'LOCAL' THEN 'REMOTE' "
                                    + "ELSE 'LOCAL' END");
                }
            };

    private static void insertMetadataForExistingRecords(
            SupportSQLiteDatabase database,
            String recordType,
            String sourceTable,
            long migrationTimestamp) {
        database.execSQL(
                "INSERT OR IGNORE INTO `sync_metadata` "
                        + "(`recordType`, `localId`, `stableId`, `updatedAt`, `deletedAt`) "
                        + "SELECT '"
                        + recordType
                        + "', `id`, "
                        + sqliteUuidExpression()
                        + ", ?, NULL FROM `"
                        + sourceTable
                        + "`",
                new Object[] {migrationTimestamp});
    }

    /** Returns a lowercase RFC 4122 version-4 UUID expression supported by Android SQLite. */
    private static String sqliteUuidExpression() {
        return "lower(hex(randomblob(4))) || '-' || "
                + "lower(hex(randomblob(2))) || '-4' || "
                + "substr(lower(hex(randomblob(2))), 2, 3) || '-' || "
                + "substr('89ab', (random() & 3) + 1, 1) || "
                + "substr(lower(hex(randomblob(2))), 2, 3) || '-' || "
                + "lower(hex(randomblob(6)))";
    }

    /** Remove old system tags Add the position field to the tags table */
    public static final Migration MIGRATION_3_4 =
            new Migration(3, 4) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL(
                            "ALTER TABLE tags ADD COLUMN position INTEGER NOT NULL DEFAULT 0");
                }
            };

    /**
     * Додаємо колонку attachments json до таблиці notes [ { «url»:
     * «file:///data/user/0/.../attachments/file1.pdf», «name»: «file1.pdf», «extension»: «pdf»,
     * «size»: 12345 } ]
     */
    public static final Migration MIGRATION_5_6 =
            new Migration(5, 6) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL("ALTER TABLE notes ADD COLUMN attachments TEXT DEFAULT ''");
                }
            };

    // MIGRATION 6 → 7
    // -------------------------------
    // This migration completely removes the old `trash` table
    // and transfers its data to the main `notes` table.
    //
    // Main logic:
    // 1) A new column `isTrash` is added to NOTES (0 = normal note, 1 = in trash).
    //    The old trash table becomes unnecessary.
    //
    // 2) Old records from the `trash` table are migrated to `notes`:
    //      - title, value, date are transferred as is
    //      - tag is reset to empty (the old trash did not have tags)
    //      - valueJson and attachments are filled with empty values,
    //        because they did not exist in the old model
    //      - hasRichContent is set to 0 (the old format did not support rich)
    //      - isTrash = 1, so that these notes become ‘in the trash’
    //
    // 3) After successful migration, the `trash` table is deleted.
    //

    public static final Migration MIGRATION_6_7 =
            new Migration(6, 7) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase db) {
                    db.execSQL("ALTER TABLE notes ADD COLUMN isTrash INTEGER NOT NULL DEFAULT 0");
                    db.execSQL(
                            """
                        INSERT INTO notes (title, value, date, tag, valueJson, attachments, hasRichContent, isTrash)
                        SELECT title, value, date, '', '', '', 0, 1 FROM trash
                    """);
                    db.execSQL("DROP TABLE IF EXISTS trash");
                }
            };

    public static final Migration MIGRATION_7_8 =
            new Migration(7, 8) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase db) {
                    db.execSQL("ALTER TABLE notes ADD COLUMN reminderTime INTEGER DEFAULT NULL");
                    db.execSQL(
                            "ALTER TABLE notes ADD COLUMN reminderRepeat TEXT NOT NULL DEFAULT 'NONE'");
                }
            };

    public static final Migration MIGRATION_9_10 =
            new Migration(9, 10) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase db) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN description TEXT");
                }
            };

    public static final Migration MIGRATION_10_11 =
            new Migration(10, 11) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase db) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN reminderTime INTEGER DEFAULT NULL");
                }
            };

    public static final Migration MIGRATION_11_12 =
            new Migration(11, 12) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase db) {
                    db.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0");
                }
            };

    public static final Migration MIGRATION_12_13 =
            new Migration(12, 13) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase db) {
                    db.execSQL(
                            "ALTER TABLE notes ADD COLUMN reminderIntervalMinutes INTEGER NOT NULL DEFAULT 0");
                    db.execSQL(
                            "ALTER TABLE tasks ADD COLUMN reminderIntervalMinutes INTEGER NOT NULL DEFAULT 0");
                }
            };

    public static final Migration MIGRATION_8_9 =
            new Migration(8, 9) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase db) {
                    db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `tasks` ("
                                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                                    + "`title` TEXT NOT NULL, "
                                    + "`isDone` INTEGER NOT NULL DEFAULT 0, "
                                    + "`categoryId` INTEGER NOT NULL DEFAULT 0, "
                                    + "`createdAt` INTEGER NOT NULL DEFAULT 0, "
                                    + "`position` INTEGER NOT NULL DEFAULT 0)");
                    db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `task_categories` ("
                                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                                    + "`name` TEXT NOT NULL, "
                                    + "`colorHex` TEXT NOT NULL DEFAULT '#6750A4', "
                                    + "`position` INTEGER NOT NULL DEFAULT 0)");
                }
            };
    private static Context appContext;

    /** Remove old system tags Set lastKnownVersion = “2.1.29” for migration */
    public static final Migration MIGRATION_2_3 =
            new Migration(2, 3) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL(
                            "DELETE FROM tags WHERE name = '' AND visibility = 0 AND systemAction = 1");
                    database.execSQL(
                            "DELETE FROM tags WHERE name = 'allNotes' AND visibility = 0 AND systemAction = 2");

                    if (appContext != null) {
                        prefs().putString(
                                        PreferencesConfig.ARGUMENT_PREFERENCE_LAST_KNOWN_VERSION,
                                        "2.1.29");
                    }
                }
            };

    /**
     * Add the valueJson column (nullable) to the notes table. Add the hasRichContent column
     * (boolean, NOT NULL, default false). Set extendedEditorEnable = false for existing users.
     */
    public static final Migration MIGRATION_4_5 =
            new Migration(4, 5) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL("ALTER TABLE notes ADD COLUMN valueJson TEXT");
                    database.execSQL(
                            "ALTER TABLE notes ADD COLUMN hasRichContent INTEGER NOT NULL DEFAULT 0");

                    if (appContext != null) {
                        prefs().putBoolean(
                                        PreferencesConfig.ARGUMENT_PREFERENCE_EXTENDED_EDITOR,
                                        false);
                    }
                }
            };

    private static SafePreferences prefs() {
        return new SafePreferences(appContext);
    }

    public static void setContext(Context context) {
        appContext = context.getApplicationContext();
    }

    public abstract TagsDao tagsDao();

    public abstract NoteDao noteDao();

    public abstract Transactions transactionsNote();

    public abstract TaskDao taskDao();

    public abstract TaskCategoryDao taskCategoryDao();

    public abstract SyncMetadataDao syncMetadataDao();

    public abstract SyncConflictDao syncConflictDao();

    public abstract SyncStateDao syncStateDao();

    public abstract SyncPendingPreferencesDao syncPendingPreferencesDao();
}
