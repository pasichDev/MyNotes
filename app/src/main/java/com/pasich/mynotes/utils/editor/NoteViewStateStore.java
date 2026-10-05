package com.pasich.mynotes.utils.editor;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Keeps each note's last reading or editing position on this device ({@link NoteViewState}), so a
 * reopened note continues where it was left.
 *
 * <p>Stored in a preferences file of its own, outside the settings that backups and Drive sync
 * carry: a position only makes sense on the device where it was taken. Only the {@link
 * #MAX_ENTRIES} most recently used notes are kept. Every instance reads and writes the same file,
 * and Android shares one in-memory copy of it per process, so instances never disagree.
 */
@Singleton
public class NoteViewStateStore {

    static final String FILE = "note_view_state";
    static final int MAX_ENTRIES = 200;
    private static final String KEY_PREFIX = "n_";
    private static final String TAG = "NoteViewStateStore";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private final Clock clock;

    interface Clock {
        long now();
    }

    @Inject
    public NoteViewStateStore(@ApplicationContext @NonNull Context context) {
        this(context, System::currentTimeMillis);
    }

    NoteViewStateStore(@NonNull Context context, @NonNull Clock clock) {
        this.prefs =
                context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        this.clock = clock;
    }

    /** The saved position of a note, or null when there is none (or it cannot be read). */
    @Nullable
    public NoteViewState get(long noteId) {
        if (noteId <= 0) return null;
        return parse(prefs.getString(key(noteId), null));
    }

    /** Whether anything is saved for the note. */
    public boolean contains(long noteId) {
        return noteId > 0 && prefs.contains(key(noteId));
    }

    /** Saves the simple editor's position, keeping the extended editor's. */
    public void putSimple(long noteId, @NonNull NoteViewState.Simple simple) {
        if (noteId <= 0) return;
        NoteViewState state = orEmpty(get(noteId));
        state.simple = simple;
        write(noteId, state);
    }

    /** Saves the extended editor's position, keeping the simple editor's. */
    public void putExtended(long noteId, @NonNull NoteViewState.Extended extended) {
        if (noteId <= 0) return;
        NoteViewState state = orEmpty(get(noteId));
        state.extended = extended;
        write(noteId, state);
    }

    /** Forgets a note's position; called when the note is deleted for good. */
    public void remove(long noteId) {
        if (noteId <= 0) return;
        prefs.edit().remove(key(noteId)).apply();
    }

    /** Forgets the positions of several notes. */
    public void removeAll(@Nullable Collection<? extends Number> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (Number id : noteIds) {
            if (id != null) editor.remove(key(id.longValue()));
        }
        editor.apply();
    }

    /** Forgets every position. */
    public void clear() {
        prefs.edit().clear().apply();
    }

    private void write(long noteId, NoteViewState state) {
        state.usedAt = clock.now();
        SharedPreferences.Editor editor = prefs.edit().putString(key(noteId), gson.toJson(state));
        Map<String, ?> all = prefs.getAll();
        // The entry being written may be new: count it before deciding what to drop.
        int count = all.size() + (all.containsKey(key(noteId)) ? 0 : 1);
        if (count > MAX_ENTRIES) {
            Map<String, Long> usedAt = new HashMap<>();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                if (entry.getKey().equals(key(noteId))) continue;
                NoteViewState other =
                        entry.getValue() instanceof String
                                ? parse((String) entry.getValue())
                                : null;
                usedAt.put(entry.getKey(), other != null ? other.usedAt : 0L);
            }
            for (String stale : leastRecentlyUsed(usedAt, count - MAX_ENTRIES)) {
                editor.remove(stale);
            }
        }
        editor.apply();
    }

    /** The {@code count} keys used longest ago. */
    @NonNull
    static List<String> leastRecentlyUsed(@NonNull Map<String, Long> usedAt, int count) {
        if (count <= 0) return Collections.emptyList();
        List<Map.Entry<String, Long>> entries = new ArrayList<>(usedAt.entrySet());
        entries.sort(
                (a, b) -> {
                    int byTime = Long.compare(a.getValue(), b.getValue());
                    return byTime != 0 ? byTime : a.getKey().compareTo(b.getKey());
                });
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < Math.min(count, entries.size()); i++) {
            keys.add(entries.get(i).getKey());
        }
        return keys;
    }

    @Nullable
    private NoteViewState parse(@Nullable String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return gson.fromJson(json, NoteViewState.class);
        } catch (JsonParseException | IllegalStateException e) {
            Log.w(TAG, "Dropping an unreadable position", e);
            return null;
        }
    }

    private static NoteViewState orEmpty(@Nullable NoteViewState state) {
        return state != null ? state : new NoteViewState();
    }

    private static String key(long noteId) {
        return KEY_PREFIX + noteId;
    }
}
