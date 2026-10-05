package com.pasich.mynotes.data.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pasich.mynotes.data.database.dao.NoteVersionDao;
import com.pasich.mynotes.data.database.entities.NoteVersionEntity;
import com.pasich.mynotes.data.model.Note;
import java.util.List;

/**
 * Decides when an earlier state of a note is worth keeping, and keeps the history bounded.
 *
 * <p>History is local to this device and never synced. Every method is meant to run inside the
 * caller's Room transaction, so a snapshot and the write that replaces its content commit or fail
 * together.
 *
 * <p>The editor autosaves a few seconds after every pause in typing, so keeping each save would
 * fill the history with near-identical states. An autosave keeps the state it replaces at most once
 * per {@link #AUTOSAVE_INTERVAL_MILLIS}, or at once when the edit removes a lot of text — the
 * select-all-and-type accident this history exists for. A state about to be overwritten by
 * something other than the user's typing (a sync, a conflict resolution, a restore) is always kept.
 */
public final class NoteHistory {

    /** Shortest time between two autosave snapshots of one note. */
    public static final long AUTOSAVE_INTERVAL_MILLIS = 10 * 60_000L;

    /** Removing at least this many characters keeps a snapshot regardless of the interval. */
    public static final int LARGE_EDIT_CHARS = 200;

    /** Versions kept per note; the oldest go first. */
    public static final int MAX_VERSIONS_PER_NOTE = 20;

    /** Keeps every {@code IN (...)} clause under SQLite's bound-variable limit. */
    private static final int QUERY_CHUNK = 500;

    /** Looks up a note's sync identity, or returns null when it has none yet. */
    public interface StableIds {
        @Nullable
        String of(int noteId);
    }

    /** History that keeps nothing; for callers built without a database. */
    public static final NoteHistory NONE = new NoteHistory(null, noteId -> null);

    @Nullable private final NoteVersionDao dao;
    @NonNull private final StableIds stableIds;

    public NoteHistory(@Nullable NoteVersionDao dao, @NonNull StableIds stableIds) {
        this.dao = dao;
        this.stableIds = stableIds;
    }

    /**
     * Keeps {@code stored} before the editor replaces it with {@code incoming}, when the interval
     * has passed since the last snapshot or the edit removes a lot of text.
     *
     * @return true when a version was written.
     */
    public boolean recordBeforeEdit(@Nullable Note stored, @NonNull Note incoming, long now) {
        if (dao == null || stored == null || sameText(stored, incoming) || !hasText(stored)) {
            return false;
        }
        NoteVersionEntity latest = dao.getLatest(stored.getId());
        boolean due =
                latest == null
                        || now - latest.createdAt >= AUTOSAVE_INTERVAL_MILLIS
                        // A clock moved backwards must not silence history until it catches up.
                        || now < latest.createdAt;
        if (!due && !isLargeEdit(stored, incoming)) return false;
        return write(stored, latest, NoteVersionReason.AUTOSAVE, now);
    }

    /**
     * Keeps {@code stored} before something other than the user's typing overwrites it.
     *
     * @param incoming what will replace it, or null when unknown; nothing is kept when it carries
     *     the same text.
     * @return true when a version was written.
     */
    public boolean recordBeforeOverwrite(
            @Nullable Note stored,
            @Nullable Note incoming,
            @NonNull NoteVersionReason reason,
            long now) {
        if (dao == null || stored == null || !hasText(stored)) return false;
        if (incoming != null && sameText(stored, incoming)) return false;
        return write(stored, dao.getLatest(stored.getId()), reason, now);
    }

    /** One version by its id, or null when it is gone. */
    @Nullable
    public NoteVersionEntity get(long versionId) {
        return dao == null ? null : dao.getById(versionId);
    }

    /** Removes the history of notes that are gone for good. */
    public void forget(@Nullable List<Integer> noteIds) {
        if (dao == null || noteIds == null || noteIds.isEmpty()) return;
        for (int start = 0; start < noteIds.size(); start += QUERY_CHUNK) {
            dao.deleteForNotes(
                    noteIds.subList(start, Math.min(noteIds.size(), start + QUERY_CHUNK)));
        }
    }

    public void forgetAll() {
        if (dao != null) dao.deleteAll();
    }

    /** Removes history whose note no longer exists; returns how many versions went. */
    public int sweepOrphans() {
        return dao == null ? 0 : dao.deleteOrphans();
    }

    private boolean write(
            @NonNull Note stored,
            @Nullable NoteVersionEntity latest,
            @NonNull NoteVersionReason reason,
            long now) {
        // Already kept: a second copy of the same text would only push an older one out.
        if (latest != null && sameText(latest, stored)) return false;
        dao.insert(
                new NoteVersionEntity(
                        stored.getId(),
                        stableIds.of(stored.getId()),
                        stored.getTitle(),
                        stored.getValue(),
                        stored.getValueJson(),
                        stored.getAttachments(),
                        now,
                        reason.name()));
        dao.prune(stored.getId(), MAX_VERSIONS_PER_NOTE);
        return true;
    }

    /** True when both carry the same title and body, which is all a version restores. */
    public static boolean sameText(@NonNull Note first, @NonNull Note second) {
        return first.getTitle().equals(second.getTitle())
                && first.getValue().equals(second.getValue())
                && first.getValueJson().equals(second.getValueJson());
    }

    static boolean sameText(@NonNull NoteVersionEntity version, @NonNull Note note) {
        return version.title.equals(note.getTitle())
                && version.value.equals(note.getValue())
                && nonNull(version.valueJson).equals(note.getValueJson());
    }

    /** Whether the state has any text worth getting back. */
    static boolean hasText(@NonNull Note note) {
        return !note.getTitle().trim().isEmpty() || !note.getValue().trim().isEmpty();
    }

    /**
     * Whether going from {@code before} to {@code after} takes out at least {@link
     * #LARGE_EDIT_CHARS} characters, or empties a note that had text.
     *
     * <p>Measured on the region where the two stop agreeing, so replacing a long passage with
     * another of the same length counts, while typing at the end of a long note does not.
     */
    static boolean isLargeEdit(@NonNull Note before, @NonNull Note after) {
        if (hasText(before) && !hasText(after)) return true;
        String first = before.getTitle() + "\n" + before.getValue();
        String second = after.getTitle() + "\n" + after.getValue();
        int shortest = Math.min(first.length(), second.length());
        int prefix = 0;
        while (prefix < shortest && first.charAt(prefix) == second.charAt(prefix)) prefix++;
        int suffix = 0;
        while (suffix < shortest - prefix
                && first.charAt(first.length() - 1 - suffix)
                        == second.charAt(second.length() - 1 - suffix)) {
            suffix++;
        }
        int removed = first.length() - prefix - suffix;
        return removed >= LARGE_EDIT_CHARS;
    }

    @NonNull
    private static String nonNull(@Nullable String value) {
        return value == null ? "" : value;
    }
}
