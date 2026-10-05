package com.pasich.mynotes.extendedEditor.attach;

import androidx.annotation.NonNull;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Files the editor stored in this process whose block may not have reached the saved note yet.
 *
 * <p>An upload writes its file first; the block that references it reaches the database only on the
 * next save. A cleanup in between (the autosave of the previous change, a save on leaving the
 * screen) saw a file nobody referenced and deleted the image the user had just inserted. The
 * cleaner leaves every file listed here alone until a saved note references it.
 */
public final class RecentAttachmentUploads {

    private static final Map<Integer, Set<String>> UPLOADED = new HashMap<>();

    private RecentAttachmentUploads() {}

    /** Records a file just written into {@code note_<noteId>}. */
    public static synchronized void register(int noteId, @NonNull String fileName) {
        Set<String> names = UPLOADED.get(noteId);
        if (names == null) {
            names = new HashSet<>();
            UPLOADED.put(noteId, names);
        }
        names.add(fileName);
    }

    /** The files of a note that must not be treated as orphans yet. */
    @NonNull
    public static synchronized Set<String> protectedNames(int noteId) {
        Set<String> names = UPLOADED.get(noteId);
        return names == null ? Collections.emptySet() : new HashSet<>(names);
    }

    /** Stops protecting files a saved note now references; the cleaner keeps those anyway. */
    public static synchronized void settle(int noteId, @NonNull Collection<String> referenced) {
        Set<String> names = UPLOADED.get(noteId);
        if (names == null) return;
        names.removeAll(referenced);
        if (names.isEmpty()) UPLOADED.remove(noteId);
    }

    /** For tests. */
    static synchronized void clear() {
        UPLOADED.clear();
    }
}
