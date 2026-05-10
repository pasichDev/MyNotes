package com.pasich.mynotes.ui.contract;

import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;

import java.util.List;

import dagger.hilt.android.scopes.ActivityScoped;

public interface TasksContract {

    interface view extends BaseView {
        void renderTasks(List<Task> active, List<Task> completed);
        void renderCategories(List<TaskCategory> categories);
        void initListeners();
    }

    @ActivityScoped
    interface presenter extends BasePresenter<view> {
        void onCategorySelected(int categoryId);
        void addTask(String title, String description, int categoryId);
        void editTask(Task task, String newTitle, String newDescription);
        void toggleTask(Task task);
        void deleteTask(Task task);
        void clearCompleted();
        void addCategory(String name, String colorHex);
        void deleteCategory(TaskCategory category);
        void updatePositions(List<Task> tasks);
        void setTaskReminder(Task task, long time);
        void clearTaskReminder(Task task);
    }
}
