package com.pasich.mynotes.utils.reminder;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Snoozed note reminders on this device, kept so that a reboot or an app update re-arms them.
 *
 * <p>A snooze is a choice made on this phone for a notification shown on this phone, so it stays
 * here and is never synced; the note's own schedule is not changed by it.
 */
public final class SnoozeStore {

    private static final String PREFS = "reminder_snoozes";

    /** One remembered snooze. */
    public static final class Entry {
        public final int noteId;
        public final long time;
        public final int intervalMinutes;

        Entry(int noteId, long time, int intervalMinutes) {
            this.noteId = noteId;
            this.time = time;
            this.intervalMinutes = intervalMinutes;
        }
    }

    private final SharedPreferences prefs;

    public SnoozeStore(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void put(int noteId, long time, int intervalMinutes) {
        prefs.edit().putString(String.valueOf(noteId), time + ":" + intervalMinutes).apply();
    }

    public void remove(int noteId) {
        prefs.edit().remove(String.valueOf(noteId)).apply();
    }

    /** The snooze remembered for the note, or null. */
    @Nullable
    public Entry get(int noteId) {
        for (Entry entry : all()) {
            if (entry.noteId == noteId) return entry;
        }
        return null;
    }

    @NonNull
    public List<Entry> all() {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (!(e.getValue() instanceof String)) continue;
            try {
                String[] parts = ((String) e.getValue()).split(":", 2);
                entries.add(
                        new Entry(
                                Integer.parseInt(e.getKey()),
                                Long.parseLong(parts[0]),
                                parts.length > 1 ? Integer.parseInt(parts[1]) : 0));
            } catch (NumberFormatException ignored) {
                // A malformed entry cannot be re-armed; it is dropped at the next remove.
            }
        }
        return entries;
    }
}
