package com.pasich.mynotes.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
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
import com.pasich.mynotes.utils.constants.Database;
import com.pasich.mynotes.utils.constants.settings.PreferencesConfig;
import com.preference.PowerPreference;

import javax.inject.Singleton;

@androidx.room.Database(version = Database.DB_VERSION,
        entities = {Tag.class, Note.class, TrashNote.class},
        autoMigrations = {
                @AutoMigration(from = 1, to = 2)
        })
@Singleton
public abstract class AppDatabase extends RoomDatabase {

    private static Context appContext;

    public static void setContext(Context context) {
        appContext = context.getApplicationContext();
    }

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Видаляємо старі системні теги
            database.execSQL("DELETE FROM tags WHERE name = '' AND visibility = 0 AND systemAction = 1");
            database.execSQL("DELETE FROM tags WHERE name = 'allNotes' AND visibility = 0 AND systemAction = 2");
            
            // Встановлюємо lastKnownVersion = "2.1.29" для міграції
            if (appContext != null) {
                PowerPreference.init(appContext);
                PowerPreference.getDefaultFile()
                    .setString(PreferencesConfig.ARGUMENT_PREFERENCE_LAST_KNOWN_VERSION, "2.1.29");
            }
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Додаємо поле position до таблиці tags
            database.execSQL("ALTER TABLE tags ADD COLUMN position INTEGER NOT NULL DEFAULT 0");
        }
    };

    public abstract TagsDao tagsDao();

    public abstract NoteDao noteDao();

    public abstract TrashDao trashDao();

    public abstract Transactions transactionsNote();
}
