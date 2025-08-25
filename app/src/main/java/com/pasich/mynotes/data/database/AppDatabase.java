package com.pasich.mynotes.data.database;

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

import javax.inject.Singleton;

@androidx.room.Database(version = Database.DB_VERSION,
        entities = {Tag.class, Note.class, TrashNote.class},
        autoMigrations = {
                @AutoMigration(from = 1, to = 2)
        })
@Singleton
public abstract class AppDatabase extends RoomDatabase {

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DELETE FROM tags WHERE name = '' AND visibility = 0 AND systemAction = 1");
            database.execSQL("DELETE FROM tags WHERE name = 'allNotes' AND visibility = 0 AND systemAction = 2");
        }
    };

    public abstract TagsDao tagsDao();

    public abstract NoteDao noteDao();

    public abstract TrashDao trashDao();

    public abstract Transactions transactionsNote();
}
