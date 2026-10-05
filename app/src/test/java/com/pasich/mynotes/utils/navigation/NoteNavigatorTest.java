package com.pasich.mynotes.utils.navigation;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.ui.view.activity.noteEditor.NoteActivity;
import com.pasich.mynotes.ui.view.activity.noteEditor.NoteExtendedEditorActivity;
import org.junit.Test;

public class NoteNavigatorTest {

    @Test
    public void simpleEditor_forAPlainNote() {
        assertThat(NoteNavigator.editorFor(false, false)).isEqualTo(NoteActivity.class);
    }

    @Test
    public void extendedEditor_whenSwitchedOn() {
        assertThat(NoteNavigator.editorFor(true, false))
                .isEqualTo(NoteExtendedEditorActivity.class);
    }

    @Test
    public void extendedEditor_forANoteWithAttachments_evenWhenSwitchedOff() {
        // A reminder used to open such notes in the simple editor, which cannot show them.
        assertThat(NoteNavigator.editorFor(false, true))
                .isEqualTo(NoteExtendedEditorActivity.class);
    }
}
