package com.pasich.mynotes.utils.editor;

/**
 * What the bar above the keyboard shows, kept free of Android types so it is covered by plain JVM
 * tests.
 *
 * <p>The bar is there only while the note is being edited and the on-screen keyboard is up: in
 * reading mode there is nothing to undo, and with a hardware keyboard (no on-screen keyboard)
 * Ctrl+Z and Ctrl+Y do the job and the bar would only take space from the note. Undo and Redo are
 * enabled while the editor's history has a step to take back or to apply again.
 */
public final class KeyboardBarState {

    private boolean editing;
    private boolean imeVisible;
    private boolean canUndo;
    private boolean canRedo;

    /** Sets whether the note is being edited; returns whether the bar's visibility changed. */
    public boolean setEditing(boolean editing) {
        boolean wasShown = isShown();
        this.editing = editing;
        return wasShown != isShown();
    }

    /**
     * Sets whether the on-screen keyboard is visible; returns whether the bar's visibility changed.
     */
    public boolean setImeVisible(boolean imeVisible) {
        boolean wasShown = isShown();
        this.imeVisible = imeVisible;
        return wasShown != isShown();
    }

    /** Sets what the editor's history allows; returns whether either button changed. */
    public boolean setHistory(boolean canUndo, boolean canRedo) {
        boolean changed = this.canUndo != canUndo || this.canRedo != canRedo;
        this.canUndo = canUndo;
        this.canRedo = canRedo;
        return changed;
    }

    public boolean isEditing() {
        return editing;
    }

    /** The bar sits above the keyboard only while the note is edited with the keyboard up. */
    public boolean isShown() {
        return editing && imeVisible;
    }

    /** Undo is offered while editing and there is a step to take back. */
    public boolean isUndoEnabled() {
        return editing && canUndo;
    }

    /** Redo is offered while editing and there is an undone step to apply again. */
    public boolean isRedoEnabled() {
        return editing && canRedo;
    }
}
