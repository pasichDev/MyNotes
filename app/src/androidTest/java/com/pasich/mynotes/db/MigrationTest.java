package com.pasich.mynotes.db;

import static com.google.common.truth.Truth.assertThat;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.pasich.mynotes.data.database.AppDatabase;
import java.io.IOException;
import java.util.regex.Pattern;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MigrationTest {

    private static final String TEST_DB = "migration-test";

    @Rule
    public final MigrationTestHelper helper =
            new MigrationTestHelper(
                    InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    @Test
    public void migrate13to14_backfillsStableIdsAndTimestamps() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 13);
        db.execSQL(
                "INSERT INTO notes "
                        + "(id, title, value, date, tag, valueJson, hasRichContent, attachments, isTrash, "
                        + "reminderTime, isPinned, reminderRepeat, reminderIntervalMinutes) "
                        + "VALUES (101, 'Note', 'Body', 10, '', '', 0, '', 0, NULL, 0, 'NONE', 0)");
        db.execSQL(
                "INSERT INTO tasks "
                        + "(id, title, description, isDone, categoryId, createdAt, position, reminderTime, "
                        + "reminderIntervalMinutes) "
                        + "VALUES (102, 'Task', NULL, 0, 0, 10, 0, NULL, 0)");
        db.execSQL(
                "INSERT INTO tags (id, name, visibility, systemAction, position) "
                        + "VALUES (103, 'Tag', 0, 0, 0)");
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(TEST_DB, 14, true, AppDatabase.MIGRATION_13_14);
        try (android.database.Cursor cursor =
                migrated.query(
                        "SELECT recordType, localId, stableId, updatedAt, deletedAt "
                                + "FROM sync_metadata ORDER BY recordType")) {
            assertThat(cursor.getCount()).isEqualTo(3);
            Pattern uuid =
                    Pattern.compile(
                            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
            while (cursor.moveToNext()) {
                assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("localId"))).isAtLeast(101L);
                assertThat(cursor.getString(cursor.getColumnIndexOrThrow("stableId")))
                        .matches(uuid.pattern());
                assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
                        .isGreaterThan(0L);
                assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("deletedAt"))).isTrue();
            }
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrate15to16_createsConflictTable() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 15);
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(TEST_DB, 16, true, AppDatabase.MIGRATION_15_16);
        try (android.database.Cursor cursor =
                migrated.query(
                        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sync_conflicts'")) {
            assertThat(cursor.moveToFirst()).isTrue();
            assertThat(cursor.getString(0)).isEqualTo("sync_conflicts");
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrate16to17_createsSyncStateTable() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 16);
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(TEST_DB, 17, true, AppDatabase.MIGRATION_16_17);
        try (android.database.Cursor cursor =
                migrated.query(
                        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sync_state'")) {
            assertThat(cursor.moveToFirst()).isTrue();
            assertThat(cursor.getString(0)).isEqualTo("sync_state");
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrate17to18_preservesExistingConflictAndAllowsVersionPairsToCoexist()
            throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 17);
        db.execSQL(
                "INSERT INTO sync_conflicts "
                        + "(recordType, stableId, winnerSource, winnerJson, loserJson, winnerUpdatedAt, "
                        + "loserUpdatedAt, winnerTombstone, loserTombstone, resolution, resolved, createdAt, resolvedAt) "
                        + "VALUES ('note', 'stable', 'LOCAL', '{}', '{}', 1, 1, 0, 0, 'PENDING', 0, 1, 0)");
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(TEST_DB, 18, true, AppDatabase.MIGRATION_17_18);
        try {
            migrated.execSQL(
                    "INSERT INTO sync_conflicts "
                            + "(recordType, stableId, versionPairHash, winnerSource, winnerJson, loserJson, "
                            + "winnerUpdatedAt, loserUpdatedAt, winnerTombstone, loserTombstone, resolution, "
                            + "resolved, createdAt, resolvedAt) "
                            + "VALUES ('note', 'stable', 'new-pair', 'REMOTE', '{}', '{}', 2, 2, 0, 0, "
                            + "'PENDING', 0, 2, 0)");
            try (android.database.Cursor cursor =
                    migrated.query(
                            "SELECT COUNT(*) FROM sync_conflicts WHERE recordType = 'note' AND stableId = 'stable'")) {
                assertThat(cursor.moveToFirst()).isTrue();
                assertThat(cursor.getInt(0)).isEqualTo(2);
            }
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrate18to19_createsThePendingPreferencesJournal() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 18);
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(TEST_DB, 19, true, AppDatabase.MIGRATION_18_19);
        try (android.database.Cursor cursor =
                migrated.query(
                        "SELECT name FROM sqlite_master WHERE type = 'table' "
                                + "AND name = 'sync_pending_preferences'")) {
            assertThat(cursor.moveToFirst()).isTrue();
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrate19to20_addsJournalIdentityAndPerSideConflictProvenance() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 19);
        db.execSQL(
                "INSERT INTO sync_conflicts "
                        + "(recordType, stableId, versionPairHash, winnerSource, winnerJson, "
                        + "loserJson, winnerUpdatedAt, loserUpdatedAt, winnerTombstone, "
                        + "loserTombstone, resolution, resolved, createdAt, resolvedAt) "
                        + "VALUES ('note', 'stable', 'pair', 'LOCAL', '{}', '{}', 1, 1, 0, 0, "
                        + "'PENDING', 0, 1, 0)");
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(TEST_DB, 20, true, AppDatabase.MIGRATION_19_20);
        try (android.database.Cursor cursor =
                migrated.query(
                        "SELECT loserSource, winnerVersionId, loserVersionId FROM sync_conflicts")) {
            assertThat(cursor.moveToFirst()).isTrue();
            // A row written before this column existed always had exactly one local side.
            assertThat(cursor.getString(0)).isEqualTo("REMOTE");
            assertThat(cursor.getString(1)).isEmpty();
            assertThat(cursor.getString(2)).isEmpty();
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrate20to21_addsTheConflictBookkeepingToTheJournal() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 20);
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(TEST_DB, 21, true, AppDatabase.MIGRATION_20_21);
        try {
            migrated.execSQL(
                    "INSERT INTO sync_pending_preferences "
                            + "(id, payloadJson, targetHash, baselineHash, recordUpdatedAt, "
                            + "quarantined, conflictId, conflictResolution) "
                            + "VALUES (1, '{}', 't', 'b', 0, 0, 7, 'KEEP_WINNER')");
            try (android.database.Cursor cursor =
                    migrated.query(
                            "SELECT conflictId, conflictResolution FROM sync_pending_preferences")) {
                assertThat(cursor.moveToFirst()).isTrue();
                assertThat(cursor.getLong(0)).isEqualTo(7L);
                assertThat(cursor.getString(1)).isEqualTo("KEEP_WINNER");
            }
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrate21to22_addsTheSyncedVersionAndLeavesItUnknownForExistingRows()
            throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 21);
        db.execSQL(
                "INSERT INTO sync_metadata (recordType, localId, stableId, updatedAt, deletedAt) "
                        + "VALUES ('note', 1, 'stable', 1000, NULL)");
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(TEST_DB, 22, true, AppDatabase.MIGRATION_21_22);
        try (android.database.Cursor cursor =
                migrated.query("SELECT syncedVersionId FROM sync_metadata WHERE localId = 1")) {
            assertThat(cursor.moveToFirst()).isTrue();
            // Unknown until the next sync fills it in; the merge then behaves as before for it.
            assertThat(cursor.isNull(0)).isTrue();
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrateFromTheLastReleasedVersion_reachesTheCurrentSchema() throws IOException {
        // 17 is what 2.6.48 shipped; everything after it lands in later releases of this line.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 17);
        db.execSQL(
                "INSERT INTO notes "
                        + "(id, title, value, date, tag, valueJson, hasRichContent, attachments, "
                        + "isTrash, reminderTime, isPinned, reminderRepeat, reminderIntervalMinutes) "
                        + "VALUES (7, 'Note', 'Body', 10, '', '', 0, '', 0, NULL, 0, 'NONE', 0)");
        db.close();

        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(
                        TEST_DB,
                        22,
                        true,
                        AppDatabase.MIGRATION_17_18,
                        AppDatabase.MIGRATION_18_19,
                        AppDatabase.MIGRATION_19_20,
                        AppDatabase.MIGRATION_20_21,
                        AppDatabase.MIGRATION_21_22);
        try (android.database.Cursor cursor = migrated.query("SELECT COUNT(*) FROM notes")) {
            assertThat(cursor.moveToFirst()).isTrue();
            assertThat(cursor.getInt(0)).isEqualTo(1);
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrate14to15_backfillsCategoryMetadata() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 14);
        db.close();
        SupportSQLiteDatabase migrated =
                helper.runMigrationsAndValidate(TEST_DB, 15, true, AppDatabase.MIGRATION_14_15);
        try (android.database.Cursor cursor =
                migrated.query(
                        "SELECT COUNT(*) FROM sync_metadata WHERE recordType = 'category'")) {
            assertThat(cursor.moveToFirst()).isTrue();
            assertThat(cursor.getInt(0)).isEqualTo(0);
        } finally {
            migrated.close();
        }
    }
}
