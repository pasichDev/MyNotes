package com.pasich.mynotes.utils.editor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;

/**
 * Decides where a reopened note should be shown, from the position saved when it was left and the
 * content it has now. Free of Android types, so every case is covered by plain JVM tests.
 *
 * <p>The note may have changed in between, here or through a sync. In order of preference the
 * position is restored exactly (the text is unchanged), found again by the text around it, mapped
 * proportionally (a modest edit with no recognisable context left), or dropped in favour of the top
 * of the note (the note was emptied or largely rewritten). A returned offset is always within the
 * current text, so a selection built from it can never be out of range.
 */
public final class PositionRestorer {

    /** How much text on each side of a position is kept to find it again. */
    static final int ANCHOR_CHARS = 32;

    /** Shorter context than this matches too much to be trusted on its own. */
    static final int MIN_ANCHOR_CHARS = 8;

    /**
     * A note whose length changed by more than this share since the position was saved is treated
     * as rewritten: an offset mapped proportionally into it would point at unrelated text.
     */
    static final float MAX_LENGTH_CHANGE = 0.5f;

    /** How a position was recovered. */
    public enum Match {
        /** The content is unchanged; the position is exactly where it was. */
        EXACT,
        /** The content changed; the position was found again by its surrounding text. */
        ANCHOR,
        /** The content changed a little; the position was mapped proportionally and clamped. */
        CLAMPED,
        /** Nothing worth restoring: the note opens at its start. */
        TOP
    }

    private PositionRestorer() {}

    // ---------------------------------------------------------------------------------------
    // Simple editor
    // ---------------------------------------------------------------------------------------

    /** Where the simple editor should put the caret and the top of the viewport. */
    public static final class SimpleTarget {
        @NonNull public final Match match;

        /** Selection to apply, or {@link EditorCursor#NONE}; within the text when set. */
        public final int selectionStart;

        public final int selectionEnd;

        /** Offset to bring to the top of the viewport, or {@link EditorCursor#NONE}. */
        public final int topOffset;

        SimpleTarget(@NonNull Match match, int selectionStart, int selectionEnd, int topOffset) {
            this.match = match;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
            this.topOffset = topOffset;
        }

        public boolean hasSelection() {
            return selectionStart >= 0;
        }

        static SimpleTarget top() {
            return new SimpleTarget(
                    Match.TOP, EditorCursor.NONE, EditorCursor.NONE, EditorCursor.NONE);
        }
    }

    /**
     * Describes the simple editor's position for saving.
     *
     * @param text the note's text as shown.
     * @param selectionStart selection start while editing, or {@link EditorCursor#NONE}.
     * @param selectionEnd selection end while editing, or {@link EditorCursor#NONE}.
     * @param topOffset offset at the top of the viewport, or NONE while the title is on screen.
     * @param scrollY scroll position in pixels.
     * @param scrollRatio scroll position as a share of the scrollable height.
     */
    @NonNull
    public static NoteViewState.Simple captureSimple(
            @Nullable String text,
            int selectionStart,
            int selectionEnd,
            int topOffset,
            int scrollY,
            float scrollRatio) {
        String value = text != null ? text : "";
        int length = value.length();
        NoteViewState.Simple state = new NoteViewState.Simple();
        state.contentLength = length;
        state.contentHash = hash(value);
        state.scrollY = Math.max(0, scrollY);
        state.scrollRatio = clampRatio(scrollRatio);

        if (selectionStart >= 0) {
            int start = EditorCursor.clamp(selectionStart, length);
            int end = selectionEnd >= 0 ? EditorCursor.clamp(selectionEnd, length) : start;
            state.selectionStart = Math.min(start, end);
            state.selectionEnd = Math.max(start, end);
            state.caretBefore = before(value, state.selectionStart);
            state.caretAfter = after(value, state.selectionStart);
        }
        if (topOffset >= 0) {
            state.topOffset = EditorCursor.clamp(topOffset, length);
            state.topBefore = before(value, state.topOffset);
            state.topAfter = after(value, state.topOffset);
        }
        return state;
    }

