package com.pasich.mynotes.utils.editor;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Carries a note's latest unsaved edits across a recreation of the editor screen, such as a
 * rotation, in memory.
 *
 * <p>The saved-state bundle has a size limit, so a long draft cannot go there; and the extended
 * editor's page hands over its last document asynchronously, after the old screen has saved its
 * state and often after the new screen has started. The old screen keeps that document here, and
 * the new one waits for the old page to finish ({@link #whenPreviousPageFinished}) before it shows
 * the note, so it never shows, or later saves over, an older document than was typed.
 *
 * <p>Used on the main thread only.
 */
public class RetainedNoteDraft extends ViewModel {

    /** A note's title and body (plain text or the extended editor's JSON); null when unchanged. */
    public static final class Draft {
        @Nullable public final String title;
        @Nullable public final String body;

        Draft(@Nullable String title, @Nullable String body) {
            this.title = title;
            this.body = body;
        }
    }

    private long noteId;
    @Nullable private String title;
    @Nullable private String body;
    // Pages of replaced screens still handing over their last edit.
    private int pagesFinishing;
    private final List<Runnable> afterPreviousPage = new ArrayList<>();

    /** Keeps the latest draft of note {@code noteId}; a null part keeps what was kept before. */
    @MainThread
    public void put(long noteId, @Nullable String title, @Nullable String body) {
        if (noteId != this.noteId) {
            this.title = null;
            this.body = null;
        }
        this.noteId = noteId;
        if (title != null) this.title = title;
        if (body != null) this.body = body;
    }

    /** The draft kept for {@code noteId}, which is then forgotten; null when there is none. */
    @Nullable
    @MainThread
    public Draft take(long noteId) {
        boolean ours = noteId > 0 && noteId == this.noteId && (title != null || body != null);
        Draft draft = ours ? new Draft(title, body) : null;
        title = null;
        body = null;
        return draft;
    }

    /** The screen is being recreated and its page is still handing over its last edit. */
    @MainThread
    public void previousPageFinishing() {
        pagesFinishing++;
    }

    /** A finishing page has handed over everything it had, or will never answer. */
    @MainThread
    public void previousPageFinished() {
        if (pagesFinishing > 0) pagesFinishing--;
        if (pagesFinishing > 0) return;
        List<Runnable> waiting = new ArrayList<>(afterPreviousPage);
        afterPreviousPage.clear();
        for (Runnable action : waiting) action.run();
    }

    /** Runs {@code action} once the page of the screen this one replaced has finished. */
    @MainThread
    public void whenPreviousPageFinished(@NonNull Runnable action) {
        if (pagesFinishing > 0) afterPreviousPage.add(action);
        else action.run();
    }

    @Override
    protected void onCleared() {
        afterPreviousPage.clear();
        title = null;
        body = null;
    }
}
