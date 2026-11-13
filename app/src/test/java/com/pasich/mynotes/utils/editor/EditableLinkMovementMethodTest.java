package com.pasich.mynotes.utils.editor;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Selection;
import android.text.method.TextKeyListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class EditableLinkMovementMethodTest {

    private EditText field;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        field = new EditText(activity);
        activity.setContentView(field);
        field.setText("Some note text");
        field.measure(
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        field.layout(0, 0, 600, 400);
        field.setMovementMethod(EditableLinkMovementMethod.getInstance());
        field.requestFocus();
        field.setSelection(3);
    }

    /**
     * Play Console crash in 2.7.56 ({@code setSpan (-1 ... -1) starts before 0}): the down lands
     * while the field is not selecting, selecting mode turns on (Shift on a hardware keyboard)
     * before the up, and {@code ArrowKeyMovementMethod} then selects from a start it never
     * recorded.
     */
    @Test
    public void upInSelectingModeWithoutARecordedDown_doesNotCrashAndKeepsTheCaret() {
        Editable buffer = field.getText();
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 50f, 10f, 0);
        field.onTouchEvent(down);
        down.recycle();
        TextKeyListener.getInstance()
                .onKeyDown(
                        field,
                        buffer,
                        KeyEvent.KEYCODE_SHIFT_LEFT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT));
        int caretBefore = Selection.getSelectionStart(buffer);

        MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, 50f, 10f, 0);
        boolean handled = EditableLinkMovementMethod.getInstance().onTouchEvent(field, buffer, up);
        up.recycle();

        assertThat(handled).isTrue();
        assertThat(Selection.getSelectionStart(buffer)).isEqualTo(caretBefore);
        assertThat(Selection.getSelectionEnd(buffer)).isEqualTo(caretBefore);
        // The platform ends selecting mode after an up; skipping it would leave Shift stuck.
        assertThat(
                        android.text.method.MetaKeyKeyListener.getMetaState(
                                buffer, KeyEvent.META_SHIFT_ON))
                .isEqualTo(0);
    }
}
