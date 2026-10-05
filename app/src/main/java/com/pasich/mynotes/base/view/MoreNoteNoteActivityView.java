package com.pasich.mynotes.base.view;

import androidx.annotation.Nullable;

public interface MoreNoteNoteActivityView {

    void changeTextStyle();

    void changeTextSizeOnline(int sizeText);

    void changeTextSizeOffline();

    void closeActivityNotSaved();

    void changeTag(String nameTag, boolean change);

    void openCopyNote(long idNote);

    void changeEditor(long idNote);

    /** Saves what is pending and opens the note's local version history. */
    void openVersionHistory();

    /** Whether the note is being edited, so Undo and Redo apply to it. */
    boolean isEditingNote();

    /** Whether there is an edit to take back. */
    boolean canUndoEdit();

    /** Whether there is an undone edit to apply again. */
    boolean canRedoEdit();

    /** Takes back the last edit. */
    void undoLastEdit();

    /** Applies the last undone edit again. */
    void redoLastEdit();

    /**
     * Runs {@code observer} whenever editing starts or stops or what Undo and Redo can do changes;
     * {@code null} stops it.
     */
    void setEditHistoryObserver(@Nullable Runnable observer);
}
