package com.pasich.mynotes.extendedEditor;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import com.pasich.mynotes.R;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NoteEditorViewLayoutTest {

    @Test
    public void beforeTheNoteIsShown_thePageTakesPartInLayoutButIsNotDrawn() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.DefaultTheme);
        FrameLayout root = new FrameLayout(activity);
        NoteEditorView editor = new NoteEditorView(activity, null);
        root.addView(editor, new FrameLayout.LayoutParams(480, 800));
        activity.setContentView(root);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));

        // The page places the caret and the saved position as soon as the note is loaded, before
        // it is faded in: it must already be laid out at the size it will be shown at, so it is
        // kept visible and only transparent. (Robolectric does not size a WebView itself.)
        WebView page = editor.getWebView();
        assertThat(page.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(page.getAlpha()).isEqualTo(0f);
    }
}
