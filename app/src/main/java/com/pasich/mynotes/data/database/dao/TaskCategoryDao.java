package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.pasich.mynotes.data.model.TaskCategory;
import io.reactivex.Flowable;
import java.util.List;

@Dao
public interface TaskCategoryDao {

    @Query("SELECT * FROM task_categories WHERE id = :id LIMIT 1")
    TaskCategory getCategorySync(int id);

    @Query("DELETE FROM task_categories WHERE id = :id")
    void deleteById(int id);

    @Query("SELECT * FROM task_categories ORDER BY position ASC, id ASC")
    Flowable<List<TaskCategory>> getCategories();

    @Query("SELECT * FROM task_categories ORDER BY position ASC, id ASC")
    List<TaskCategory> getCategoriesSync();

    @Insert
    long insertCategory(TaskCategory category);

    @Update
    void updateCategory(TaskCategory category);

    @Delete
    void deleteCategory(TaskCategory category);
}
