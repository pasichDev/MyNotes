package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.TrashNote;


@Dao
public abstract class Transactions {

    @Query("UPDATE NOTES SET tag=:newTag WHERE tag=:oldTag")
    public abstract void renameTagNotes(String oldTag, String newTag);

    @Update
    public abstract void updateNote(Note note);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract void moveToTrash(TrashNote note);

    @Delete
    public abstract void deleteNote(Note note);

    @Delete
    public abstract void deleteTag(Tag tag);

    @Query("UPDATE NOTES SET tag='' WHERE tag=:tag")
    public abstract void deleteTagNotes(String tag);

    @Query("DELETE FROM NOTES  WHERE tag=:tag")
    public abstract void deleteTagAndNotes(String tag);

    @Query("UPDATE tags SET name=:newName WHERE id=:tagId")
    public abstract void setTagNote(String newName, long tagId);

    @Query(" INSERT INTO trash SELECT null,title,value,date FROM notes WHERE tag = :tag")
    public abstract void copyNoteToTrashFunctionDeleteTag(String tag);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract long addNote(Note note);

    @Query("DELETE FROM trash WHERE value=:text ")
    public abstract void deleteNoteForText(String text);

    @Delete
    public abstract void deleteTrashNotes(TrashNote note);


    /**
     * Транзакция переноса заметки в корзину
     *
     * @param tNote - модель корзинной заметки
     * @param mNote - заметка которую переносим
     */
    @Transaction
    public void transferNoteToTrash(TrashNote tNote, Note mNote) {
        moveToTrash(tNote);
        deleteNote(mNote);
    }

    /**
     * Перенос заметки из корзины
     */
    @Transaction
    public void transferNoteOutTrash(TrashNote tNote, Note mNote) {
        addNote(mNote);
        deleteTrashNotes(tNote);
    }


    /**
     * Удаление метки и удаление метки с заметки
     *
     * @param tag - метка
     */
    @Transaction
    public void deleteTagForNotes(Tag tag) {
        deleteTagNotes(tag.getNameTag());
        deleteTag(tag);
    }

    /**
     * Удаление метки и вместе с ней заметки
     *
     * @param tag - метка
     */
    @Transaction
    public void deleteTagAndNotes(Tag tag) {
        copyNoteToTrashFunctionDeleteTag(tag.getNameTag());
        deleteTagAndNotes(tag.getNameTag());
        deleteTag(tag);
    }

    /**
     * Удаление метки и вместе с ней заметки
     */
    @Transaction
    public void copyNotes(Note oNote, Note nNote, boolean noteActivity) {
        if (noteActivity) updateNote(oNote);

    }

    /**
     * Восстановление заметки и удаление из корзины через текст
     */
    @Transaction
    public void restoreNote(Note nNote) {
        addNote(nNote);
        deleteNoteForText(nNote.getValue());

    }

    /**
     * Метод переименования метки
     */
    @Transaction
    public void renameTag(Tag mTag, String newName) {
        renameTagNotes(mTag.getNameTag(), newName);
        setTagNote(newName, mTag.id);

    }
}
