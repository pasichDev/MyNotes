package com.pasich.mynotes.utils.editor;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class EditorCursorTest {

    private static final int NONE = EditorCursor.NONE;

    @Test
    public void restoredSelectionWinsOverTheViewport() {
        int[] selection = EditorCursor.activationSelection(120, 140, 400, 900, 2000);

        assertThat(selection).asList().containsExactly(120, 140).inOrder();
    }

    @Test
    public void restoredSelectionIsClampedToTheTextThatIsThere() {
        // The note got shorter (sync, another device) while the screen was gone.
        int[] selection = EditorCursor.activationSelection(500, 700, NONE, NONE, 300);

        assertThat(selection).asList().containsExactly(300, 300).inOrder();
    }

    @Test
    public void reversedRestoredSelectionIsNormalised() {
        int[] selection = EditorCursor.activationSelection(40, 10, NONE, NONE, 100);

        assertThat(selection).asList().containsExactly(10, 40).inOrder();
    }

    @Test
    public void restoredCaretWithoutEndIsACaret() {
        int[] selection = EditorCursor.activationSelection(25, NONE, NONE, NONE, 100);

        assertThat(selection).asList().containsExactly(25, 25).inOrder();
    }

    @Test
    public void longNoteReadInTheMiddleKeepsTheCaretOnScreen() {
        int[] selection = EditorCursor.activationSelection(NONE, NONE, 1200, 1800, 5000);

        assertThat(selection).asList().containsExactly(1200, 1200).inOrder();
    }

    @Test
    public void noteWhoseEndIsVisibleIsContinuedAtItsEnd() {
        int[] selection = EditorCursor.activationSelection(NONE, NONE, 0, 80, 80);

        assertThat(selection).asList().containsExactly(80, 80).inOrder();
    }

    @Test
    public void unknownViewportFallsBackToTheEnd() {
        int[] selection = EditorCursor.activationSelection(NONE, NONE, NONE, NONE, 42);

        assertThat(selection).asList().containsExactly(42, 42).inOrder();
    }

    @Test
    public void emptyNoteGetsOffsetZero() {
        int[] selection = EditorCursor.activationSelection(NONE, NONE, 0, 0, 0);

        assertThat(selection).asList().containsExactly(0, 0).inOrder();
    }

    @Test
    public void clampKeepsOffsetsInsideTheText() {
        assertThat(EditorCursor.clamp(-5, 10)).isEqualTo(0);
        assertThat(EditorCursor.clamp(15, 10)).isEqualTo(10);
        assertThat(EditorCursor.clamp(7, 10)).isEqualTo(7);
        assertThat(EditorCursor.clamp(3, 0)).isEqualTo(0);
    }

    @Test
    public void shortStillPressIsATap() {
        assertThat(EditorCursor.isTap(3f, 4f, 120, 8, 400)).isTrue();
    }

    @Test
    public void dragIsNotATap() {
        assertThat(EditorCursor.isTap(30f, 2f, 120, 8, 400)).isFalse();
    }

    @Test
    public void longPressIsNotATap() {
        // A long press selects a word; it must never open the link under it.
        assertThat(EditorCursor.isTap(0f, 0f, 400, 8, 400)).isFalse();
    }

    @Test
    public void tapOnLinkOpensItUnlessTheCaretIsAlreadyInside() {
        assertThat(EditorCursor.shouldOpenLink(0, 0, 10, 30)).isTrue();
        assertThat(EditorCursor.shouldOpenLink(5, 12, 10, 30)).isTrue();
        assertThat(EditorCursor.shouldOpenLink(15, 15, 10, 30)).isFalse();
        // At the very edge the caret is next to the link, not inside it.
        assertThat(EditorCursor.shouldOpenLink(10, 10, 10, 30)).isTrue();
        assertThat(EditorCursor.shouldOpenLink(0, 0, -1, -1)).isFalse();
    }
}
