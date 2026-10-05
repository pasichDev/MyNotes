package com.pasich.mynotes.ui.view.dialogs;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.pasich.mynotes.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Labels that were cut off ("Edit a co…", "Tonig…") may wrap to a second line instead. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LabelsFitTest {

    private final Context context =
            new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.DefaultTheme);

    @Test
    public void reminderPresets_mayWrap() {
        View sheet = LayoutInflater.from(context).inflate(R.layout.dialog_reminder_picker, null);
        assertThat(((TextView) sheet.findViewById(R.id.presetToday)).getMaxLines()).isAtLeast(2);
        assertThat(((TextView) sheet.findViewById(R.id.presetTomorrow)).getMaxLines()).isAtLeast(2);
    }

    @Test
    public void moreQuickActions_mayWrap() {
        View sheet = LayoutInflater.from(context).inflate(R.layout.dialog_more_note, null);
        ViewGroup copy = sheet.findViewById(R.id.quickCopy);
        TextView label = null;
        for (int i = 0; i < copy.getChildCount(); i++) {
            if (copy.getChildAt(i) instanceof TextView text) label = text;
        }
        assertThat(label).isNotNull();
        assertThat(label.getMaxLines()).isAtLeast(2);
    }
}