    /**
     * Where to put the simple editor's caret and viewport for {@code text}, given what was saved.
     */
    @NonNull
    public static SimpleTarget restoreSimple(
            @Nullable NoteViewState.Simple saved, @Nullable String text) {
        if (saved == null || text == null || text.isEmpty()) return SimpleTarget.top();
        boolean hadSelection = saved.selectionStart >= 0;
        boolean hadTop = saved.topOffset >= 0;
        if (!hadSelection && !hadTop) return SimpleTarget.top();

        int length = text.length();

        if (length == saved.contentLength && hash(text).equals(saved.contentHash)) {
            int start = hadSelection ? snap(text, saved.selectionStart) : EditorCursor.NONE;
            int end =
                    hadSelection
                            ? snap(text, Math.max(saved.selectionStart, saved.selectionEnd))
                            : EditorCursor.NONE;
            int top = hadTop ? snap(text, saved.topOffset) : EditorCursor.NONE;
            return new SimpleTarget(Match.EXACT, start, end, top);
        }

        float scale = saved.contentLength > 0 ? (float) length / saved.contentLength : 1f;
        int caret =
                hadSelection
                        ? relocate(
                                text,
                                saved.caretBefore,
                                saved.caretAfter,
                                Math.round(saved.selectionStart * scale))
                        : EditorCursor.NONE;
        int top =
                hadTop
                        ? relocate(
                                text,
                                saved.topBefore,
                                saved.topAfter,
                                Math.round(saved.topOffset * scale))
                        : EditorCursor.NONE;

        if (caret >= 0 || top >= 0) {
            // The old selection's text may be gone, so only the caret is put back. Without its
            // own context the viewport follows the caret.
            int viewport = top >= 0 ? top : (hadTop ? caret : EditorCursor.NONE);
            return new SimpleTarget(Match.ANCHOR, caret, caret, viewport);
        }

        if (isRewrite(saved.contentLength, length)) return SimpleTarget.top();

        int clampedCaret =
                hadSelection
                        ? snap(text, Math.round(saved.selectionStart * scale))
                        : EditorCursor.NONE;
        int clampedTop =
                hadTop ? snap(text, Math.round(saved.topOffset * scale)) : EditorCursor.NONE;
        return new SimpleTarget(Match.CLAMPED, clampedCaret, clampedCaret, clampedTop);
    }

    // ---------------------------------------------------------------------------------------
    // Extended editor
    // ---------------------------------------------------------------------------------------

    /** Where the extended editor should put the caret and the top of the viewport. */
    public static final class ExtendedTarget {
        @NonNull public final Match match;

        /** Block to put the caret in, or null; with its input index and character offset. */
        @Nullable public final String caretBlockId;

        public final int caretInput;
        public final int caretOffset;

        /** Block to bring to the top, by id, or by index when its id is gone; else none. */
        @Nullable public final String topBlockId;

        public final int topBlockIndex;
        public final int topOffsetPx;

        ExtendedTarget(
                @NonNull Match match,
                @Nullable String caretBlockId,
                int caretInput,
                int caretOffset,
                @Nullable String topBlockId,
                int topBlockIndex,
                int topOffsetPx) {
            this.match = match;
            this.caretBlockId = caretBlockId;
            this.caretInput = caretInput;
            this.caretOffset = caretOffset;
            this.topBlockId = topBlockId;
            this.topBlockIndex = topBlockIndex;
            this.topOffsetPx = topOffsetPx;
        }

        static ExtendedTarget top() {
            return new ExtendedTarget(Match.TOP, null, 0, 0, null, -1, 0);
        }
    }

