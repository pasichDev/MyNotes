package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.pasich.mynotes.data.model.TrashNote;

import java.util.List;

import io.reactivex.Flowable;

@Dao
public interface TrashDao {
 @Query("SELECT * FROM trash")
 Flowable<List<TrashNote>> getTrash();

 @Query("SELECT * FROM trash")
 List<TrashNote> getTrashWorker();

 @Delete
 void deleteNote(TrashNote note);

 @Insert(onConflict = OnConflictStrategy.IGNORE)
 Long addNote(TrashNote trashNote);

 @Insert(onConflict = OnConflictStrategy.IGNORE)
 void addNotes(List<TrashNote> trashNote);

  @Query("DELETE FROM trash")
  void deleteAll();

}
