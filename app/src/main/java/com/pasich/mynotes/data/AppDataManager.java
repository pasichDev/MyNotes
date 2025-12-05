package com.pasich.mynotes.data;


import android.net.Uri;

import com.pasich.mynotes.data.database.DbHelper;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.utils.backup.models.JsonBackup;
import com.pasich.mynotes.utils.backup.models.PreferencesBackup;
import com.pasich.mynotes.data.preferences.AppPreferencesHelper;
import com.pasich.mynotes.utils.backup.BackupCacheHelper;
import com.pasich.mynotes.utils.backup.local.LocalBackup;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;

@Singleton
public class AppDataManager implements DataManager {


    private final DbHelper dbHelper;
    private final AppPreferencesHelper preferencesHelper;
    private final LocalBackup apiBackup;

    @Inject
    AppDataManager(AppPreferencesHelper preferencesHelper, DbHelper dbHelper, LocalBackup apiBackup) {
        this.dbHelper = dbHelper;
        this.preferencesHelper = preferencesHelper;
        this.apiBackup = apiBackup;
    }


    /**
     * PreferencesBackup
     */

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
    public String getTypeFaceNoteActivity() {
        return preferencesHelper.getTypeFaceNoteActivity();
    }


    /**
     * Tags
     */

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
        return dbHelper.clearTrash();
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

    /**
     * Notes
     */
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
    public Single<Long> addNote(Note note, boolean copyNote) {
        return dbHelper.addNote(note, copyNote);
    }


    @Override
    public Completable deleteNote(Note note) {
        return dbHelper.deleteNote(note);
    }

    @Override
    public Completable deleteNote(ArrayList<Note> notes) {
        return dbHelper.deleteNote(notes);
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

}
