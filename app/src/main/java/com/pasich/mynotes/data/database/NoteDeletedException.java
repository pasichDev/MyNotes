package com.pasich.mynotes.data.database;

/**
 * A note's content could not be written because the note no longer exists, for instance because a
 * sync applied another device's delete while it was open. Nothing was written.
 */
public class NoteDeletedException extends RuntimeException {

    public NoteDeletedException(long noteId) {
        super("Note " + noteId + " no longer exists");
    }
}
