package com.pasich.mynotes.ui.view.activity.noteEditor;

import static com.google.common.truth.Truth.assertThat;

import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import com.pasich.mynotes.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SimpleEditorLengthTest {

    /**
     * A note longer than any input limit (shared into the app, written in the extended editor, or
     * restored from a backup) must reach the simple editor whole. A length filter also cuts the
     * text passed to setText, and the next autosave then wrote the cut text over the note.
     */
    @Test
    public void aLongNoteIsShownWhole() {
        View root =
                LayoutInflater.from(
                                new ContextThemeWrapper(
                                        RuntimeEnvironment.getApplication(), R.style.DefaultTheme))
                        .inflate(R.layout.activity_note, null);
        EditText body = root.findViewById(R.id.valueNote);
        String note = "x".repeat(109_000);

        body.setText(note);

        assertThat(body.getText().length()).isEqualTo(note.length());
    }
}
