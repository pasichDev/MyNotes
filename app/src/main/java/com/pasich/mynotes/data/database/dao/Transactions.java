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


    @Transaction
    public long addNoteTransaction(Note note) {
        return addNote(note);
    }

    @Query("DELETE FROM trash WHERE value=:text ")
    public abstract void deleteNoteForText(String text);

    @Delete
    public abstract void deleteTrashNotes(TrashNote note);

    /**
     * Transaction for moving a note to the basket
     *
     * @param tNote - basket note model
     * @param mNote - note being moved
     */
    @Transaction
    public void transferNoteToTrash(TrashNote tNote, Note mNote) {
        moveToTrash(tNote);
        deleteNote(mNote);
    }

    // Moving a note from the trash
    @Transaction
    public void transferNoteOutTrash(TrashNote tNote, Note mNote) {
        addNote(mNote);
        deleteTrashNotes(tNote);
    }


    // Deleting a tag
    @Transaction
    public void deleteTagForNotes(Tag tag) {
        deleteTagNotes(tag.getNameTag());
        deleteTag(tag);
    }


    // Deleting a tag and the note associated with it
    @Transaction
    public void deleteTagAndNotes(Tag tag) {
        copyNoteToTrashFunctionDeleteTag(tag.getNameTag());
        deleteTagAndNotes(tag.getNameTag());
        deleteTag(tag);
    }

    @Transaction
    public void copyNotes(Note oNote, Note nNote, boolean noteActivity) {
        if (noteActivity) updateNote(oNote);

    }

    // Restoring a note and deleting it from the trash via text
    @Transaction
    public void restoreNote(Note nNote) {
        addNote(nNote);
        deleteNoteForText(nNote.getValue());

    }


    // Method for renaming a label
    @Transaction
    public void renameTag(Tag mTag, String newName) {
        renameTagNotes(mTag.getNameTag(), newName);
        setTagNote(newName, mTag.id);

    }
}
