package com.pasich.mynotes.data.database.helpers;

import com.pasich.mynotes.data.model.TrashNote;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Flowable;

public interface DbTrashHelper {
    /**
     * Trash
     */
    Flowable<List<TrashNote>> getTrashNotesLoad();

    Completable addTrashNotes(List<TrashNote> noteList);

    Completable deleteTrashNotes(List<TrashNote> note);

    Completable deleteAll();

    Completable addTrashNote(TrashNote note);

}
