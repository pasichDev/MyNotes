package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import io.reactivex.Flowable;
import java.util.List;

@Dao
public interface TagsDao {
    @Query("SELECT * FROM tags")
    Flowable<List<Tag>> getTags();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addTags(List<Tag> tags);

    @Update
    void updateTags(List<Tag> tags);

    @Query("SELECT * FROM tags where systemAction = " + SystemTagsManager.SYSTEM_ACTION_USER_TAG)
    Flowable<List<Tag>> getTagsUser();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addTag(Tag tag);

    @Query(
            "SELECT COUNT(name) FROM tags WHERE systemAction = "
                    + SystemTagsManager.SYSTEM_ACTION_USER_TAG)
    int getCountAllTag();

    @Update
    void updateTag(Tag tag);

    @Delete
    void deleteTag(Tag tag);
}
