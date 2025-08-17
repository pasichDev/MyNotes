package com.pasich.mynotes.utils;

import android.text.Layout;
import android.text.Selection;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.MotionEvent;


/**
 * <a href="https://stackoverflow.com/questions/1697084/handle-textview-link-click-in-my-android-app/16644228#16644228">...</a>
 */
public abstract class CustomLinkMovementMethod extends LinkMovementMethod {

    public int LINK_WEB = 0;
    public int LINK_MAIL = 1;
    public int LINK_PHONE = 2;


    @Override
    public boolean onTouchEvent(android.widget.TextView widget, android.text.Spannable buffer, android.view.MotionEvent event) {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);
            if (link.length != 0) {

                if (action == MotionEvent.ACTION_UP) {
                    String url = link[0].getURL();
                    if (url.startsWith("https")) {
                        onClickLink(url, LINK_WEB);
                    } else if (url.startsWith("tel")) {

                        onClickLink(url, LINK_PHONE);
                    } else if (url.startsWith("mailto")) {

                        onClickLink(url, LINK_MAIL);
                    }
                } else {
                    Selection.setSelection(buffer, buffer.getSpanStart(link[0]), buffer.getSpanEnd(link[0]));
                }


            }

            return true;
        }

        return super.onTouchEvent(widget, buffer, event);
    }

    protected abstract void onClickLink(String link, int type);

}