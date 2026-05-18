package com.pasich.mynotes.db;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.pasich.mynotes.data.database.AppDatabase;
import java.io.IOException;
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
    public void migrate2to3_succeeds() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2);
        db.close();
        helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3);
    }

    @Test
    public void migrate3to4_addsPositionColumn() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 3);
        db.close();
        helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4);
    }

    @Test
    public void migrate4to5_addsValueJsonAndRichContentColumns() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 4);
        db.close();
        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5);
    }

    @Test
    public void migrate5to6_addsAttachmentsColumn() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 5);
        db.close();
        helper.runMigrationsAndValidate(TEST_DB, 6, true, AppDatabase.MIGRATION_5_6);
    }

    @Test
    public void migrate6to7_addIsTrashColumn() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 6);
        db.close();
        helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7);
    }

    @Test
    public void migrateAllStepsChained_succeeds() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2);
        db.close();
        helper.runMigrationsAndValidate(
                TEST_DB,
                7,
                true,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7);
    }
}
