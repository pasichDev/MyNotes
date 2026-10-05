package com.pasich.mynotes.base.view;

import com.pasich.mynotes.data.model.Note;

public interface MoreNoteMainActivityView {

    void actionStartNote(Note note, int position);

    void openCopyNote(long idNote);

    void callbackDeleteNote(Note mNote);

    /** Opens the note's local version history. */
    void openVersionHistory(int noteId);
}
