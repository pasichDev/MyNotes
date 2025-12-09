package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;

import java.util.List;


@Dao
public abstract class Transactions {

    @Query("UPDATE notes SET tag = :newTag WHERE tag = :oldTag")
    public abstract void renameTagNotes(String oldTag, String newTag);

    @Delete
    public abstract void deleteTag(Tag tag);

    @Query("UPDATE notes SET tag = '' WHERE tag = :tag")
    public abstract void clearTagInNotes(String tag);

    @Query("UPDATE notes SET isTrash = 1 WHERE tag = :tag")
    public abstract void moveNotesWithTagToTrash(String tag);

    @Query("UPDATE tags SET name = :newName WHERE id = :tagId")
    public abstract void updateTagName(String newName, long tagId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract long addNote(Note note);

    @Query("UPDATE notes SET tag='' WHERE tag NOT IN (SELECT name FROM tags)")
    protected abstract void clearInvalidTags();

    @Query("UPDATE notes SET isTrash = 0 WHERE id IN (:ids)")
    protected abstract void restoreNotesInternal(List<Integer> ids);

    @Transaction
    public long addNoteTransaction(Note note) {
        return addNote(note);
    }

    // Only rename tag
    @Transaction
    public void renameTag(Tag tag, String newName) {
        renameTagNotes(tag.getNameTag(), newName);
        updateTagName(newName, tag.id);
    }

    // Delete tag but keep notes
    @Transaction
    public void deleteTagButKeepNotes(Tag tag) {
        clearTagInNotes(tag.getNameTag());
        deleteTag(tag);
    }

    // Delete tag → move notes to trash
    @Transaction
    public void deleteTagAndMoveNotesToTrash(Tag tag) {
        moveNotesWithTagToTrash(tag.getNameTag());
        clearTagInNotes(tag.getNameTag());
        deleteTag(tag);
    }


    @Transaction
    public void restoreNotesAndFixTags(List<Integer> ids) {
        clearInvalidTags();
        restoreNotesInternal(ids);
    }
}

