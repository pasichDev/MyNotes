package com.pasich.mynotes.data.database;


import android.content.Context;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.extendedEditor.attach.AttachmentCleaner;
import com.pasich.mynotes.utils.managers.SystemTagsManager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;

@Singleton
public class AppDbHelper implements DbHelper {

    private final AppDatabase appDatabase;

    private final Context appContext;

    @Inject
    AppDbHelper(AppDatabase appDatabase, @ApplicationContext Context context) {
        this.appDatabase = appDatabase;
        this.appContext = context;
    }

    @Override
    public Flowable<List<Tag>> getTags() {
        return appDatabase.tagsDao().getTags().map(userTags -> {
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
        return Completable.fromAction(() -> appDatabase.tagsDao().addTag(tag));
    }

    @Override
    public Completable addTags(List<Tag> tags) {
        return Completable.fromAction(() -> appDatabase.tagsDao().addTags(tags));
    }

    @Override
    public Completable deleteTag(Tag tag) {
        return Completable.fromAction(() -> appDatabase.tagsDao().deleteTag(tag));
    }

    @Override
    public Completable updateTag(Tag tag) {
        return Completable.fromAction(() -> appDatabase.tagsDao().updateTag(tag));
    }

    @Override
    public Completable updateTags(List<Tag> tags) {
        return Completable.fromAction(() -> appDatabase.tagsDao().updateTags(tags));
    }


    @Override
    public Completable clearTagInNotes(Tag tag) {
        return Completable.fromAction(() -> appDatabase.transactionsNote().deleteTagButKeepNotes(tag));
    }

    @Override
    public Completable deleteTagAndMoveNotesToTrash(Tag tag) {
        return Completable.fromAction(() -> appDatabase.transactionsNote().deleteTagAndMoveNotesToTrash(tag));
    }

    @Override
    public Completable moveNoteToTrash(int id) {
        return Completable.fromCallable(() -> {
            appDatabase.noteDao().moveNoteToTrash(id);
            return null;
        });
    }

    @Override
    public Completable moveNotesToTrash(List<Integer> ids) {
        return Completable.fromAction(() -> appDatabase.noteDao().moveNotesToTrash(ids));
    }

    @Override
    public Completable transferNoteOutTrash(int id) {
        return Completable.fromCallable(() -> {
            appDatabase.noteDao().restoreNoteFromTrash(id);
            return null;
        });
    }

    @Override
    public Completable transferNotesOutTrash(List<Integer> ids) {
        return Completable.fromAction(() -> appDatabase.noteDao().restoreNotesFromTrash(ids));
    }

    @Override
    public Completable clearTrash() {
        return Completable.fromAction(() -> {
            List<Note> trashNotes = appDatabase.noteDao().getTrashNotesSync();

            // Deleting attachments
            for (Note note : trashNotes) {
                AttachmentCleaner.deleteAttachmentFolderByNoteId(appContext, note.getId());
            }

            // Deleting notes
            appDatabase.noteDao().deleteAllTrashNotes();
        });
    }

    @Override
    public Completable setTagForNotes(String tag, List<Integer> noteIds) {
        return Completable.fromAction(() -> appDatabase.noteDao().setTagForNotes(tag, noteIds));
    }

    @Override
    public Completable renameTag(Tag mTag, String newName) {
        return Completable.fromAction(() -> appDatabase.transactionsNote().renameTag(mTag, newName));
    }

    @Override
    public Completable restoreNotesAndFixTags(List<Integer> ids) {
        return Completable.fromAction(() -> appDatabase.transactionsNote().restoreNotesAndFixTags(ids));
    }


    @Override
    public Single<Integer> getCountData() {
        return Single.fromCallable(() -> appDatabase.noteDao().getDataCount());
    }


    /**
     * Notes
     */
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
        return appDatabase.noteDao().getNoteForId(idNote)
                .onErrorReturnItem(new Note());
    }

    @Override
    public Single<Long> addNote(Note note, boolean copyNote) {
        return Single.fromCallable(() -> copyNote ? appDatabase.noteDao().addNoteCopy(note) : appDatabase.transactionsNote().addNoteTransaction(note));
    }

    public Single<Long> copyNote(Note original) {
        return addNote(original.duplicate(), true);
    }


    @Override
    public Completable addNotes(List<Note> notes) {
        return Completable.fromAction(() -> appDatabase.noteDao().addNotes(notes));
    }

    @Override
    public Completable deleteNote(Note note) {
        return Completable.fromAction(() -> appDatabase.noteDao().deleteNote(note));
    }

    @Override
    public Completable deleteNote(ArrayList<Note> notes) {
        return Completable.fromAction(() -> {
            for (Note note : notes)
                appDatabase.noteDao().deleteNote(note);
        });
    }

    @Override
    public Completable updateNote(Note note) {
        return Completable.fromAction(() -> appDatabase.noteDao().updateNote(note));
    }

    @Override
    public Completable moveToNotes(List<Note> notes) {
        return Completable.fromAction(() -> appDatabase.noteDao().addNotes(notes));
    }

    @Override
    public Completable setTagNote(String nameTag, int idNote) {
        return Completable.fromAction(() -> appDatabase.noteDao().setTagNote(nameTag, idNote));
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
        return Single.fromCallable(() ->
                appDatabase.noteDao().getNotesWithActiveRemindersSync(System.currentTimeMillis())
        ).subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }

    @Override
    public Completable clearReminder(int noteId) {
        return Completable.fromAction(() ->
                appDatabase.noteDao().clearReminderSync(noteId)
        ).subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }

    @Override
    public Completable updateNoteReminder(int noteId, long reminderTime, String repeat) {
        return Completable.fromAction(() ->
                appDatabase.noteDao().updateReminderSync(noteId, reminderTime, repeat)
        ).subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }

    @Override
    public Completable updateNoteReminderFull(int noteId, long reminderTime, String repeat, int intervalMinutes) {
        return Completable.fromAction(() ->
                appDatabase.noteDao().updateReminderFullSync(noteId, reminderTime, repeat, intervalMinutes)
        ).subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }

    @Override
    public Completable setPinNote(int noteId, boolean pinned) {
        return Completable.fromAction(() ->
                appDatabase.noteDao().setPinNoteSync(noteId, pinned)
        ).subscribeOn(io.reactivex.schedulers.Schedulers.io());
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
        return Completable.fromAction(() -> appDatabase.taskDao().insertTask(task));
    }

    @Override
    public Completable updateTask(Task task) {
        return Completable.fromAction(() -> appDatabase.taskDao().updateTask(task));
    }

    @Override
    public Completable deleteTask(Task task) {
        return Completable.fromAction(() -> appDatabase.taskDao().deleteTask(task));
    }

    @Override
    public Completable toggleTask(int taskId, boolean isDone) {
        return Completable.fromAction(() -> appDatabase.taskDao().setTaskDone(taskId, isDone ? 1 : 0));
    }

    @Override
    public Completable clearCompletedTasks() {
        return Completable.fromAction(() -> appDatabase.taskDao().clearCompletedTasks());
    }

    @Override
    public Flowable<List<TaskCategory>> getCategories() {
        return appDatabase.taskCategoryDao().getCategories();
    }

    @Override
    public Completable addCategory(TaskCategory category) {
        return Completable.fromAction(() -> appDatabase.taskCategoryDao().insertCategory(category));
    }

    @Override
    public Completable updateCategory(TaskCategory category) {
        return Completable.fromAction(() -> appDatabase.taskCategoryDao().updateCategory(category));
    }

    @Override
    public Completable deleteCategory(TaskCategory category) {
        return Completable.fromAction(() -> appDatabase.taskCategoryDao().deleteCategory(category));
    }

    @Override
    public Single<Integer> getTaskCountForCategory(int categoryId) {
        return Single.fromCallable(() -> appDatabase.taskDao().getTaskCountForCategory(categoryId));
    }

    @Override
    public Completable setTaskReminder(int taskId, long time) {
        return Completable.fromAction(() -> appDatabase.taskDao().setTaskReminder(taskId, time));
    }

    @Override
    public Completable setTaskReminderFull(int taskId, long time, int intervalMinutes) {
        return Completable.fromAction(() ->
                appDatabase.taskDao().setTaskReminderFullSync(taskId, time, intervalMinutes)
        ).subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }

    @Override
    public Completable clearTaskReminder(int taskId) {
        return Completable.fromAction(() -> appDatabase.taskDao().clearTaskReminder(taskId));
    }

    @Override
    public Single<List<Task>> getTasksWithReminders() {
        return Single.fromCallable(() -> appDatabase.taskDao().getTasksWithRemindersSync())
                .subscribeOn(io.reactivex.schedulers.Schedulers.io());
    }
}
