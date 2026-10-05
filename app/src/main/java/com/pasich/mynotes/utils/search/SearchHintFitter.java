package com.pasich.mynotes.utils.search;

import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.material.search.SearchBar;

/**
 * Keeps the search bar hint readable. The full hint ("Search notes") is long in some languages and
 * at large font sizes it would be cut off with an ellipsis; when it does not fit the space the bar
 * gives it, the short one ("Search") is shown instead.
 */
public final class SearchHintFitter {

    private SearchHintFitter() {}

    /** Re-checks the hint whenever the bar's text area is laid out again. */
    public static void attach(
            @NonNull SearchBar searchBar, @NonNull String fullHint, @NonNull String shortHint) {
        TextView text = searchBar.getTextView();
        text.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    float available =
                            text.getWidth() - text.getPaddingLeft() - text.getPaddingRight();
                    if (available <= 0) return;
                    String hint =
                            choose(
                                    text.getPaint().measureText(fullHint),
                                    available,
                                    fullHint,
                                    shortHint);
                    CharSequence current = searchBar.getHint();
                    if (current == null || !hint.contentEquals(current)) {
                        // Changing the hint during layout would be ignored until the next pass.
                        text.post(() -> searchBar.setHint(hint));
                    }
                });
    }

    /**
     * The full hint when it fits {@code available} pixels, the short one otherwise. Half a pixel of
     * slack covers the rounding between a measured string and the width laid out for it.
     */
    @VisibleForTesting
    static String choose(float fullWidth, float available, String fullHint, String shortHint) {
        return fullWidth <= available + 0.5f ? fullHint : shortHint;
    }
}
