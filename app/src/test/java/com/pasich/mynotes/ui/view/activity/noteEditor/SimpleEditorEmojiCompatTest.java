package com.pasich.mynotes.ui.view.activity.noteEditor;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import com.pasich.mynotes.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SimpleEditorEmojiCompatTest {

    /** Inflates the note screen the way the app does, through AppCompat. */
    public static class Host extends AppCompatActivity {
        @Override
        protected void onCreate(Bundle state) {
            setTheme(R.style.DefaultTheme);
            super.onCreate(state);
            setContentView(R.layout.activity_note);
        }
    }

    /**
     * EmojiCompat on the note body ends every edit with a change report for the whole text, so the
     * field laid out the whole note again on each keystroke: typing in a 109 KB note took more than
     * a full core on a Pixel 7a. The system emoji font draws emoji without it.
     */
    @Test
    public void theNoteBodyDoesNotRunEmojiCompat() {
        Host host = Robolectric.buildActivity(Host.class).setup().get();
        AppCompatEditText body = host.findViewById(R.id.valueNote);

        assertThat(body.isEmojiCompatEnabled()).isFalse();
    }
}
