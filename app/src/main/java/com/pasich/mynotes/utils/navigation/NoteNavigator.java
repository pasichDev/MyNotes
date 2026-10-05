package com.pasich.mynotes.utils.navigation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import com.pasich.mynotes.cache.ThemePreferencesCache;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.view.activity.noteEditor.NoteActivity;
import com.pasich.mynotes.ui.view.activity.noteEditor.NoteExtendedEditorActivity;

/** Navigates to the appropriate note editor based on settings and note type. */
public record NoteNavigator(Activity activity, ThemePreferencesCache prefs) {

    /**
     * The editor a note opens in: the extended one when it is switched on, and always for a note
     * with attachments, which the simple editor cannot show.
     */
    @NonNull
    public static Class<? extends Activity> editorFor(
            boolean extendedEditorEnabled, boolean hasAttachments) {
        return extendedEditorEnabled || hasAttachments
                ? NoteExtendedEditorActivity.class
                : NoteActivity.class;
    }

    /**
     * An intent that opens an existing note in the right editor, for callers without an activity to
     * start it from (a notification, a trampoline).
     */
    @NonNull
    public static Intent existingNoteIntent(
            @NonNull Context context,
            @NonNull ThemePreferencesCache prefs,
            long noteId,
            boolean hasAttachments) {
        Intent intent =
                new Intent(context, editorFor(prefs.isExtendedEditorEnabled(), hasAttachments));
        intent.putExtra(NoteExtras.EXTRA_NEW_NOTE, false);
        intent.putExtra(NoteExtras.EXTRA_ID_NOTE, noteId);
        return intent;
    }

    /** Opens a note by model, optionally with a shared-element transition. */
    public void openNote(
            @NonNull Note note,
            boolean isNew,
            String tag,
            View transitionView,
            String transitionName) {
        openNote(note.id, isNew, tag, transitionView, transitionName, note.isAttachments());
    }

    /** Opens a note by ID, routing to extended or simple editor as needed. */
    public void openNote(
            long noteId,
            boolean isNew,
            String tag,
            View transitionView,
            String transitionName,
            boolean isAttachesNote) {

        Intent intent =
                new Intent(activity, editorFor(prefs.isExtendedEditorEnabled(), isAttachesNote));

        intent.putExtra(NoteExtras.EXTRA_NEW_NOTE, isNew);
        intent.putExtra(NoteExtras.EXTRA_ID_NOTE, noteId);
        intent.putExtra(NoteExtras.EXTRA_TAG_NOTE, tag != null ? tag : "");

        if (transitionView != null) {
            ActivityOptionsCompat options =
                    ActivityOptionsCompat.makeSceneTransitionAnimation(
                            activity, transitionView, transitionName);
            activity.startActivity(intent, options.toBundle());
        } else {
            activity.startActivity(intent);
        }
    }
}
