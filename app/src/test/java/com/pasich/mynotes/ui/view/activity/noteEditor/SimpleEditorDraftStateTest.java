package com.pasich.mynotes.ui.view.activity.noteEditor;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The simple editor has no length limit, so an unsaved draft is put in the saved state only while
 * it is small: a long note there crashed the app with TransactionTooLargeException.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SimpleEditorDraftStateTest {

    @Test
    public void aShortDraftGoesInTheSavedState() {
        assertThat(NoteActivity.draftFitsSavedState("title", "x".repeat(1000))).isTrue();
        assertThat(NoteActivity.draftFitsSavedState(null, null)).isTrue();
    }

    @Test
    public void aLongDraftIsLeftOut() {
        assertThat(NoteActivity.draftFitsSavedState("", "x".repeat(NoteActivity.MAX_DRAFT_CHARS)))
                .isTrue();
        assertThat(NoteActivity.draftFitsSavedState("t", "x".repeat(NoteActivity.MAX_DRAFT_CHARS)))
                .isFalse();
        assertThat(NoteActivity.draftFitsSavedState("", "x".repeat(500_000))).isFalse();
    }
}
