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
