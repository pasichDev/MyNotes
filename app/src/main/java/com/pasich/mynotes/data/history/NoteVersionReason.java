package com.pasich.mynotes.data.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Why an earlier state of a note was kept. */
public enum NoteVersionReason {
    /** The editor's autosave replaced it, at most once per interval or after a large edit. */
    AUTOSAVE,
    /** A version from Google Drive replaced it during a sync. */
    PRE_SYNC,
    /** The user restored an older version over it. */
    PRE_RESTORE,
    /** The user settled a sync conflict with the other version. */
    PRE_CONFLICT;

    /** Reads a stored value, treating anything unknown as an ordinary autosave. */
    @NonNull
    public static NoteVersionReason fromStored(@Nullable String stored) {
        if (stored != null) {
            for (NoteVersionReason reason : values()) {
                if (reason.name().equals(stored)) return reason;
            }
        }
        return AUTOSAVE;
    }
}
