package com.pasich.mynotes.data.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.pasich.mynotes.data.model.Task;
import io.reactivex.Flowable;
import java.util.List;

/** DAO for task CRUD and reminder operations. */
@Dao
public interface TaskDao {

    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY position ASC, createdAt ASC")
    Flowable<List<Task>> getActiveTasks();

    @Query(
            "SELECT * FROM tasks WHERE categoryId = :categoryId AND isDone = 0 ORDER BY position ASC, createdAt ASC")
    Flowable<List<Task>> getActiveTasksByCategory(int categoryId);

    @Query("SELECT * FROM tasks WHERE isDone = 1 ORDER BY createdAt DESC")
    Flowable<List<Task>> getCompletedTasks();

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    Task getTaskSync(int taskId);

    @Query("DELETE FROM tasks WHERE id = :taskId")
    void deleteById(int taskId);

    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY position ASC, createdAt ASC LIMIT 10")
    List<Task> getActiveTasksSync();

    @Insert
    long insertTask(Task task);

    @Update
    void updateTask(Task task);

    @Delete
    void deleteTask(Task task);

    @Query("UPDATE tasks SET isDone = :isDone WHERE id = :taskId")
    void setTaskDone(int taskId, int isDone);

    @Query("DELETE FROM tasks WHERE isDone = 1")
    void clearCompletedTasks();

    @Query("SELECT COUNT(*) FROM tasks WHERE categoryId = :categoryId")
    int getTaskCountForCategory(int categoryId);

    @Query("UPDATE tasks SET reminderTime = :time WHERE id = :taskId")
    void setTaskReminder(int taskId, long time);

    @Query(
            "UPDATE tasks SET reminderTime = :time, reminderIntervalMinutes = :intervalMinutes WHERE id = :taskId")
    void setTaskReminderFullSync(int taskId, long time, int intervalMinutes);

    @Query("UPDATE tasks SET reminderTime = NULL, reminderIntervalMinutes = 0 WHERE id = :taskId")
    void clearTaskReminder(int taskId);

    @Query("SELECT * FROM tasks WHERE reminderTime IS NOT NULL AND isDone = 0")
    List<Task> getTasksWithRemindersSync();
}
