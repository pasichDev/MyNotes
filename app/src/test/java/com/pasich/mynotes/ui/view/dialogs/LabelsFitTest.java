package com.pasich.mynotes.ui.view.dialogs;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.pasich.mynotes.R;
import com.pasich.mynotes.ui.view.widgets.QuickActionsRow;
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

    /** 10 px a character: "Редагувати" is 100 px wide. */
    private static double tenPerChar(String text) {
        return text.length() * 10.0;
    }

    @Test
    public void aLabelWrapsOnlyBetweenWords_inTwoLines() {
        assertThat(QuickActionsRow.fits("Редагувати копію нотатки", 130, LabelsFitTest::tenPerChar))
                .isTrue();
        // Three lines would be needed: the label would be cut off.
        assertThat(QuickActionsRow.fits("Зробити переклад нотатки", 100, LabelsFitTest::tenPerChar))
                .isFalse();
        // "Редагувати" is wider than the cell: it would be broken inside the word.
        assertThat(QuickActionsRow.fits("Редагувати копію", 90, LabelsFitTest::tenPerChar))
                .isFalse();
        assertThat(QuickActionsRow.fits("Поділитися", 100, LabelsFitTest::tenPerChar)).isTrue();
    }

    @Test
    public void moreQuickActions_neverBreakInsideAWord() {
        View sheet = LayoutInflater.from(context).inflate(R.layout.dialog_more_note, null);
        assertThat((View) sheet.findViewById(R.id.quickActionsRow))
                .isInstanceOf(QuickActionsRow.class);
        ViewGroup copy = sheet.findViewById(R.id.quickCopy);
        for (int i = 0; i < copy.getChildCount(); i++) {
            if (copy.getChildAt(i) instanceof TextView label) {
                assertThat(label.getBreakStrategy())
                        .isEqualTo(android.text.Layout.BREAK_STRATEGY_SIMPLE);
                assertThat(label.getHyphenationFrequency())
                        .isEqualTo(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
            }
        }
    }
}
