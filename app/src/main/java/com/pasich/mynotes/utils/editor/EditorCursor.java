package com.pasich.mynotes.utils.editor;

/**
 * Pure decisions about where the simple editor's caret goes, kept free of Android types so they are
 * covered by plain JVM tests.
 */
public final class EditorCursor {

    /** No offset known. */
    public static final int NONE = -1;

    private EditorCursor() {}

    /** Clamps {@code offset} into {@code [0, length]}. */
    public static int clamp(int offset, int length) {
        if (length <= 0) return 0;
        return Math.max(0, Math.min(offset, length));
    }

    /**
     * Picks the selection to apply when editing is switched on.
     *
     * <p>A selection restored from a previous instance (rotation, process restore) wins, clamped to
     * the text that is actually there. Otherwise the caret goes where the reader is looking: the
     * end of the note when its end is on screen, so a short note is continued, or the start of the
     * first visible line, so a long note stays exactly where it was instead of jumping to its end.
     *
     * @param restoredStart restored selection start, or {@link #NONE}.
     * @param restoredEnd restored selection end, or {@link #NONE}.
     * @param firstVisibleOffset text offset at the top of the viewport, or {@link #NONE}.
     * @param lastVisibleOffset text offset at the bottom of the viewport, or {@link #NONE}.
     * @param length current text length.
     * @return {@code {start, end}}, both within {@code [0, length]}, start ≤ end.
     */
    public static int[] activationSelection(
            int restoredStart,
            int restoredEnd,
            int firstVisibleOffset,
            int lastVisibleOffset,
            int length) {
        if (restoredStart >= 0) {
            int start = clamp(restoredStart, length);
            int end = restoredEnd >= 0 ? clamp(restoredEnd, length) : start;
            return new int[] {Math.min(start, end), Math.max(start, end)};
        }
        if (firstVisibleOffset < 0 || lastVisibleOffset < 0 || lastVisibleOffset >= length) {
            return new int[] {length, length};
        }
        int caret = clamp(firstVisibleOffset, length);
        return new int[] {caret, caret};
    }

    /**
     * Whether a pointer that went down and up is a tap rather than a drag or a long press.
     *
     * @param dx horizontal travel in pixels.
     * @param dy vertical travel in pixels.
     * @param durationMs time between down and up.
     * @param touchSlop the platform touch slop in pixels.
     * @param longPressTimeoutMs the platform long-press timeout.
     */
    public static boolean isTap(
            float dx, float dy, long durationMs, int touchSlop, long longPressTimeoutMs) {
        if (durationMs < 0 || durationMs >= longPressTimeoutMs) return false;
        return dx * dx + dy * dy <= (float) touchSlop * touchSlop;
    }

    /**
     * Whether a tap on a link should open it. It should not when the caret already sits inside the
     * link: the user put it there on purpose to edit the address.
     */
    public static boolean shouldOpenLink(
            int selectionStart, int selectionEnd, int linkStart, int linkEnd) {
        if (linkStart < 0 || linkEnd <= linkStart) return false;
        boolean caretInside =
                selectionStart >= 0
                        && selectionStart == selectionEnd
                        && selectionStart > linkStart
                        && selectionStart < linkEnd;
        return !caretInside;
    }
}
