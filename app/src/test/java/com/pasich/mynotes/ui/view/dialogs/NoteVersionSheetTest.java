package com.pasich.mynotes.ui.view.dialogs;

import static com.google.common.truth.Truth.assertThat;

import com.pasich.mynotes.data.database.entities.NoteVersionEntity;
import com.pasich.mynotes.data.model.Note;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NoteVersionSheetTest {

    @Test
    public void aSheetRecreatedBeforeItsHostHasReloaded_waitsInsteadOfClosing() {
        assertThat(NoteVersionSheet.decide(false, null, null))
                .isEqualTo(NoteVersionSheet.Show.WAIT);
        assertThat(NoteVersionSheet.decide(false, new Note(), null))
                .isEqualTo(NoteVersionSheet.Show.WAIT);
    }

    @Test
    public void aVersionThatIsGone_closesTheSheet() {
        assertThat(NoteVersionSheet.decide(true, new Note(), null))
                .isEqualTo(NoteVersionSheet.Show.CLOSE);
    }

    @Test
    public void withTheNoteAndTheVersion_theSheetShowsIt() {
        assertThat(
                        NoteVersionSheet.decide(
                                true,
                                new Note(),
                                new NoteVersionEntity(1, null, "", "", null, null, 0L, "edit")))
                .isEqualTo(NoteVersionSheet.Show.BIND);
    }

    @Test
    public void theRestoreQuestionKeepsItsVersionInItsArguments() {
        // A dialog fragment is recreated from its arguments after a rotation.
        assertThat(RestoreVersionDialog.newInstance(77).versionId()).isEqualTo(77);
    }
}
