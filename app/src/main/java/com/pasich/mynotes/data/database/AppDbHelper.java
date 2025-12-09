package com.pasich.mynotes.data.database;


import android.content.Context;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
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
}
