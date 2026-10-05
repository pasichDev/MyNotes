package com.pasich.mynotes.utils.editor;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class KeyboardBarStateTest {

    @Test
    public void hiddenUntilEditingWithTheKeyboardUp() {
        KeyboardBarState state = new KeyboardBarState();
        assertThat(state.isShown()).isFalse();

        assertThat(state.setImeVisible(true)).isFalse();
        assertThat(state.isShown()).isFalse();

        assertThat(state.setEditing(true)).isTrue();
        assertThat(state.isShown()).isTrue();
    }

    @Test
    public void readingHidesTheBarEvenWithTheKeyboardUp() {
        KeyboardBarState state = new KeyboardBarState();
        state.setEditing(true);
        state.setImeVisible(true);

        assertThat(state.setEditing(false)).isTrue();

        assertThat(state.isShown()).isFalse();
    }

    @Test
    public void noOnScreenKeyboardMeansNoBar() {
        // A hardware keyboard: editing, but no IME on screen.
        KeyboardBarState state = new KeyboardBarState();
        state.setEditing(true);

        assertThat(state.isShown()).isFalse();
        assertThat(state.setImeVisible(true)).isTrue();
        assertThat(state.setImeVisible(false)).isTrue();
        assertThat(state.isShown()).isFalse();
    }

    @Test
    public void reportsOnlyRealChanges() {
        KeyboardBarState state = new KeyboardBarState();
        state.setEditing(true);
        state.setImeVisible(true);

        assertThat(state.setEditing(true)).isFalse();
        assertThat(state.setImeVisible(true)).isFalse();
        assertThat(state.setHistory(false, false)).isFalse();
        assertThat(state.setHistory(true, false)).isTrue();
        assertThat(state.setHistory(true, false)).isFalse();
    }

    @Test
    public void undoAndRedoFollowTheHistoryWhileEditing() {
        KeyboardBarState state = new KeyboardBarState();
        state.setEditing(true);

        state.setHistory(true, false);
        assertThat(state.isUndoEnabled()).isTrue();
        assertThat(state.isRedoEnabled()).isFalse();

        state.setHistory(false, true);
        assertThat(state.isUndoEnabled()).isFalse();
        assertThat(state.isRedoEnabled()).isTrue();
    }

    @Test
    public void undoAndRedoAreOffWhileReading() {
        KeyboardBarState state = new KeyboardBarState();
        state.setHistory(true, true);

        assertThat(state.isUndoEnabled()).isFalse();
        assertThat(state.isRedoEnabled()).isFalse();
    }
}
