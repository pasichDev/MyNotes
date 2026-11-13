package com.pasich.mynotes.ui.view.dialogs;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import com.pasich.mynotes.data.model.Note;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The share dialog keeps what it needs in its arguments and never exports nothing silently. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ShareOptionsDialogArgsTest {

    private static Note note(int id, String title, String value) {
        Note note = new Note().create(title, value, 1L, "");
        note.setId(id);
        return note;
    }

    @Test
    public void recreatedDialog_keepsTheNoteIdsAndFlags() {
        ShareOptionsDialog original =
                ShareOptionsDialog.newInstance(
                        Arrays.asList(note(3, "A", "a"), note(9, "B", "b")), true);

        Fragment rebuilt =
                new FragmentFactory()
                        .instantiate(
                                getClass().getClassLoader(), ShareOptionsDialog.class.getName());
        rebuilt.setArguments(new Bundle(original.requireArguments()));

        Bundle args = rebuilt.requireArguments();
        assertThat(args.getIntArray("noteIds")).asList().containsExactly(3, 9).inOrder();
        assertThat(ShareOptionsDialog.exportTitle(args)).isEqualTo("Data_Export");
    }

    @Test
    public void singleNote_titleComesFromTheArguments() {
        Bundle args = ShareOptionsDialog.arguments(List.of(note(1, "", "text")), true, false);

        assertThat(ShareOptionsDialog.exportTitle(args)).isEqualTo("***");
        ShareOptionsDialog.Export export =
                ShareOptionsDialog.buildExport(args, List.of(note(1, "", "text")));
        assertThat(export.title).isEqualTo("***");
        assertThat(export.content).isEqualTo("text");
    }

    @Test
    public void severalNotes_areJoinedInOrder() {
        List<Note> notes = Arrays.asList(note(1, "One", "1"), note(2, "Two", "2"));
        Bundle args = ShareOptionsDialog.arguments(notes, false, false);

        ShareOptionsDialog.Export export = ShareOptionsDialog.buildExport(args, notes);

        assertThat(export.title).isEqualTo("Multiple_Notes");
        assertThat(export.content).isEqualTo("One\n\n1\n\n\n===================\n\nTwo\n\n2");
    }

    @Test
    public void notesGoneMeanwhile_nothingToExport() {
        Bundle args = ShareOptionsDialog.arguments(List.of(note(1, "A", "a")), false, true);

        assertThat(ShareOptionsDialog.buildExport(args, Collections.emptyList())).isNull();
        assertThat(ShareOptionsDialog.buildExport(args, null)).isNull();
    }
}
