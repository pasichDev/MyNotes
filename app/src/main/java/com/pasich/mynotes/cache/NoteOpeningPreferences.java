package com.pasich.mynotes.cache;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * How notes open: in reading or editing mode, at their start or where they were left, and whether a
 * double tap in reading mode starts editing.
 *
 * <p>Kept in a preferences file of its own on purpose. These settings are not part of {@code
 * PreferencesBackup} or Drive sync: an older app version restoring or syncing settings writes back
 * only the keys it knows, and would otherwise reset these on every device it touches.
 */
@Singleton
public class NoteOpeningPreferences {

    /** Mode a note opens in. */
    public enum OpenMode {
        /** As before this setting: reading in the simple editor, editing in the extended one. */
        AUTO,
        READ,
        EDIT
    }

    /** Where in the note it opens. */
    public enum OpenPosition {
        START,
        /** Where it was left on this device, see {@code NoteViewStateStore}. */
        LAST
    }

    static final String FILE = "note_opening";
    static final String KEY_MODE = "open_mode";
    static final String KEY_POSITION = "open_position";
    static final String KEY_DOUBLE_TAP = "double_tap_to_edit";

    public static final OpenMode DEFAULT_MODE = OpenMode.AUTO;
    public static final OpenPosition DEFAULT_POSITION = OpenPosition.LAST;
    public static final boolean DEFAULT_DOUBLE_TAP = false;

    private final SharedPreferences prefs;

    @Inject
    public NoteOpeningPreferences(@ApplicationContext @NonNull Context context) {
        this.prefs =
                context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @NonNull
    public OpenMode getOpenMode() {
        return parse(prefs.getString(KEY_MODE, null), OpenMode.class, DEFAULT_MODE);
    }

    public void setOpenMode(@NonNull OpenMode mode) {
        prefs.edit().putString(KEY_MODE, mode.name()).apply();
    }

    @NonNull
    public OpenPosition getOpenPosition() {
        return parse(prefs.getString(KEY_POSITION, null), OpenPosition.class, DEFAULT_POSITION);
    }

    public void setOpenPosition(@NonNull OpenPosition position) {
        prefs.edit().putString(KEY_POSITION, position.name()).apply();
    }

    public boolean isDoubleTapToEdit() {
        return prefs.getBoolean(KEY_DOUBLE_TAP, DEFAULT_DOUBLE_TAP);
    }

    public void setDoubleTapToEdit(boolean enabled) {
        prefs.edit().putBoolean(KEY_DOUBLE_TAP, enabled).apply();
    }

    /** Whether the note's last position on this device is restored when it opens. */
    public boolean restoresLastPosition() {
        return getOpenPosition() == OpenPosition.LAST;
    }

    /**
     * Whether a note opens ready for editing.
     *
     * @param mode the chosen mode.
     * @param extendedEditor whether the note opens in the extended editor.
     * @param newNote whether the note is being created; a new note always opens for editing.
     */
    public static boolean opensInEditMode(
            @NonNull OpenMode mode, boolean extendedEditor, boolean newNote) {
        if (newNote) return true;
        return switch (mode) {
            case READ -> false;
            case EDIT -> true;
            case AUTO -> extendedEditor;
        };
    }

    @NonNull
    static <E extends Enum<E>> E parse(@Nullable String value, Class<E> type, @NonNull E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            // A value from a newer version this one does not know.
            return fallback;
        }
    }
}
