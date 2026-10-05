package com.pasich.mynotes.utils.editor;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

/**
 * Where a note was last being read or edited on this device. One entry per note, with a section for
 * each editor, because the two describe a position differently: the simple editor by text offsets,
 * the extended one by Editor.js block ids.
 *
 * <p>It is device-local on purpose and never part of the {@code Note} entity: the note syncs, and a
 * caret position from one device means nothing on another.
 */
public final class NoteViewState {

    /** When the entry was last written, for the least-recently-used limit. */
    @SerializedName("u")
    public long usedAt;

    @SerializedName("s")
    @Nullable
    public Simple simple;

    @SerializedName("x")
    @Nullable
    public Extended extended;

    /** Position in the simple editor, by text offset, with enough context to find it again. */
    public static final class Simple {
        /** Selection when the note was left while editing; {@link EditorCursor#NONE} otherwise. */
        @SerializedName("ss")
        public int selectionStart = EditorCursor.NONE;

        @SerializedName("se")
        public int selectionEnd = EditorCursor.NONE;

        /** Offset of the text at the top of the viewport; NONE while the title was on screen. */
        @SerializedName("to")
        public int topOffset = EditorCursor.NONE;

        @SerializedName("sy")
        public int scrollY;

        /** Scroll position as a share of the scrollable height, 0..1. */
        @SerializedName("sr")
        public float scrollRatio;

        /** Length and hash of the text the offsets refer to. */
        @SerializedName("len")
        public int contentLength;

        @SerializedName("h")
        @Nullable
        public String contentHash;

        /** Text just before and just after the caret, to find it again after an edit. */
        @SerializedName("cb")
        @Nullable
        public String caretBefore;

        @SerializedName("ca")
        @Nullable
        public String caretAfter;

        /** The same around the top of the viewport. */
        @SerializedName("tb")
        @Nullable
        public String topBefore;

        @SerializedName("ta")
        @Nullable
        public String topAfter;
    }

    /** Position in the extended editor, by Editor.js block. */
    public static final class Extended {
        /** Block holding the caret and the caret's character offset in it; null when reading. */
        @SerializedName("cb")
        @Nullable
        public String caretBlockId;

        @SerializedName("co")
        public int caretOffset;

        /** Index of the block's input the caret is in (a list block has one per item). */
        @SerializedName("ci")
        public int caretInput;

        /** Block at the top of the viewport and its distance from the top, in CSS pixels. */
        @SerializedName("tb")
        @Nullable
        public String topBlockId;

        @SerializedName("ti")
        public int topBlockIndex = -1;

        @SerializedName("tp")
        public int topOffsetPx;

        @SerializedName("st")
        public int scrollTop;

        @SerializedName("sr")
        public float scrollRatio;
    }
}
