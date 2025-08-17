package com.pasich.mynotes.data.database.helpers;

import com.pasich.mynotes.data.model.Note;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;

public interface DbNotesHelper {
    /**
     * Notes
     */
    Single<Integer> getCountData();

    Flowable<List<Note>> getNotes();

    Completable addNotes(List<Note> notes);

    Observable<List<Note>> getNotesForTag(String nameTag);

    Single<Integer> getCountNotesTag(String nameTag);

    Single<Note> getNoteForId(long idNote);

    Single<Long> addNote(Note note, boolean copyNote);

    Completable deleteNote(Note note);

    Completable deleteNote(ArrayList<Note> notes);

    Completable updateNote(Note note);

    Completable moveToNotes(List<Note> notes);

    Completable setTagNote(String nameTag, int idNote);


}
