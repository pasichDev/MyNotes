package com.pasich.mynotes.ui.contract;

import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import dagger.hilt.android.scopes.ActivityScoped;
import java.util.List;

/** Contract for the tasks (to-do) screen. */
public interface TasksContract {

    interface view extends BaseView {
        /** Renders active and completed task lists. */
        void renderTasks(List<Task> active, List<Task> completed);

        /** Renders the category chip list. */
        void renderCategories(List<TaskCategory> categories);

        /** Wires all UI event listeners. */
        void initListeners();
    }

    @ActivityScoped
    interface presenter extends BasePresenter<view> {
        /** Filters tasks by the given category id. */
        void onCategorySelected(int categoryId);

        /** Creates and persists a new task. */
        void addTask(String title, String description, int categoryId);

        /** Updates the title and description of an existing task. */
        void editTask(Task task, String newTitle, String newDescription);

        /** Toggles the done state of the given task. */
        void toggleTask(Task task);

        /** Permanently deletes the given task. */
        void deleteTask(Task task);

        /** Removes all completed tasks. */
        void clearCompleted();

        /** Creates and persists a new task category. */
        void addCategory(String name, String colorHex);

        /** Deletes a task category and its associated tasks. */
        void deleteCategory(TaskCategory category);

        /** Persists a new drag-and-drop ordering for the task list. */
        void updatePositions(List<Task> tasks);

        /** Schedules a reminder notification for the task at the given time. */
        void setTaskReminder(Task task, long time);

        /** Cancels the scheduled reminder for the given task. */
        void clearTaskReminder(Task task);
    }
}
