package com.pasich.mynotes.data;

import android.content.Context;
import android.net.Uri;
import com.pasich.mynotes.data.database.DbHelper;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.Task;
import com.pasich.mynotes.data.model.TaskCategory;
import com.pasich.mynotes.data.preferences.AppPreferencesHelper;
import com.pasich.mynotes.extendedEditor.attach.AttachmentCleaner;
import com.pasich.mynotes.utils.backup.BackupCacheHelper;
import com.pasich.mynotes.utils.backup.local.LocalBackup;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import com.pasich.mynotes.utils.backup.models.PreferencesBackup;
import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppDataManager implements DataManager {

    private final Context context;
    private final DbHelper dbHelper;
    private final AppPreferencesHelper preferencesHelper;
    private final LocalBackup apiBackup;

    @Inject
    AppDataManager(
            @ApplicationContext Context context,
            AppPreferencesHelper preferencesHelper,
            DbHelper dbHelper,
            LocalBackup apiBackup) {
        this.context = context;
        this.dbHelper = dbHelper;
        this.preferencesHelper = preferencesHelper;
        this.apiBackup = apiBackup;
    }

    /** PreferencesBackup */
    @Override
    public int getFormatCount() {
        return preferencesHelper.getFormatCount();
    }

    @Override
    public int getSizeTextNoteActivity() {
        return preferencesHelper.getSizeTextNoteActivity();
    }

    @Override
    public String getSortParam() {
        return preferencesHelper.getSortParam();
    }

    @Override
    public String getSortParamTags() {
        return preferencesHelper.getSortParamTags();
    }

    @Override
    public void setSortParamTags(String paramTags) {
        preferencesHelper.setSortParamTags(paramTags);
    }

    @Override
    public void editSizeTextNoteActivity(int value) {
        preferencesHelper.editSizeTextNoteActivity(value);
    }

    @Override
    public PreferencesBackup getListPreferences() {
        return preferencesHelper.getListPreferences();
    }

    @Override
    public void setListPreferences(PreferencesBackup preferences) {
        preferencesHelper.setListPreferences(preferences);
    }

    @Override
    public String getLastKnownVersion() {
        return preferencesHelper.getLastKnownVersion();
    }

    @Override
    public void setLastKnownVersion(String version) {
        preferencesHelper.setLastKnownVersion(version);
    }

    @Override
    public boolean isSyncEnabled() {
        return preferencesHelper.isSyncEnabled();
    }

    @Override
    public void setSyncEnabled(boolean enabled) {
        preferencesHelper.setSyncEnabled(enabled);
    }

    @Override
    public boolean isBackgroundSyncEnabled() {
        return preferencesHelper.isBackgroundSyncEnabled();
    }

    @Override
    public void setBackgroundSyncEnabled(boolean enabled) {
        preferencesHelper.setBackgroundSyncEnabled(enabled);
    }

    @Override
    public boolean isFirstSyncConfirmed() {
        return preferencesHelper.isFirstSyncConfirmed();
    }

    @Override
    public void setFirstSyncConfirmed(boolean confirmed) {
        preferencesHelper.setFirstSyncConfirmed(confirmed);
    }

    @Override
    public String getTypeFaceNoteActivity() {
        return preferencesHelper.getTypeFaceNoteActivity();
    }

    /** Tags */
    @Override
    public Flowable<List<Tag>> getTags() {
        return dbHelper.getTags();
    }

    @Override
    public Flowable<List<Tag>> getTagsUser() {
        return dbHelper.getTagsUser();
    }

    @Override
    public Single<Integer> getCountTagAll() {
        return dbHelper.getCountTagAll();
    }

    @Override
    public Completable addTag(Tag tag) {
        return dbHelper.addTag(tag);
    }

    @Override
    public Completable addTags(List<Tag> tags) {
        return dbHelper.addTags(tags);
    }

    @Override
    public Completable deleteTag(Tag tag) {
        return dbHelper.deleteTag(tag);
    }

    @Override
    public Completable updateTag(Tag tag) {
        return dbHelper.updateTag(tag);
    }

    @Override
    public Completable updateTags(List<Tag> tags) {
        return dbHelper.updateTags(tags);
    }

    @Override
    public Completable clearTagInNotes(Tag tag) {
        return dbHelper.clearTagInNotes(tag);
    }

    @Override
    public Completable deleteTagAndMoveNotesToTrash(Tag tag) {
        return dbHelper.deleteTagAndMoveNotesToTrash(tag);
    }

    @Override
    public Completable moveNoteToTrash(int id) {
        return dbHelper.moveNoteToTrash(id);
    }

    @Override
    public Completable transferNoteOutTrash(int id) {
        return dbHelper.transferNoteOutTrash(id);
    }

    @Override
    public Completable moveNotesToTrash(List<Integer> ids) {
        return dbHelper.moveNotesToTrash(ids);
    }

    @Override
    public Completable transferNotesOutTrash(List<Integer> ids) {
        return dbHelper.transferNotesOutTrash(ids);
    }

    @Override
    public Completable clearTrash() {
        return dbHelper.getNotesInTrash()
                .firstOrError()
                .flatMapCompletable(
                        notes -> {
                            for (Note note : notes) {
                                AttachmentCleaner.deleteAttachmentFolderByNoteId(
                                        context, note.getId());
                            }
                            return dbHelper.clearTrash();
                        });
    }

    @Override
    public Completable setTagForNotes(String tag, List<Integer> noteIds) {
        return dbHelper.setTagForNotes(tag, noteIds);
    }

    @Override
    public Completable renameTag(Tag mTag, String newName) {
        return dbHelper.renameTag(mTag, newName);
    }

    @Override
    public Completable restoreNotesAndFixTags(List<Integer> ids) {
        return dbHelper.restoreNotesAndFixTags(ids);
    }

    @Override
    public Single<Integer> getCountData() {
        return dbHelper.getCountData();
    }

    /** Notes */
    @Override
    public Flowable<List<Note>> getNotes() {
        return dbHelper.getNotes();
    }

    @Override
    public Flowable<List<Note>> getNotesInTrash() {
        return dbHelper.getNotesInTrash();
    }

    @Override
    public Completable addNotes(List<Note> notes) {
        return dbHelper.addNotes(notes);
    }

    @Override
    public Observable<List<Note>> getNotesForTag(String nameTag) {
        return dbHelper.getNotesForTag(nameTag);
    }

    @Override
    public Single<Integer> getCountNotesTag(String nameTag) {
        return dbHelper.getCountNotesTag(nameTag);
    }

    @Override
    public Single<Note> getNoteForId(long idNote) {
        return dbHelper.getNoteForId(idNote);
    }

    @Override
    public Single<Long> addNote(Note note) {
        return dbHelper.addNote(note);
    }

    @Override
    public Completable deleteNote(Note note) {
        return dbHelper.deleteNote(note);
    }

    @Override
    public Completable deleteNote(ArrayList<Note> notes) {
        return Completable.fromAction(
                        () -> {
                            for (Note note : notes) {
                                AttachmentCleaner.deleteAttachmentFolderByNoteId(
                                        context, note.getId());
                            }
                        })
                .andThen(dbHelper.deleteNote(notes));
    }

    @Override
    public Completable updateNote(Note note) {
        return dbHelper.updateNote(note);
    }

    @Override
    public Completable moveToNotes(List<Note> notes) {
        return dbHelper.moveToNotes(notes);
    }

    @Override
    public Completable setTagNote(String nameTag, int idNote) {
        return dbHelper.setTagNote(nameTag, idNote);
    }

    @Override
    public Single<Long> copyNote(Note original) {
        return dbHelper.copyNote(original);
    }

    @Override
    public boolean writeBackupLocalFile(BackupCacheHelper serviceCache, Uri uriLocalFile) {
        return apiBackup.writeBackupLocalFile(serviceCache, uriLocalFile);
    }

    @Override
    public JsonBackup readBackupLocalFile(Uri uriLocalFile) {
        return apiBackup.readBackupLocalFile(uriLocalFile);
    }

    @Override
    public Flowable<Integer> getNotesCount() {
        return dbHelper.getNotesCount();
    }

    @Override
    public Flowable<Integer> getNotesCreatedLastMonth() {
        return dbHelper.getNotesCreatedLastMonth();
    }

    @Override
    public Flowable<Long> getTotalCharacters() {
        return dbHelper.getTotalCharacters();
    }

    @Override
    public Single<List<Note>> getNotesWithActiveReminders() {
        return dbHelper.getNotesWithActiveReminders();
    }

    @Override
    public Completable clearReminder(int noteId) {
        return dbHelper.clearReminder(noteId);
    }

    @Override
    public Completable updateNoteReminder(int noteId, long reminderTime, String repeat) {
        return dbHelper.updateNoteReminder(noteId, reminderTime, repeat);
    }

    @Override
    public Completable updateNoteReminderFull(
            int noteId, long reminderTime, String repeat, int intervalMinutes) {
        return dbHelper.updateNoteReminderFull(noteId, reminderTime, repeat, intervalMinutes);
    }

    @Override
    public Completable setPinNote(int noteId, boolean pinned) {
        return dbHelper.setPinNote(noteId, pinned);
    }

    @Override
    public Flowable<List<Task>> getActiveTasks() {
        return dbHelper.getActiveTasks();
    }

    @Override
    public Flowable<List<Task>> getActiveTasksByCategory(int categoryId) {
        return dbHelper.getActiveTasksByCategory(categoryId);
    }

    @Override
    public Flowable<List<Task>> getCompletedTasks() {
        return dbHelper.getCompletedTasks();
    }

    @Override
    public Completable addTask(Task task) {
        return dbHelper.addTask(task);
    }

    @Override
    public Completable updateTask(Task task) {
        return dbHelper.updateTask(task);
    }

    @Override
    public Completable deleteTask(Task task) {
        return dbHelper.deleteTask(task);
    }

    @Override
    public Completable toggleTask(int taskId, boolean isDone) {
        return dbHelper.toggleTask(taskId, isDone);
    }

    @Override
    public Completable clearCompletedTasks() {
        return dbHelper.clearCompletedTasks();
    }

    @Override
    public Flowable<List<TaskCategory>> getCategories() {
        return dbHelper.getCategories();
    }

    @Override
    public Completable addCategory(TaskCategory category) {
        return dbHelper.addCategory(category);
    }

    @Override
    public Completable updateCategory(TaskCategory category) {
        return dbHelper.updateCategory(category);
    }

    @Override
    public Completable deleteCategory(TaskCategory category) {
        return dbHelper.deleteCategory(category);
    }

    @Override
    public Single<Integer> getTaskCountForCategory(int categoryId) {
        return dbHelper.getTaskCountForCategory(categoryId);
    }

    @Override
    public Completable setTaskReminder(int taskId, long time) {
        return dbHelper.setTaskReminder(taskId, time);
    }

    @Override
    public Completable setTaskReminderFull(int taskId, long time, int intervalMinutes) {
        return dbHelper.setTaskReminderFull(taskId, time, intervalMinutes);
    }

    @Override
    public Completable clearTaskReminder(int taskId) {
        return dbHelper.clearTaskReminder(taskId);
    }

    @Override
    public Single<List<Task>> getTasksWithReminders() {
        return dbHelper.getTasksWithReminders();
    }
}
