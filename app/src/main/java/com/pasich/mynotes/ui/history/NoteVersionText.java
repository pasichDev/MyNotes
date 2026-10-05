package com.pasich.mynotes.ui.history;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.material.color.MaterialColors;
import com.pasich.mynotes.R;
import com.pasich.mynotes.data.history.NoteVersionReason;
import com.pasich.mynotes.ui.sync.SyncConflictPresentation;

/** Wording and formatting shared by the version list and the version preview. */
public final class NoteVersionText {

    private NoteVersionText() {}

    /** What a person reads as the note: its title, then its text. */
    @NonNull
    public static String readable(@NonNull String title, @NonNull String value) {
        String head = title.trim();
        String body = value.trim();
        if (head.isEmpty()) return body;
        if (body.isEmpty()) return head;
        return head + "\n" + body;
    }

    @StringRes
    public static int reasonLabel(@NonNull NoteVersionReason reason) {
        return switch (reason) {
            case PRE_SYNC -> R.string.version_reason_pre_sync;
            case PRE_RESTORE -> R.string.version_reason_pre_restore;
            case PRE_CONFLICT -> R.string.version_reason_pre_conflict;
            case AUTOSAVE -> R.string.version_reason_autosave;
        };
    }

    /**
     * "Just now, 14:05", "5 minutes ago, 14:05", "Yesterday, 14:05" or a date, in the user's
     * locale. Under a minute the system's wording says "0 minutes ago".
     */
    @NonNull
    public static CharSequence when(@NonNull Context context, long time) {
        if (isJustNow(System.currentTimeMillis(), time)) {
            return context.getString(
                    R.string.version_time_just_now,
                    android.text.format.DateFormat.getTimeFormat(context).format(time));
        }
        return DateUtils.getRelativeDateTimeString(
                context,
                time,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_MONTH);
    }

    /** Whether a version kept at {@code time} is less than a minute old at {@code now}. */
    @VisibleForTesting
    static boolean isJustNow(long now, long time) {
        return Math.abs(now - time) < DateUtils.MINUTE_IN_MILLIS;
    }

    /**
     * Widens a highlighted range so it never starts or ends inside a word or a number: a change
     * from "1200" to "1500" marks "1200" and "1500", not "2" and "5". An empty range inside a word
     * (letters only added on the other side) marks that word.
     *
     * @return {@code {start, end}}.
     */
    @NonNull
    @VisibleForTesting
    static int[] wholeWords(@NonNull CharSequence text, int start, int end) {
        int length = text.length();
        int from = Math.max(0, Math.min(start, length));
        int to = Math.max(from, Math.min(end, length));
        while (from > 0
                && from < length
                && isWordChar(text.charAt(from - 1))
                && isWordChar(text.charAt(from))) {
            from--;
        }
        while (to > 0
                && to < length
                && isWordChar(text.charAt(to - 1))
                && isWordChar(text.charAt(to))) {
            to++;
        }
        return new int[] {from, to};
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    /** The window's text with its differing range marked in the theme's primary colours. */
    @NonNull
    public static CharSequence highlighted(
            @NonNull Context context, @NonNull SyncConflictPresentation.Window window) {
        int[] range = wholeWords(window.text, window.start, window.end);
        if (range[1] <= range[0]) return window.text;
        SpannableString text = new SpannableString(window.text);
        int container =
                MaterialColors.getColor(
                        context, com.google.android.material.R.attr.colorPrimaryContainer, 0);
        int onContainer =
                MaterialColors.getColor(
                        context, com.google.android.material.R.attr.colorOnPrimaryContainer, 0);
        text.setSpan(
                new BackgroundColorSpan(container),
                range[0],
                range[1],
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
                new ForegroundColorSpan(onContainer),
                range[0],
                range[1],
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }
}
