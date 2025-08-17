package com.pasich.mynotes.data.database;

import androidx.room.AutoMigration;
import androidx.room.RoomDatabase;

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

    public abstract TagsDao tagsDao();

    public abstract NoteDao noteDao();

    public abstract TrashDao trashDao();

    public abstract Transactions transactionsNote();
}