    /**
     * Where to put the extended editor's caret and viewport, given what was saved and the ids of
     * the blocks the note has now.
     *
     * <p>Editor.js keeps a block's id across edits and saves, so a block that still exists is found
     * by id wherever it moved. When none of the saved blocks is left, the note was replaced (or it
     * is an old plain-text note, whose single block gets a new id on every render): the viewport
     * then goes back to the same block index, clamped, and the caret is not restored.
     */
    @NonNull
    public static ExtendedTarget restoreExtended(
            @Nullable NoteViewState.Extended saved, @Nullable List<String> blockIds) {
        if (saved == null || blockIds == null || blockIds.isEmpty()) return ExtendedTarget.top();
        boolean hadCaret = saved.caretBlockId != null;
        boolean hadTop = saved.topBlockId != null || saved.topBlockIndex >= 0;
        if (!hadCaret && !hadTop) return ExtendedTarget.top();

        boolean caretFound = hadCaret && blockIds.contains(saved.caretBlockId);
        boolean topFound = saved.topBlockId != null && blockIds.contains(saved.topBlockId);

        if (!caretFound && !topFound) {
            if (saved.topBlockIndex < 0) return ExtendedTarget.top();
            int index = Math.min(saved.topBlockIndex, blockIds.size() - 1);
            return new ExtendedTarget(
                    Match.CLAMPED, null, 0, 0, null, index, Math.max(0, saved.topOffsetPx));
        }

        String caretId = caretFound ? saved.caretBlockId : null;
        int caretInput = caretFound ? Math.max(0, saved.caretInput) : 0;
        int caretOffset = caretFound ? Math.max(0, saved.caretOffset) : 0;
        // Without its own block the viewport is left to the caret, which scrolls itself into view.
        String topId = topFound ? saved.topBlockId : null;
        int topIndex = topFound ? blockIds.indexOf(saved.topBlockId) : -1;
        int topPx = topFound ? saved.topOffsetPx : 0;
        boolean exact = caretFound == hadCaret && topFound == (saved.topBlockId != null);
        return new ExtendedTarget(
                exact ? Match.EXACT : Match.ANCHOR,
                caretId,
                caretInput,
                caretOffset,
                topId,
                topIndex,
                topPx);
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    /** A stable 64-bit FNV-1a hash of the text, in hex. */
    @NonNull
    public static String hash(@NonNull String text) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            h ^= (c & 0xff);
            h *= 0x100000001b3L;
            h ^= (c >>> 8);
            h *= 0x100000001b3L;
        }
        return Long.toHexString(h);
    }

    /**
     * Finds the offset described by the text before and after it, preferring the occurrence nearest
     * to {@code expected}. Returns {@link EditorCursor#NONE} when the context is gone.
     */
    static int relocate(
            @NonNull String text, @Nullable String before, @Nullable String after, int expected) {
        String head = before != null ? before : "";
        String tail = after != null ? after : "";
        String both = head + tail;
        if (both.length() >= MIN_ANCHOR_CHARS) {
            int at = nearest(text, both, expected - head.length());
            if (at >= 0) return snap(text, at + head.length());
        }
        if (tail.length() >= MIN_ANCHOR_CHARS) {
            int at = nearest(text, tail, expected);
            if (at >= 0) return snap(text, at);
        }
        if (head.length() >= MIN_ANCHOR_CHARS) {
            int at = nearest(text, head, expected - head.length());
            if (at >= 0) return snap(text, at + head.length());
        }
        return EditorCursor.NONE;
    }

    private static int nearest(@NonNull String text, @NonNull String needle, int expected) {
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            long distance = Math.abs((long) at - expected);
            if (distance < bestDistance) {
                best = at;
                bestDistance = distance;
            }
        }
        return best;
    }

    static boolean isRewrite(int oldLength, int newLength) {
        if (oldLength <= 0) return true;
        return Math.abs(newLength - oldLength) > MAX_LENGTH_CHANGE * oldLength;
    }

    /** Clamps into the text and keeps an offset from splitting a surrogate pair. */
    static int snap(@NonNull String text, int offset) {
        int clamped = EditorCursor.clamp(offset, text.length());
        if (clamped > 0
                && clamped < text.length()
                && Character.isLowSurrogate(text.charAt(clamped))
                && Character.isHighSurrogate(text.charAt(clamped - 1))) {
            return clamped - 1;
        }
        return clamped;
    }

    private static String before(String text, int offset) {
        return text.substring(Math.max(0, offset - ANCHOR_CHARS), offset);
    }

    private static String after(String text, int offset) {
        return text.substring(offset, Math.min(text.length(), offset + ANCHOR_CHARS));
    }

    private static float clampRatio(float ratio) {
        if (Float.isNaN(ratio)) return 0f;
        return Math.max(0f, Math.min(1f, ratio));
    }
}
