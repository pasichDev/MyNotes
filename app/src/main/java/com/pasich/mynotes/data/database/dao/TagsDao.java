package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pasich.mynotes.data.model.Tag;

import java.util.List;

import io.reactivex.Flowable;

@Dao
public interface TagsDao {
  @Query("SELECT * FROM tags")
  Flowable<List<Tag>> getTags();

  @Query("SELECT * FROM tags")
  List<Tag> getTagsWorker();

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void addTags(List<Tag> tags);

  @Query("SELECT * FROM tags where systemAction = 0")
  Flowable<List<Tag>> getTagsUser();

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void addTag(Tag tag);

  @Query("SELECT COUNT(name) FROM tags WHERE systemAction = 0")
  int getCountAllTag();

  @Update
  void updateTag(Tag tag);

  @Delete
  void deleteTag(Tag tag);

}
