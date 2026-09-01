package com.pasich.mynotes.data.database;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.data.sync.SyncMutationCoordinator;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppDbHelper implements DbHelper {

    private final AppDatabase appDatabase;
    private final SyncMutationCoordinator syncMutationCoordinator;

    @Inject
    AppDbHelper(AppDatabase appDatabase, SyncMutationCoordinator syncMutationCoordinator) {
        this.appDatabase = appDatabase;
        this.syncMutationCoordinator = syncMutationCoordinator;
    }

    @Override
    public Flowable<List<Tag>> getTags() {
        return appDatabase
                .tagsDao()
                .getTags()
                .map(
                        userTags -> {
                            userTags.addAll(SystemTagsManager.getSystemTags());
                            return userTags;
                        });
    }

    @Override
    public Flowable<List<Tag>> getTagsUser() {
        return appDatabase.tagsDao().getTagsUser();
    }

    @Override
    public Single<Integer> getCountTagAll() {
        return Single.fromCallable(() -> appDatabase.tagsDao().getCountAllTag());
    }

    @Override
    public Completable addTag(Tag tag) {
        return Completable.fromAction(() -> syncMutationCoordinator.insertTag(tag));
    }

    @Override
    public Completable addTags(List<Tag> tags) {
        return Completable.fromAction(() -> syncMutationCoordinator.insertTags(tags));
    }

    @Override
    public Completable deleteTag(Tag tag) {
        return Completable.fromAction(() -> syncMutationCoordinator.deleteTag(tag));
    }

    @Override
    public Completable updateTag(Tag tag) {
        return Completable.fromAction(() -> syncMutationCoordinator.updateTag(tag));
    }

    @Override
    public Completable updateTags(List<Tag> tags) {
        return Completable.fromAction(() -> syncMutationCoordinator.updateTags(tags));
    }

    @Override
    public Completable clearTagInNotes(Tag tag) {
        return Completable.fromAction(() -> syncMutationCoordinator.deleteTagButKeepNotes(tag));
    }

    @Override
    public Completable deleteTagAndMoveNotesToTrash(Tag tag) {
        return Completable.fromAction(
                () -> syncMutationCoordinator.deleteTagAndMoveNotesToTrash(tag));
    }

    @Override
    public Completable moveNoteToTrash(int id) {
        return Completable.fromAction(() -> syncMutationCoordinator.moveNoteToTrash(id));
    }

    @Override
    public Completable moveNotesToTrash(List<Integer> ids) {
        return Completable.fromAction(() -> syncMutationCoordinator.moveNotesToTrash(ids));
    }

    @Override
    public Completable transferNoteOutTrash(int id) {
        return Completable.fromAction(() -> syncMutationCoordinator.restoreNoteFromTrash(id));
    }

    @Override
    public Completable transferNotesOutTrash(List<Integer> ids) {
        return Completable.fromAction(() -> syncMutationCoordinator.restoreNotesFromTrash(ids));
    }

    @Override
    public Completable clearTrash() {
        return Completable.fromAction(syncMutationCoordinator::deleteAllTrashNotes);
    }

    @Override
    public Completable setTagForNotes(String tag, List<Integer> noteIds) {
        return Completable.fromAction(() -> syncMutationCoordinator.setTagForNotes(tag, noteIds));
    }

    @Override
    public Completable renameTag(Tag mTag, String newName) {
        return Completable.fromAction(() -> syncMutationCoordinator.renameTag(mTag, newName));
    }

    @Override
    public Completable restoreNotesAndFixTags(List<Integer> ids) {
        return Completable.fromAction(() -> syncMutationCoordinator.restoreNotesAndFixTags(ids));
    }

    @Override
    public Single<Integer> getCountData() {
        return Single.fromCallable(() -> appDatabase.noteDao().getDataCount());
    }

    /** Notes */
    @Override
    public Flowable<List<Note>> getNotes() {
        return appDatabase.noteDao().getNotesAll();
    }

    @Override
    public Flowable<List<Note>> getNotesInTrash() {
        return appDatabase.noteDao().getTrashNotes();
    }

    @Override
    public Observable<List<Note>> getNotesForTag(String nameTag) {
        return Observable.fromCallable(() -> appDatabase.noteDao().getNotesForTag(nameTag));
    }

    @Override
    public Single<Integer> getCountNotesTag(String nameTag) {
        return Single.fromCallable(() -> appDatabase.noteDao().getCountNotesTag(nameTag));
    }

    @Override
    public Single<Note> getNoteForId(long idNote) {
        return appDatabase.noteDao().getNoteForId(idNote).onErrorReturnItem(new Note());
    }

    @Override
    public Single<Long> addNote(Note note, boolean copyNote) {
        return Single.fromCallable(() -> syncMutationCoordinator.insertNote(note));
    }

    public Single<Long> copyNote(Note original) {
        return addNote(original.duplicate(), true);
    }

    @Override
    public Completable addNotes(List<Note> notes) {
        return Completable.fromAction(() -> syncMutationCoordinator.insertNotes(notes));
    }

    @Override
    public Completable deleteNote(Note note) {
        return Completable.fromAction(() -> syncMutationCoordinator.deleteNote(note));
    }

    @Override
    public Completable deleteNote(ArrayList<Note> notes) {
        return Completable.fromAction(() -> syncMutationCoordinator.deleteNotes(notes));
    }

    @Override
    public Completable updateNote(Note note) {
        return Completable.fromAction(() -> syncMutationCoordinator.updateNoteContent(note));
    }

    @Override
    public Completable moveToNotes(List<Note> notes) {
        return Completable.fromAction(() -> syncMutationCoordinator.insertNotes(notes));
    }

    @Override
    public Completable setTagNote(String nameTag, int idNote) {
        return Completable.fromAction(() -> syncMutationCoordinator.setTagNote(nameTag, idNote));
    }

    @Override
    public Flowable<Integer> getNotesCount() {
        return appDatabase.noteDao().getNotesCount().map(v -> v == null ? 0 : v);
    }

    @Override
    public Flowable<Integer> getNotesCreatedLastMonth() {
        long monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
        return appDatabase.noteDao().getNotesCreatedSince(monthAgo).map(v -> v == null ? 0 : v);
    }

    @Override
    public Flowable<Long> getTotalCharacters() {
        return appDatabase.noteDao().getTotalCharacters().map(v -> v == null ? 0 : v);
    }

    @Override
    public Single<List<Note>> getNotesWithActiveReminders() {
        return Single.fromCallable(
                        () ->
                                appDatabase
                                        .noteDao()
                                        .getNotesWithActiveRemindersSync(
                                                System.currentTimeMillis()))
                .subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }

    @Override
    public Completable clearReminder(int noteId) {
        return Completable.fromAction(() -> syncMutationCoordinator.clearReminder(noteId))
                .subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }

    @Override
    public Completable updateNoteReminder(int noteId, long reminderTime, String repeat) {
        return Completable.fromAction(
                        () ->
                                syncMutationCoordinator.updateNoteReminder(
                                        noteId, reminderTime, repeat))
                .subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }

    @Override
    public Completable updateNoteReminderFull(
            int noteId, long reminderTime, String repeat, int intervalMinutes) {
        return Completable.fromAction(
                        () ->
                                syncMutationCoordinator.updateNoteReminderFull(
                                        noteId, reminderTime, repeat, intervalMinutes))
                .subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }

    @Override
    public Completable setPinNote(int noteId, boolean pinned) {
        return Completable.fromAction(() -> syncMutationCoordinator.setPinNote(noteId, pinned))
                .subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }

    // ---- DbTasksHelper ----

    @Override
    public Flowable<List<Task>> getActiveTasks() {
        return appDatabase.taskDao().getActiveTasks();
    }

    @Override
    public Flowable<List<Task>> getActiveTasksByCategory(int categoryId) {
        return appDatabase.taskDao().getActiveTasksByCategory(categoryId);
    }

    @Override
    public Flowable<List<Task>> getCompletedTasks() {
        return appDatabase.taskDao().getCompletedTasks();
    }

    @Override
    public Completable addTask(Task task) {
        return Completable.fromAction(() -> syncMutationCoordinator.insertTask(task));
    }

    @Override
    public Completable updateTask(Task task) {
        return Completable.fromAction(() -> syncMutationCoordinator.updateTask(task));
    }

    @Override
    public Completable deleteTask(Task task) {
        return Completable.fromAction(() -> syncMutationCoordinator.deleteTask(task));
    }

    @Override
    public Completable toggleTask(int taskId, boolean isDone) {
        return Completable.fromAction(() -> syncMutationCoordinator.setTaskDone(taskId, isDone));
    }

    @Override
    public Completable clearCompletedTasks() {
        return Completable.fromAction(() -> syncMutationCoordinator.clearCompletedTasks());
    }

    @Override
    public Flowable<List<TaskCategory>> getCategories() {
        return appDatabase.taskCategoryDao().getCategories();
    }

    @Override
    public Completable addCategory(TaskCategory category) {
        return Completable.fromAction(() -> syncMutationCoordinator.insertCategory(category));
    }

    @Override
    public Completable updateCategory(TaskCategory category) {
        return Completable.fromAction(() -> syncMutationCoordinator.updateCategory(category));
    }

    @Override
    public Completable deleteCategory(TaskCategory category) {
        return Completable.fromAction(() -> syncMutationCoordinator.deleteCategory(category));
    }

    @Override
    public Single<Integer> getTaskCountForCategory(int categoryId) {
        return Single.fromCallable(() -> appDatabase.taskDao().getTaskCountForCategory(categoryId));
    }

    @Override
    public Completable setTaskReminder(int taskId, long time) {
        return Completable.fromAction(() -> syncMutationCoordinator.setTaskReminder(taskId, time));
    }

    @Override
    public Completable setTaskReminderFull(int taskId, long time, int intervalMinutes) {
        return Completable.fromAction(
                        () ->
                                syncMutationCoordinator.setTaskReminderFull(
                                        taskId, time, intervalMinutes))
                .subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }

    @Override
    public Completable clearTaskReminder(int taskId) {
        return Completable.fromAction(() -> syncMutationCoordinator.clearTaskReminder(taskId))
                .subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }

    @Override
    public Single<List<Task>> getTasksWithReminders() {
        return Single.fromCallable(() -> appDatabase.taskDao().getTasksWithRemindersSync())
                .subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }
}
