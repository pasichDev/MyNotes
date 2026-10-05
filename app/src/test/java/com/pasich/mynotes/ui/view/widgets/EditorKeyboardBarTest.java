package com.pasich.mynotes.ui.view.widgets;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import com.pasich.mynotes.R;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class EditorKeyboardBarTest {

    private static final int NAV_BAR = 48;
    private static final int KEYBOARD = 900;

    private EditorKeyboardBar bar;
    private final List<String> calls = new ArrayList<>();

    @Before
    public void setUp() {
        Context context =
                new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.DefaultTheme);
        FrameLayout parent = new FrameLayout(context);
        bar = new EditorKeyboardBar(context);
        parent.addView(
                bar,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.setActions(
                new EditorKeyboardBar.Actions() {
                    @Override
                    public void onUndo() {
                        calls.add("undo");
                    }

                    @Override
                    public void onRedo() {
                        calls.add("redo");
                    }

                    @Override
                    public void onHideKeyboard() {
                        calls.add("hide");
                    }
                });
    }

    private static WindowInsetsCompat insets(boolean keyboard) {
        return new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_BAR))
                .setInsets(
                        WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard ? KEYBOARD : 0))
                .setVisible(WindowInsetsCompat.Type.ime(), keyboard)
                .build();
    }

    private View undo() {
        return bar.findViewById(R.id.keyboardBarUndo);
    }

    private View redo() {
        return bar.findViewById(R.id.keyboardBarRedo);
    }

    @Test
    public void hiddenAndReservingNothingByDefault() {
        assertThat(bar.getVisibility()).isEqualTo(View.GONE);
        assertThat(bar.getReservedHeight()).isEqualTo(0);
    }

    @Test
    public void showsOnTopOfTheKeyboardWhileEditing() {
        bar.setEditing(true);
        bar.onWindowInsets(insets(true));

        assertThat(bar.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(((ViewGroup.MarginLayoutParams) bar.getLayoutParams()).bottomMargin)
                .isEqualTo(KEYBOARD);
        int expected =
                bar.getResources().getDimensionPixelSize(R.dimen.editor_keyboard_bar_row_height)
                        + bar.getResources()
                                .getDimensionPixelSize(R.dimen.editor_keyboard_bar_divider);
        assertThat(bar.getReservedHeight()).isEqualTo(expected);
    }

    @Test
    public void hiddenWithoutTheOnScreenKeyboard() {
        // A hardware keyboard: editing, no IME.
        bar.setEditing(true);
        bar.onWindowInsets(insets(false));

        assertThat(bar.getVisibility()).isEqualTo(View.GONE);
        assertThat(bar.getReservedHeight()).isEqualTo(0);
        assertThat(((ViewGroup.MarginLayoutParams) bar.getLayoutParams()).bottomMargin)
                .isEqualTo(NAV_BAR);
    }

    @Test
    public void hiddenWhileReading() {
        bar.onWindowInsets(insets(true));
        assertThat(bar.getVisibility()).isEqualTo(View.GONE);

        bar.setEditing(true);
        assertThat(bar.getVisibility()).isEqualTo(View.VISIBLE);

        bar.setEditing(false);
        assertThat(bar.getVisibility()).isEqualTo(View.GONE);
        assertThat(bar.getReservedHeight()).isEqualTo(0);
    }

    @Test
    public void goesAwayWhenTheKeyboardCloses() {
        bar.setEditing(true);
        bar.onWindowInsets(insets(true));

        bar.onWindowInsets(insets(false));

        assertThat(bar.getVisibility()).isEqualTo(View.GONE);
        assertThat(bar.getReservedHeight()).isEqualTo(0);
    }

    @Test
    public void undoAndRedoFollowTheHistory() {
        bar.setEditing(true);
        assertThat(undo().isEnabled()).isFalse();
        assertThat(redo().isEnabled()).isFalse();

        bar.setHistoryState(true, false);
        assertThat(undo().isEnabled()).isTrue();
        assertThat(redo().isEnabled()).isFalse();

        bar.setHistoryState(false, true);
        assertThat(undo().isEnabled()).isFalse();
        assertThat(redo().isEnabled()).isTrue();
    }

    @Test
    public void historyIsOffWhileReading() {
        bar.setHistoryState(true, true);

        assertThat(undo().isEnabled()).isFalse();
        assertThat(redo().isEnabled()).isFalse();
    }

    @Test
    public void buttonsAskTheEditor() {
        bar.setEditing(true);
        bar.setHistoryState(true, true);

        undo().performClick();
        redo().performClick();
        bar.findViewById(R.id.keyboardBarHide).performClick();

        assertThat(calls).containsExactly("undo", "redo", "hide").inOrder();
    }

    @Test
    public void buttonsAreLabelledForAccessibility() {
        assertThat(undo().getContentDescription().toString())
                .isEqualTo(bar.getContext().getString(R.string.editor_undo));
        assertThat(redo().getContentDescription().toString())
                .isEqualTo(bar.getContext().getString(R.string.editor_redo));
        assertThat(bar.findViewById(R.id.keyboardBarHide).getContentDescription().toString())
                .isEqualTo(bar.getContext().getString(R.string.editor_hide_keyboard));
        assertThat(undo().getTooltipText().toString())
                .isEqualTo(bar.getContext().getString(R.string.editor_undo));
    }
}
