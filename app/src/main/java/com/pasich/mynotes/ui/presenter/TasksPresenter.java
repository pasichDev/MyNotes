package com.pasich.mynotes.ui.presenter;

import android.content.Context;
import android.util.Log;
import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.ui.contract.TasksContract;
import com.pasich.mynotes.utils.reminder.TaskReminderManager;
import com.pasich.mynotes.utils.rx.SchedulerProvider;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import java.util.List;
import javax.inject.Inject;

@ActivityScoped
public class TasksPresenter extends BasePresenter<TasksContract.view>
        implements TasksContract.presenter {

    private static final String TAG = "TasksPresenter";
    private int selectedCategoryId = 0;
    private final Context appContext;

    @Inject
    public TasksPresenter(
            SchedulerProvider schedulerProvider,
            CompositeDisposable compositeDisposable,
            DataManager dataManager,
            @ApplicationContext Context appContext) {
        super(schedulerProvider, compositeDisposable, dataManager);
        this.appContext = appContext;
    }

    @Override
    public void viewIsReady() {
        getView().initListeners();
        loadCategories();
        loadTasks();
    }

    private void loadCategories() {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .getCategories()
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        categories -> {
                                            if (isViewAttached())
                                                getView().renderCategories(categories);
                                        },
                                        err -> Log.e(TAG, "loadCategories", err)));
    }

    private void loadTasks() {
        getCompositeDisposable().clear();
        loadCategories();

        Flowable<List<Task>> activeStream =
                selectedCategoryId == 0
                        ? getDataManager().getActiveTasks()
                        : getDataManager().getActiveTasksByCategory(selectedCategoryId);

        Flowable<List<Task>> completedStream = getDataManager().getCompletedTasks();

        getCompositeDisposable()
                .add(
                        Flowable.combineLatest(
                                        activeStream,
                                        completedStream,
                                        (active, completed) -> new Object[] {active, completed})
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(
                                        pair -> {
                                            if (isViewAttached()) {
                                                @SuppressWarnings("unchecked")
                                                List<Task> active = (List<Task>) pair[0];
                                                @SuppressWarnings("unchecked")
                                                List<Task> completed = (List<Task>) pair[1];
                                                getView().renderTasks(active, completed);
                                            }
                                        },
                                        err -> Log.e(TAG, "loadTasks", err)));
    }

    @Override
    public void onCategorySelected(int categoryId) {
        selectedCategoryId = categoryId;
        loadTasks();
    }

    @Override
    public void addTask(String title, String description, int categoryId) {
        if (title == null || title.trim().isEmpty()) return;
        Task task = new Task(title.trim(), categoryId);
        task.setDescription(description);
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .addTask(task)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(() -> {}, err -> Log.e(TAG, "addTask", err)));
    }

    @Override
    public void toggleTask(Task task) {
        boolean markingDone = !task.isDone();
        if (markingDone && task.getReminderTime() != null) {
            clearTaskReminder(task);
        }
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .toggleTask(task.getId(), markingDone)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(() -> {}, err -> Log.e(TAG, "toggleTask", err)));
    }

    @Override
    public void deleteTask(Task task) {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .deleteTask(task)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(() -> {}, err -> Log.e(TAG, "deleteTask", err)));
    }

    @Override
    public void clearCompleted() {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .clearCompletedTasks()
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(() -> {}, err -> Log.e(TAG, "clearCompleted", err)));
    }

    @Override
    public void addCategory(String name, String colorHex) {
        if (name == null || name.trim().isEmpty()) return;
        TaskCategory cat = new TaskCategory(name.trim(), colorHex);
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .addCategory(cat)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(() -> {}, err -> Log.e(TAG, "addCategory", err)));
    }

    @Override
    public void editTask(Task task, String newTitle, String newDescription) {
        if (newTitle == null || newTitle.trim().isEmpty()) return;
        task.setTitle(newTitle.trim());
        task.setDescription(
                newDescription == null || newDescription.trim().isEmpty()
                        ? null
                        : newDescription.trim());
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .updateTask(task)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(() -> {}, err -> Log.e(TAG, "editTask", err)));
    }

    @Override
    public void updatePositions(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setPosition(i);
        }
        getCompositeDisposable()
                .add(
                        io.reactivex.Observable.fromIterable(tasks)
                                .flatMapCompletable(t -> getDataManager().updateTask(t))
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(() -> {}, err -> Log.e(TAG, "updatePositions", err)));
    }

    @Override
    public void deleteCategory(TaskCategory category) {
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .deleteCategory(category)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(() -> {}, err -> Log.e(TAG, "deleteCategory", err)));
    }

    @Override
    public void setTaskReminder(Task task, long time) {
        task.setReminderTime(time);
        TaskReminderManager.scheduleReminder(appContext, task);
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .setTaskReminder(task.getId(), time)
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(() -> {}, err -> Log.e(TAG, "setTaskReminder", err)));
    }

    @Override
    public void clearTaskReminder(Task task) {
        TaskReminderManager.cancelReminder(appContext, task.getId());
        task.setReminderTime(null);
        getCompositeDisposable()
                .add(
                        getDataManager()
                                .clearTaskReminder(task.getId())
                                .subscribeOn(getSchedulerProvider().io())
                                .observeOn(getSchedulerProvider().ui())
                                .subscribe(() -> {}, err -> Log.e(TAG, "clearTaskReminder", err)));
    }
}
