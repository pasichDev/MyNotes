package com.pasich.mynotes.data.database.helpers;

import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;

public interface DbTasksHelper {
    Flowable<List<Task>> getActiveTasks();
    Flowable<List<Task>> getActiveTasksByCategory(int categoryId);
    Flowable<List<Task>> getCompletedTasks();
    Completable addTask(Task task);
    Completable updateTask(Task task);
    Completable deleteTask(Task task);
    Completable toggleTask(int taskId, boolean isDone);
    Completable clearCompletedTasks();
    Flowable<List<TaskCategory>> getCategories();
    Completable addCategory(TaskCategory category);
    Completable updateCategory(TaskCategory category);
    Completable deleteCategory(TaskCategory category);
    Single<Integer> getTaskCountForCategory(int categoryId);
    Completable setTaskReminder(int taskId, long time);
    Completable setTaskReminderFull(int taskId, long time, int intervalMinutes);
    Completable clearTaskReminder(int taskId);
    Single<List<Task>> getTasksWithReminders();
}
