package com.pasich.mynotes.ui.view.dialogs;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pasich.mynotes.base.view.MoreNoteNoteActivityView;

/**
 * Undo and Redo in the editor's More sheet. They are there while the note is being edited, so the
 * history stays reachable with the keyboard (and the bar above it) put away, and are dimmed and
 * disabled when there is nothing to take back or bring back.
 */
final class EditHistoryRows {

    /** The Material 3 disabled content opacity. */
    static final float DISABLED_ALPHA = 0.38f;

    private EditHistoryRows() {}

    /** Shows the rows while {@code editor} edits a note and enables them by its history. */
    static void apply(
            @NonNull View group,
            @NonNull View undo,
            @NonNull View redo,
            @Nullable MoreNoteNoteActivityView editor) {
        boolean editing = editor != null && editor.isEditingNote();
        group.setVisibility(editing ? View.VISIBLE : View.GONE);
        setRowEnabled(undo, editing && editor.canUndoEdit());
        setRowEnabled(redo, editing && editor.canRedoEdit());
    }

    /** Disables a row and dims its icon and label; the row's background keeps its colour. */
    static void setRowEnabled(@NonNull View row, boolean enabled) {
        row.setEnabled(enabled);
        float alpha = enabled ? 1f : DISABLED_ALPHA;
        if (row instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) row;
            for (int i = 0; i < group.getChildCount(); i++) {
                group.getChildAt(i).setAlpha(alpha);
            }
        } else {
            row.setAlpha(alpha);
        }
    }
}
