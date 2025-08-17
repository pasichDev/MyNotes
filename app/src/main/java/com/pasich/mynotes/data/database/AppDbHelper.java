package com.pasich.mynotes.data.database;


import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.TrashNote;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;

@Singleton
public class AppDbHelper implements DbHelper {

    private final AppDatabase appDatabase;

    @Inject
    AppDbHelper(AppDatabase appDatabase) {
        this.appDatabase = appDatabase;
    }


    @Override
    public Flowable<List<Tag>> getTags() {
        return appDatabase.tagsDao().getTags();
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

    /**
     * Trash
     */
    @Override
    public Flowable<List<TrashNote>> getTrashNotesLoad() {
        return appDatabase.trashDao().getTrash();
    }


    @Override
    public Completable deleteTrashNotes(List<TrashNote> note) {
        return null;
    }

    @Override
    public Completable deleteAll() {
        return Completable.fromAction(() -> appDatabase.trashDao().deleteAll());
    }

    @Override
    public Completable addTrashNote(TrashNote note) {
        return Completable.fromAction(() -> appDatabase.trashDao().addNote(note));
    }

    @Override
    public Completable moveNoteToTrash(TrashNote tNote, Note mNote) {
        return Completable.fromAction(() -> appDatabase.transactionsNote().transferNoteToTrash(tNote, mNote));
    }

    @Override
    public Completable addTrashNotes(List<TrashNote> noteList) {
        return Completable.fromAction(() -> appDatabase.trashDao().addNotes(noteList));
    }

    @Override
    public Completable deleteTagForNotes(Tag tag) {
        return Completable.fromAction(() -> appDatabase.transactionsNote().deleteTagForNotes(tag));
    }

    @Override
    public Completable deleteTagAndNotes(Tag tag) {
        return Completable.fromAction(() -> appDatabase.transactionsNote().deleteTagAndNotes(tag));
    }

    @Override
    public Completable transferNoteOutTrash(TrashNote tNote, Note mNote) {
        return Completable.fromAction(() -> appDatabase.transactionsNote().transferNoteOutTrash(tNote, mNote));
    }

    @Override
    public Completable restoreNote(Note mNote) {
        return Completable.fromAction(() -> appDatabase.transactionsNote().restoreNote(mNote));
    }

    @Override
    public Completable renameTag(Tag mTag, String newName) {
        return Completable.fromAction(() -> appDatabase.transactionsNote().renameTag(mTag, newName));
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
    public Observable<List<Note>> getNotesForTag(String nameTag) {
        return Observable.fromCallable(() -> appDatabase.noteDao().getNotesForTag(nameTag));
    }

    @Override
    public Single<Integer> getCountNotesTag(String nameTag) {
        return Single.fromCallable(() -> appDatabase.noteDao().getCountNotesTag(nameTag));
    }

    @Override
    public Single<Note> getNoteForId(long idNote) {
        return Single.fromCallable(() -> appDatabase.noteDao().getNoteForId(idNote));
    }

    @Override
    public Single<Long> addNote(Note note, boolean copyNote) {
        return Single.fromCallable(() -> copyNote ? appDatabase.noteDao().addNoteCopy(note) : appDatabase.noteDao().addNote(note));
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

}
