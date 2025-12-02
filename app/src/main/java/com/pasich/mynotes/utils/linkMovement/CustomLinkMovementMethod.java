package com.pasich.mynotes.utils.linkMovement;

import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.widget.TextView;

public class CustomLinkMovementMethod extends LinkMovementMethod {

    private static CustomLinkMovementMethod INSTANCE;

    public static CustomLinkMovementMethod getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CustomLinkMovementMethod();
        }
        return INSTANCE;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getAction();

        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_DOWN)
            return super.onTouchEvent(widget, buffer, event);

        int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
        int y = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();

        Layout layout = widget.getLayout();
        if (layout == null) return false;

        int line = layout.getLineForVertical(y);
        int offset = layout.getOffsetForHorizontal(line, x);

        URLSpan[] links = buffer.getSpans(offset, offset, URLSpan.class);
        if (links.length == 0) {
            return false;
        }

        URLSpan link = links[0];

        if (action == MotionEvent.ACTION_DOWN) {
            Selection.setSelection(buffer, buffer.getSpanStart(link), buffer.getSpanEnd(link));
            return true;
        }

        Selection.removeSelection(buffer);

        return true;

    }

}
