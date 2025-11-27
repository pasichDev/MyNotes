package com.pasich.mynotes.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.pasich.mynotes.data.database.dao.NoteDao;
import com.pasich.mynotes.data.database.dao.TagsDao;
import com.pasich.mynotes.data.database.dao.Transactions;
import com.pasich.mynotes.data.database.dao.TrashDao;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.TrashNote;
import com.pasich.mynotes.utils.constants.DatabaseConstants;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.preference.PowerPreference;

import javax.inject.Singleton;

@Database(version = DatabaseConstants.DB_VERSION,
        entities = {Tag.class, Note.class, TrashNote.class},
        autoMigrations = {
                @AutoMigration(from = 1, to = 2)
        })
@Singleton
public abstract class AppDatabase extends RoomDatabase {
    /**
     * Remove old system tags
     * Add the position field to the tags table
     */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tags ADD COLUMN position INTEGER NOT NULL DEFAULT 0");
        }
    };
    /**
     * Додаємо колонку attachments json до таблиці notes
     * [
     * {
     * «url»: «file:///data/user/0/.../attachments/file1.pdf»,
     * «name»: «file1.pdf»,
     * «extension»: «pdf»,
     * «size»: 12345
     * }
     * ]
     */
    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE notes ADD COLUMN attachments TEXT DEFAULT ''");
        }
    };
    private static Context appContext;
    /**
     * Remove old system tags
     * Set lastKnownVersion = “2.1.29” for migration
     */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DELETE FROM tags WHERE name = '' AND visibility = 0 AND systemAction = 1");
            database.execSQL("DELETE FROM tags WHERE name = 'allNotes' AND visibility = 0 AND systemAction = 2");

            if (appContext != null) {
                PowerPreference.init(appContext);
                PowerPreference.getDefaultFile()
                        .setString(PreferencesConfig.ARGUMENT_PREFERENCE_LAST_KNOWN_VERSION, "2.1.29");
            }
        }
    };
    /**
     * Add the valueJson column (nullable) to the notes table.
     * Add the hasRichContent column (boolean, NOT NULL, default false).
     * Set extendedEditorEnable = false for existing users.
     */
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE notes ADD COLUMN valueJson TEXT");
            database.execSQL("ALTER TABLE notes ADD COLUMN hasRichContent INTEGER NOT NULL DEFAULT 0");

            if (appContext != null) {
                PowerPreference.init(appContext);
                PowerPreference.getDefaultFile()
                        .setBoolean(PreferencesConfig.ARGUMENT_PREFERENCE_EXTENDED_EDITOR, false);
            }
        }
    };

    public static void setContext(Context context) {
        appContext = context.getApplicationContext();
    }

    public abstract TagsDao tagsDao();

    public abstract NoteDao noteDao();

    public abstract TrashDao trashDao();

    public abstract Transactions transactionsNote();
}
