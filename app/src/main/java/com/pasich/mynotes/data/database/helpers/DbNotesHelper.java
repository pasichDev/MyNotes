package com.pasich.mynotes.data.database.helpers;

import com.pasich.mynotes.data.model.Note;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.List;

public interface DbNotesHelper {
    Single<Integer> getCountData();

    Flowable<List<Note>> getNotes();

    Flowable<List<Note>> getNotesInTrash();

    Completable addNotes(List<Note> notes);

    Observable<List<Note>> getNotesForTag(String nameTag);

    Single<Integer> getCountNotesTag(String nameTag);

    Single<Note> getNoteForId(long idNote);

    Single<Long> addNote(Note note);

    Completable deleteNote(Note note);

    Completable deleteNote(ArrayList<Note> notes);

    Completable updateNote(Note note);

    Completable moveToNotes(List<Note> notes);

    Completable setTagNote(String nameTag, int idNote);

    Single<Long> copyNote(Note original);

    // New trash actions
    Completable moveNoteToTrash(int id);

    Completable transferNoteOutTrash(int id);

    Completable moveNotesToTrash(List<Integer> ids);

    Completable transferNotesOutTrash(List<Integer> ids);

    Completable clearTrash();

    Completable setTagForNotes(String tag, List<Integer> noteIds);

    Single<List<Note>> getNotesWithActiveReminders();

    Completable clearReminder(int noteId);

    Completable updateNoteReminder(int noteId, long reminderTime, String repeat);

    Completable updateNoteReminderFull(
            int noteId, long reminderTime, String repeat, int intervalMinutes);

    Completable setPinNote(int noteId, boolean pinned);
}
