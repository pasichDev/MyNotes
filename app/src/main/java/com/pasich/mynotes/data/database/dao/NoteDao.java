package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pasich.mynotes.data.model.Note;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;

@Dao
public interface NoteDao {

    @Query("SELECT COUNT() FROM notes")
    int getDataCount();

    @Query("SELECT * FROM notes WHERE isTrash = 0")
    Flowable<List<Note>> getNotesAll();

    @Query("SELECT * FROM notes WHERE isTrash = 1")
    Flowable<List<Note>> getTrashNotes();

    @Query("SELECT * FROM notes WHERE isTrash = 1")
    List<Note> getTrashNotesSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Long addNote(Note note);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Long addNoteCopy(Note note);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addNotes(List<Note> notes);

    @Update
    void updateNote(Note note);

    @Delete
    void deleteNote(Note note);

    @Query("SELECT COUNT(tag) FROM notes WHERE tag = :nameTag AND isTrash = 0")
    int getCountNotesTag(String nameTag);

    @Query("SELECT * FROM notes WHERE tag = :nameTag AND isTrash = 0")
    List<Note> getNotesForTag(String nameTag);

    @Query("SELECT * FROM notes WHERE id=:idNote")
    Single<Note> getNoteForId(long idNote);

    @Query("UPDATE NOTES SET tag=:tag WHERE id=:noteID")
    void setTagNote(String tag, int noteID);


    @Query("UPDATE notes SET isTrash = 1 WHERE id IN (:ids)")
    void moveNotesToTrash(List<Integer> ids);

    @Query("UPDATE notes SET isTrash = 0 WHERE id IN (:ids)")
    void restoreNotesFromTrash(List<Integer> ids);

    @Query("UPDATE notes SET isTrash = 1 WHERE id = :id")
    void moveNoteToTrash(int id);

    @Query("UPDATE notes SET isTrash = 0 WHERE id = :id")
    void restoreNoteFromTrash(int id);

    @Query("DELETE FROM notes WHERE isTrash = 1")
    void deleteAllTrashNotes();


}
