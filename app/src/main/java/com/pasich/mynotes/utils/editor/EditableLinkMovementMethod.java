package com.pasich.mynotes.utils.editor;

import android.content.ActivityNotFoundException;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

/**
 * Movement for an editable text that also contains links.
 *
 * <p>It is the movement every EditText already uses, with one addition: a tap on a link opens it.
 * The previous {@code LinkMovementMethod} subclass was built for read-only text: it cleared the
 * selection whenever the field took focus, so the caret fell back to offset 0 and the next
 * keystroke landed at the very top of the note. Dragging, long presses and taps outside links are
 * left entirely to the platform, so selection, handles and the caret behave as in any field.
 */
public final class EditableLinkMovementMethod extends ArrowKeyMovementMethod {

    private static final String TAG = "EditableLinkMovement";
    private static EditableLinkMovementMethod instance;

    private float downX;
    private float downY;
    private long downTime;

    private EditableLinkMovementMethod() {}

    public static MovementMethod getInstance() {
        if (instance == null) instance = new EditableLinkMovementMethod();
        return instance;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            downTime = event.getEventTime();
        } else if (action == MotionEvent.ACTION_UP && isTap(widget, event)) {
            URLSpan link = linkAt(widget, buffer, event);
            if (link != null
                    && EditorCursor.shouldOpenLink(
                            Selection.getSelectionStart(buffer),
                            Selection.getSelectionEnd(buffer),
                            buffer.getSpanStart(link),
                            buffer.getSpanEnd(link))) {
                try {
                    link.onClick(widget);
                    return true;
                } catch (ActivityNotFoundException e) {
                    Log.w(TAG, "No app can open this link", e);
                }
            }
        }
        return super.onTouchEvent(widget, buffer, event);
    }

    private boolean isTap(TextView widget, MotionEvent up) {
        ViewConfiguration config = ViewConfiguration.get(widget.getContext());
        return EditorCursor.isTap(
                up.getX() - downX,
                up.getY() - downY,
                up.getEventTime() - downTime,
                config.getScaledTouchSlop(),
                ViewConfiguration.getLongPressTimeout());
    }

    /** The link under the pointer, ignoring the empty space after a line's last character. */
    private static URLSpan linkAt(TextView widget, Spannable buffer, MotionEvent event) {
        Layout layout = widget.getLayout();
        if (layout == null) return null;
        int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
        int y = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();
        int line = layout.getLineForVertical(y);
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null;
        int offset = layout.getOffsetForHorizontal(line, x);
        URLSpan[] links = buffer.getSpans(offset, offset, URLSpan.class);
        return links.length == 0 ? null : links[0];
    }
}
