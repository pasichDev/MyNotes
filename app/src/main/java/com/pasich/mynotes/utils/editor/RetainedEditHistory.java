package com.pasich.mynotes.utils.editor;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

/**
 * Carries the extended editor's undo history across a recreation of the screen, such as a rotation.
 * The page lives in the WebView and is lost with the screen; it hands its history over as it
 * finishes and the new page takes it over for the same note.
 *
 * <p>Kept in memory only, as a {@link ViewModel}: the history can hold many versions of a long
 * note, far more than the saved-state bundle allows, and it has no value once the screen is closed.
 * The page bounds what it hands over.
 */
public class RetainedEditHistory extends ViewModel {

    private long noteId;
    @Nullable private String history;

    /** Keeps the history the page of note {@code noteId} handed over, replacing any older one. */
    public synchronized void put(long noteId, @Nullable String history) {
        this.noteId = noteId;
        this.history = history;
    }

    /**
     * The history kept for {@code noteId}, which is then forgotten; null when there is none, or it
     * belongs to another note.
     */
    @Nullable
    public synchronized String take(long noteId) {
        String kept = noteId > 0 && noteId == this.noteId ? history : null;
        history = null;
        return kept;
    }
}
